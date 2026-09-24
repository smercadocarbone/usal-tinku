package com.tinku.cierre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.seguridad.model.OrigenSancion;
import com.tinku.seguridad.model.Sancion;
import com.tinku.seguridad.model.TipoSancion;
import com.tinku.seguridad.repository.SancionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-FIN-02 — rama de seguridad completa, de punta a punta y SIN mocks entre
 * módulos propios: menor real (alta por su AR) → reserva paga (M4→M3→M5) →
 * kill-switch rama MENOR (corte directo, Artículo II) → suspensión preventiva
 * del Tutor (M1/M2 flag) + Alerta en M8 → resolución del Admin de Moderación →
 * sanción propagada a M1 (estado de cuenta), M2 (matching), M4 (reservas
 * futuras canceladas) y M5 (reembolso del escrow).
 *
 * <p>Como las colas M8 y la resolución exigen la fila del Admin en
 * {@code admin.admins} (no hay API pública para darse de alta como Admin), esa
 * cuenta se siembra por repositorio — el mismo seed que usan
 * {@code DenunciasModeracionIntegracionTest} y {@code AdminPanelIntegracionTest}.
 * Los proveedores externos (OCR, almacenamiento, MercadoPago) van mockeados.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
// T-TES-10/DT7: la rama de seguridad incluye la Solicitud del menor y su
// aprobación; se corre con la flag en true para no deshabilitar esa rama del
// E2E (el corte por defecto lo cubre GateMenoresPilotoIntegracionTest).
// La flag solo se abre con T-M3-06 y T02 cerradas (AGENTS §3).
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class E2ERamaSeguridadIntegracionTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired Scheduler scheduler;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired AlertaSeguridadRepository alertaRepository;
    @Autowired SancionRepository sancionRepository;

    @Value("${tinku.mercadopago.webhook-secret}") String webhookSecret;

    @MockBean OcrService ocrService;
    @MockBean Almacenamiento almacenamiento;
    @MockBean MercadoPagoClient mercadopago;
    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;
    @MockBean AlertaSoporteProveedor alertaSoporte;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 20_700_000 + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocks() {
        when(almacenamiento.guardar(any(), any()))
                .thenReturn("https://cdn.test/" + UUID.randomUUID() + ".png");
        when(mercadopago.crearPreferencia(any()))
                .thenReturn(new PreferenciaPago("pref-mock", "https://mercadopago.com/mock", false));
    }

    // ---------------------------------------------------------------- helpers

    private MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name, MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1, 2, 3});
    }

    private String login(String dni) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    private void registrarAdulto(String dni, String nombre) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, "Lopez", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, "Lopez", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, true, true)))
                        .file(foto()))
                .andExpect(status().isCreated());
    }

    private void registrarTutor(String dni, String nombre) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, "Garcia", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(
                                dni, nombre, "Garcia", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());
    }

    private void registrarMenor(String dni, String tokenAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, "Sofia", "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(
                                dni, "Sofia", "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());
    }

    private void autorizar(UUID tutorId, UUID menorId, String tokenAr) throws Exception {
        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    private void publicarFranja(String tokenTutor, LocalDate fecha) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(),
                                "horaInicio", "15:00",
                                "horaFin", "16:00"))))
                .andExpect(status().isCreated());
    }

    private Instant dentroDeFranja(LocalDate fecha) {
        // D6: 15:00 por 60 min ocupa la franja 15-16 entera → precio = tarifa por hora.
        return ZonedDateTime.of(fecha, LocalTime.of(15, 0), ReservasZonaHoraria.ZONA).toInstant();
    }

    private UUID reservaDeMenor(String tokenMenor, String tokenAr, UUID tutorId,
                                Instant horario) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + tokenMenor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horarioPropuesto", horario.toString(),
                                "duracionMinutos", 60))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID solicitudId = UUID.fromString(objectMapper.readTree(
                res.getResponse().getContentAsString()).get("id").asText());
        res = mockMvc.perform(post("/api/solicitudes/" + solicitudId + "/aprobar")
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                res.getResponse().getContentAsString()).get("id").asText());
    }

    private void pagarReserva(String tokenAr, UUID reservaId, String mpPaymentId)
            throws Exception {
        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isOk());
        when(mercadopago.getPago(mpPaymentId)).thenReturn(
                new PagoMercadoPago(mpPaymentId, "approved", reservaId.toString(),
                        new BigDecimal("15000")));
        var payload = objectMapper.createObjectNode();
        payload.put("action", "payment.updated");
        payload.put("type", "payment");
        payload.putObject("data").put("id", mpPaymentId);
        long ts = Instant.now().toEpochMilli();
        String manifest = "id:" + mpPaymentId.toLowerCase() + ";request-id:req-fin;ts:" + ts + ";";
        String firma = "ts=" + ts + ",v1=" + hmacHex(manifest);
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma)
                        .header("x-request-id", "req-fin")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    private String hmacHex(String texto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"));
            StringBuilder hex = new StringBuilder();
            for (byte b : mac.doFinal(texto.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Admin de Moderación: Usuario + fila en {@code admin.admins} (M8, V16). */
    private Usuario adminModeracion() {
        Usuario admin = new Usuario();
        admin.setDni(dniUnico());
        admin.setNombre("Admin");
        admin.setApellido("Seguridad");
        admin.setFechaNacimiento(LocalDate.of(1985, 1, 2));
        admin.setTipo(TipoUsuario.ADULTO);
        admin.setPasswordHash("hash");
        admin.setCapacidadEstudiante(true);
        admin.setCapacidadAdultoResponsable(true);
        usuarioRepository.save(admin);
        Admin fila = new Admin();
        fila.setUsuario(admin);
        fila.setRol(RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(fila);
        return admin;
    }

    // ---------------------------------------------------------------- T-FIN-02

    @Test
    void killswitchMenor_resolucionAdmin_sancionaYPropagaAM1M2M4M5() throws Exception {
        // Asegurado por HTTP: AR, menor, Tutor, autorización, franjas y dos
        // reservas pagas (la "sesión con menor" y una futura del mismo Tutor).
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        registrarAdulto(dniAr, "Ana");
        String tokenAr = login(dniAr);
        registrarTutor(dniTutor, "Pablo");
        String tokenTutor = login(dniTutor);
        registrarMenor(dniMenor, tokenAr);
        String tokenMenor = login(dniMenor);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID menorId = usuarioPorDni(dniMenor).getId();
        autorizar(tutorId, menorId, tokenAr);

        LocalDate fechaActual = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(3);
        LocalDate fechaFutura = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(5);
        publicarFranja(tokenTutor, fechaActual);
        publicarFranja(tokenTutor, fechaFutura);

        UUID reservaActual = reservaDeMenor(tokenMenor, tokenAr, tutorId,
                dentroDeFranja(fechaActual));
        UUID reservaFutura = reservaDeMenor(tokenMenor, tokenAr, tutorId,
                dentroDeFranja(fechaFutura));
        pagarReserva(tokenAr, reservaActual, "mp-switch-" + CONTADOR_DNIS.get());
        pagarReserva(tokenAr, reservaFutura, "mp-futura-" + CONTADOR_DNIS.get());
        UUID sesionActual = sesionRepository.findByReservaId(reservaActual).orElseThrow().getId();
        assertThat(transaccionRepository.findByReservaId(reservaActual).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);

        // M3 — kill-switch de la sesión con menor, disparado por el AR. La rama
        // la decide el backend (menor → corte directo, sin preguntarle nada).
        mockMvc.perform(post("/api/sesiones/" + sesionActual + "/killswitch")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("detectadoId", tutorId.toString()))))
                .andExpect(status().isOk());

        // Corte directo + Alerta rama menor + suspensión preventiva + escrow en pausa
        // por Alerta (ADR-M3-02 + FASE2-10: el reembolso espera la resolución de la Alerta).
        SesionAprendizaje cortada = sesionRepository.findById(sesionActual).orElseThrow();
        assertThat(cortada.getEstado()).isEqualTo(SesionAprendizaje.ESTADO_FINALIZADA);
        assertThat(reservaRepository.findById(reservaActual).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);
        AlertaSeguridad alerta = alertaRepository.findBySesionId(sesionActual).orElseThrow();
        assertThat(alerta.getRama()).isEqualTo("menor");
        assertThat(alerta.getDetectadoId()).isEqualTo(tutorId);
        assertThat(alerta.getEstado()).isEqualTo(AlertaSeguridad.ESTADO_PENDIENTE_REVISION);
        assertThat(usuarioPorDni(dniTutor).isActivoParaMatching()).isFalse();
        assertThat(transaccionRepository.findByReservaId(reservaActual).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.PAUSADO_ALERTA);

        // M8 — la Alerta entra a la cola de moderación (admin de moderación real).
        Usuario admin = adminModeracion();
        String tokenAdmin = jwtUtil.generateToken(admin.getDni(), "ADULTO", true, true);
        String cola = mockMvc.perform(get("/api/admin/moderacion/alertas")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(idEn(cola, alerta.getId())).isTrue();

        // M9 — el Admin sanciona al Tutor (suspensión temporal, 15 días).
        mockMvc.perform(post("/api/admin/moderacion/alertas-seguridad/"
                        + alerta.getId() + "/resolver")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "decision", "sancionar",
                                "tipoSancion", "suspension_temporal",
                                "diasSuspension", 15))))
                .andExpect(status().isOk());

        AlertaSeguridad resuelta = alertaRepository.findById(alerta.getId()).orElseThrow();
        assertThat(resuelta.getEstado()).isEqualTo(AlertaSeguridad.ESTADO_RESUELTA_BAJA);
        Sancion sancion = sancionRepository.findByAlertaId(alerta.getId()).orElseThrow();
        assertThat(sancion.getOrigen()).isEqualTo(OrigenSancion.ALERTA_SEGURIDAD);
        assertThat(sancion.getTipo()).isEqualTo(TipoSancion.SUSPENSION_TEMPORAL);
        assertThat(sancion.getDiasSuspension()).isEqualTo(15);
        // M5 — resuelta la Alerta, recién ahora se reembolsa al Estudiante.
        assertThat(transaccionRepository.findByReservaId(reservaActual).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);

        // M1 — cuenta suspendida; M2 — fuera del matching (mismo flag).
        Usuario tutorSancionado = usuarioPorDni(dniTutor);
        assertThat(tutorSancionado.getEstadoCuenta()).isEqualTo(EstadoCuenta.SUSPENDIDA);
        assertThat(tutorSancionado.isActivoParaMatching()).isFalse();
        // US-6 — reactivación automática agendada (Quartz persistido, M9).
        assertThat(scheduler.checkExists(new TriggerKey(
                "reactivacion-trigger-" + tutorId, "m9-seguridad"))).isTrue();

        // M4 — la reserva futura del sancionado se cancela (la del switch ya
        // estaba finalizada y no se toca de nuevo).
        assertThat(reservaRepository.findById(reservaFutura).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
        assertThat(reservaRepository.findById(reservaFutura).orElseThrow().getMotivoCancelacion())
                .isEqualTo(MotivoCancelacion.SANCION);
        assertThat(reservaRepository.findById(reservaActual).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);
        // M5 — el escrow de la reserva futura se reembolsa vía la cancelación.
        assertThat(transaccionRepository.findByReservaId(reservaFutura).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);

        // Artículo II: la suspensión no permite que el AR re-agende al tutor.
        assertThat(usuarioRepository.findById(tutorId).orElseThrow()
                .isActivoParaMatching()).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    private boolean idEn(String json, UUID id) throws Exception {
        return java.util.stream.StreamSupport.stream(
                        objectMapper.readTree(json).spliterator(), false)
                .anyMatch(n -> id.toString().equals(n.get("id").asText()));
    }
}