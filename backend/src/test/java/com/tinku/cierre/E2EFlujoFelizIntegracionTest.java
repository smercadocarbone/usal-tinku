package com.tinku.cierre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.port.Almacenamiento;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.MercadoPagoClient;
import com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago;
import com.tinku.pagos.port.MercadoPagoClient.PreferenciaPago;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.pagos.service.LiberacionEscrowService;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.resumen.model.ResumenSesion;
import com.tinku.resumen.port.ResumenProveedor;
import com.tinku.resumen.port.TranscriptSesionProveedor;
import com.tinku.resumen.repository.ResumenSesionRepository;
import com.tinku.resumen.service.ResumenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-FIN-01 — flujo feliz completo, de punta a punta y SIN mocks entre módulos
 * propios. El recorrido de datos es real entre M1→M4→M3→M5→M6→M7:
 *
 * <p>Registros por HTTP con OCR mockeado (proveedor externo) → autorización del
 * Tutor → Solicitud del menor → aprobación del AR → preferencia + webhook de
 * MercadoPago (client mockeado) → Reserva CONFIRMADA que agenda la Sesión por
 * evento (M4→M3) → `sesion.finalizada` (US-8) → escrow retenido con liberación a
 * +24hs (M5) → resumen LLM (M6) → calificación pública + perfil (M7).
 *
 * <p>Únicas excepciones al "todas las APIs": la simulación del JOIN de LiveKit
 * (proveedor externo) se siembra por repositorio igual que
 * {@code KillswitchIntegracionTest}, y el avance de las 24hs del escrow se hace
 * corriendo el disparo al pasado como en {@code LiberacionEscrowIntegracionTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
// T-TES-10/DT7: el E2E del flujo feliz incluye US-2/US-3 (Solicitud y aprobación
// por el AR de una clase con menor). La spec T10 manda este test CON la flag en
// true para que siga ejerciendo el flujo completo; el corte por defecto (flag
// false) lo cubre GateMenoresPilotoIntegracionTest. La flag solo se abre con
// T-M3-06 y T02 cerradas (AGENTS §3).
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class E2EFlujoFelizIntegracionTest {

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
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired ResumenSesionRepository resumenRepository;
    @Autowired ResumenService resumenService;
    @Autowired LiberacionEscrowService liberacionEscrow;

    @Value("${tinku.mercadopago.webhook-secret}") String webhookSecret;

    @MockBean OcrService ocrService;
    @MockBean Almacenamiento almacenamiento;
    @MockBean MercadoPagoClient mercadopago;
    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;
    @MockBean AlertaSoporteProveedor alertaSoporte;
    @MockBean TranscriptSesionProveedor transcript;
    @MockBean ResumenProveedor resumenProveedor;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private static final String TRANSCRIPT_CON_DATOS =
            "Hola Pablo, soy la profe María. Mi celular es 011 15 5555-1234 y mi email es "
                    + "pablo.perez@gmail.com. Para la próxima clase me pasás el pago por alias "
                    + "pablo.estudiante.mp.";

    private String dniUnico() {
        return String.format("%08d", 20_500_000 + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocks() {
        when(almacenamiento.guardar(any(), any()))
                .thenReturn("https://cdn.test/" + UUID.randomUUID() + ".png");
        when(mercadopago.crearPreferencia(any()))
                .thenReturn(new PreferenciaPago("pref-mock", "https://mercadopago.com/mock", false));
        when(transcript.transcript(any(UUID.class))).thenReturn(TRANSCRIPT_CON_DATOS);
        when(resumenProveedor.generarResumen(any()))
                .thenReturn(new ResumenProveedor.ResumenResultado("Resumen de la clase."));
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

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
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
        return ZonedDateTime.of(fecha, LocalTime.of(15, 30), ReservasZonaHoraria.ZONA).toInstant();
    }

    private UUID solicitar(String tokenMenor, UUID tutorId, Instant horario) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + tokenMenor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horarioPropuesto", horario.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                res.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID aprobar(String tokenAr, UUID solicitudId) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/solicitudes/" + solicitudId + "/aprobar")
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                res.getResponse().getContentAsString()).get("id").asText());
    }

    private void preferencia(String tokenAr, UUID reservaId) throws Exception {
        mockMvc.perform(post("/api/pagos/preferencia")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("reservaId", reservaId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferenciaId").value("pref-mock"));
    }

    private String cuerpoNotificacion(String mpPaymentId) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("action", "payment.updated");
        payload.put("api_version", "v1");
        payload.put("type", "payment");
        payload.putObject("data").put("id", mpPaymentId);
        return objectMapper.writeValueAsString(payload);
    }

    private String firma(String mpPaymentId, String xRequestId) {
        long ts = Instant.now().toEpochMilli();
        StringBuilder manifest = new StringBuilder();
        manifest.append("id:").append(mpPaymentId.toLowerCase()).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifest.append("request-id:").append(xRequestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return "ts=" + ts + ",v1=" + hmacHex(manifest.toString());
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

    private void pagoAprobado(String mpPaymentId, UUID reservaId, String monto) throws Exception {
        when(mercadopago.getPago(mpPaymentId)).thenReturn(
                new PagoMercadoPago(mpPaymentId, "approved", reservaId.toString(),
                        new BigDecimal(monto)));
        mockMvc.perform(post("/api/webhooks/mercadopago")
                        .contentType(MediaType.APPLICATION_JSON)
                        .queryParam("data.id", mpPaymentId)
                        .header("x-signature", firma(mpPaymentId, "req-fin"))
                        .header("x-request-id", "req-fin")
                        .content(cuerpoNotificacion(mpPaymentId)))
                .andExpect(status().isOk());
    }

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    // ---------------------------------------------------------------- T-FIN-01

    @Test
    void flujoFelizCompleto_compraPagaDictaResumeYCalifica() throws Exception {
        // M1 — alta de AR, menor (por el AR, Artículo II) y Tutor.
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        registrarAdulto(dniAr, "Ana");
        String tokenAr = login(dniAr);
        registrarTutor(dniTutor, "Pablo");
        registrarMenor(dniMenor, tokenAr);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID menorId = usuarioPorDni(dniMenor).getId();
        String tokenMenor = login(dniMenor);

        // M1 — el AR autoriza al Tutor para el menor; el Tutor publica su franja.
        autorizar(tutorId, menorId, tokenAr);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        String tokenTutor = login(dniTutor);
        publicarFranja(tokenTutor, fecha);
        Instant horario = dentroDeFranja(fecha);

        // M4 — el menor solicita (US-2) y su AR aprueba (US-3): nace la Reserva en pago.
        UUID solicitudId = solicitar(tokenMenor, tutorId, horario);
        UUID reservaId = aprobar(tokenAr, solicitudId);
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE_PAGO);

        // M5 — preferencia + webhook firmado: escrow retenido y Reserva confirmada.
        // (M4→M3) el evento ReservaConfirmada agenda la Sesión.
        preferencia(tokenAr, reservaId);
        String mpPaymentId = "mp-flujo-feliz-" + CONTADOR_DNIS.get();
        pagoAprobado(mpPaymentId, reservaId, "15000");
        Transaccion transaccion = transaccionRepository.findByReservaId(reservaId).orElseThrow();
        assertThat(transaccion.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(transaccion.getMontoBruto()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(transaccion.getComisionPlataforma()).isEqualByComparingTo(new BigDecimal("4050.00"));
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);

        SesionAprendizaje sesion = sesionRepository.findByReservaId(reservaId).orElseThrow();
        assertThat(sesion.getEstado()).isEqualTo(SesionAprendizaje.ESTADO_NO_INICIADA);
        assertThat(sesion.getDuracionAgendadaSegundos()).isEqualTo(3600);

        // M3 — la clase arranca (JOIN de LiveKit, proveedor externo: se siembra
        // por repositorio y se corre el inicio 11min atrás para pasar el umbral
        // de duración mínima de M6).
        sesion.setEstado(SesionAprendizaje.ESTADO_EN_CURSO);
        sesion.setInicioReal(Instant.now().minusSeconds(11 * 60));
        sesionRepository.save(sesion);

        // M3 — US-8 «Finalizar»: emite `sesion.finalizada`.
        mockMvc.perform(post("/api/sesiones/" + sesion.getId() + "/finalizar")
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isOk());
        SesionAprendizaje finalizada = sesionRepository.findById(sesion.getId()).orElseThrow();
        assertThat(finalizada.getEstado()).isEqualTo(SesionAprendizaje.ESTADO_FINALIZADA);
        assertThat(finalizada.getDuracionEfectivaSegundos()).isGreaterThanOrEqualTo(600);
        assertThat(reservaRepository.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);

        // M5 — la finalización deja el escrow retenido con liberación a fin+24hs.
        Transaccion retenida = transaccionRepository.findByReservaId(reservaId).orElseThrow();
        assertThat(retenida.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(retenida.getLiberarAt())
                .isBetween(Instant.now().plus(Duration.ofHours(23)),
                        Instant.now().plus(Duration.ofHours(25)));

        // M6 — el resumen se genera con el transcript YA anonimizado (T-M6-08).
        resumenService.ejecutarGenerar(sesion.getId());
        ResumenSesion fila = resumenRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(fila.getEstado()).isEqualTo(ResumenSesion.ESTADO_GENERADO);
        assertThat(fila.getResumenFinal()).isEqualTo("Resumen de la clase.");
        ArgumentCaptor<ResumenProveedor.ResumenRequest> captor =
                ArgumentCaptor.forClass(ResumenProveedor.ResumenRequest.class);
        verify(resumenProveedor).generarResumen(captor.capture());
        assertThat(captor.getValue().transcriptAnonimizado())
                .doesNotContain("Pablo", "María", "gmail", "5555")
                .contains("[nombre]", "[telefono]", "[email]", "[pago]");
        assertThat(fila.getTranscriptAnonimizado()).isEqualTo(captor.getValue().transcriptAnonimizado());

        // M5 — las 24hs del escrow se liberan automáticamente (disparo vencido).
        retenida.setLiberarAt(Instant.now().minusSeconds(60));
        transaccionRepository.save(retenida);
        liberacionEscrow.ejecutarLiberacion(retenida.getId());
        Transaccion liberada = transaccionRepository.findById(retenida.getId()).orElseThrow();
        assertThat(liberada.getEstado()).isEqualTo(EstadoTransaccion.LIBERADO);
        verify(liberacion).liberarAlTutor(any(Transaccion.class));

        // M7 — el AR califica la sesión finalizada (dirección derivada por rol, pública).
        mockMvc.perform(post("/api/sesiones/" + sesion.getId() + "/calificacion")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("estrellas", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direccion").value("estudiante_a_tutor"));

        // M7/FR-REP-007 — el perfil público cuenta la calificación (promedio se
        // oculta hasta las 5 públicas).
        mockMvc.perform(get("/api/tutores/" + tutorId)
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cantidadCalificaciones").value(1))
                .andExpect(jsonPath("$.calificacionPromedio").isEmpty());
    }
}