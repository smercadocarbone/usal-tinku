package com.tinku.identidad;

import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.port.NotificadorResetPassword;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.SolicitudNoPendienteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración (Testcontainers) de la baja de menor por anonimización,
 * FASE2-06 / AUD-017 (ADR-M1-05). El endpoint real del Adulto Responsable
 * (DELETE /api/usuarios/menores/{id}) ya no borra la fila: la anonimiza. Antes
 * del fix, el DELETE contra un menor con actividad real fallaba con 500 por las
 * FKs (reservas/transacciones) — ese 500 es el RED que este archivo dejó de ver.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class BajaMenorAnonimizacionIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(
                    DockerImageName.parse("pgvector/pgvector:pg16"))
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
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired SolicitudSesionRepository solicitudSesionRepository;
    @Autowired ReservaService reservaService;

    @MockitoBean OcrService ocrService;
    @MockitoBean NotificadorResetPassword notificadorResetPassword;

    private static final String PASSWORD = "password123";

    @BeforeEach
    void programarOcr() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
    }

    private com.tinku.identidad.ocr.ResultadoOcr resultado(String dni, String nombre,
                                                           String apellido, LocalDate nac) {
        return new com.tinku.identidad.ocr.ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    // ------------------------------------------------ helpers

    private MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name, MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1, 2, 3});
    }

    private String registrarAdultoYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, true, true)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private UUID registrarMenor(String token, String dni, String nombre) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(
                                dni, nombre, "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
        return usuarioRepository.findByDni(dni).orElseThrow().getId();
    }

    private String login(String dni) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    // ------------------------------------------------ tests

    @Test
    void bajaDeMenorConReservaFinalizada_noFallaYAnonimiza() throws Exception {
        String token = registrarAdultoYToken("41111001", "Maria", "Perez");
        UUID menorId = registrarMenor(token, "41111002", "Sofia");

        Usuario ar = usuarioRepository.findByDni("41111001").orElseThrow();
        Usuario menor = usuarioRepository.findById(menorId).orElseThrow();

        Usuario tutor = new Usuario();
        tutor.setDni("41111003");
        tutor.setNombre("Pablo");
        tutor.setApellido("Sosa");
        tutor.setFechaNacimiento(LocalDate.of(1988, 9, 9));
        tutor.setTipo(TipoUsuario.TUTOR);
        tutor.setEmail("41111003@tinku.test");
        tutor.setPasswordHash("x");
        tutor = usuarioRepository.save(tutor);

        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(menor);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minus(1, ChronoUnit.DAYS));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.FINALIZADA);
        reserva = reservaRepository.save(reserva);

        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId("mp-" + UUID.randomUUID());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(BigDecimal.valueOf(4050));
        transaccion.setEstado(EstadoTransaccion.LIBERADO);
        transaccion.setEnBypass(true);
        transaccionRepository.save(transaccion);

        mockMvc.perform(delete("/api/usuarios/menores/{id}", menorId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // La reserva sigue viva apuntando al MISMO beneficiario (registro contable).
        Reserva persistida = reservaRepository.findById(reserva.getId()).orElseThrow();
        assertThat(persistida.getBeneficiario().getId()).isEqualTo(menorId);
        assertThat(persistida.getEstado()).isEqualTo(EstadoReserva.FINALIZADA);
        assertThat(transaccionRepository.findById(transaccion.getId())).isPresent();

        // El usuario sigue existiendo (no se borró) pero quedó anonimizado.
        Usuario anonimizado = usuarioRepository.findById(menorId).orElseThrow();
        assertThat(anonimizado.getDni()).startsWith("BAJA-");
        assertThat(anonimizado.getDni()).isNotEqualTo("41111002");
        assertThat(anonimizado.getNombre()).isEqualTo("Perfil");
        assertThat(anonimizado.getApellido()).isEqualTo("dado de baja");
        assertThat(anonimizado.getEmail()).isNull();
        assertThat(anonimizado.getFechaNacimiento()).isEqualTo(LocalDate.of(1900, 1, 1));
        assertThat(anonimizado.getEstadoCuenta()).isEqualTo(EstadoCuenta.BAJA);
        assertThat(anonimizado.isActivoParaMatching()).isFalse();
    }

    @Test
    void menorDadoDeBaja_noPuedeLoguear() throws Exception {
        String token = registrarAdultoYToken("41112001", "Maria", "Perez");
        registrarMenor(token, "41112002", "Sofia");

        mockMvc.perform(delete("/api/usuarios/menores/{id}",
                        usuarioRepository.findByDni("41112002").orElseThrow().getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("41112002", PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void menorDadoDeBaja_noApareceEnListarMenores() throws Exception {
        String token = registrarAdultoYToken("41113001", "Maria", "Perez");
        registrarMenor(token, "41113002", "Sofia");

        mockMvc.perform(delete("/api/usuarios/menores/{id}",
                        usuarioRepository.findByDni("41113002").orElseThrow().getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/usuarios/menores")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void bajaDeMenor_dniAnonimoEsUnico() throws Exception {
        String token = registrarAdultoYToken("41114001", "Maria", "Perez");
        UUID m1 = registrarMenor(token, "41114002", "Sofia");
        UUID m2 = registrarMenor(token, "41114003", "Tomas");

        mockMvc.perform(delete("/api/usuarios/menores/{id}", m1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/usuarios/menores/{id}", m2)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Dos dnis anonimizados distintos (determinísticos por UUID) y dentro
        // del VARCHAR(20): la segunda baja no viola el UNIQUE de V2.
        String dni1 = usuarioRepository.findById(m1).orElseThrow().getDni();
        String dni2 = usuarioRepository.findById(m2).orElseThrow().getDni();
        assertThat(dni1).startsWith("BAJA-").hasSizeLessThanOrEqualTo(20);
        assertThat(dni2).startsWith("BAJA-").hasSizeLessThanOrEqualTo(20);
        assertThat(dni1).isNotEqualTo(dni2);
    }

    // ------------------------------------------------ reservas futuras (decisión del usuario, 2026-09-24)
    //
    // La baja confirmada cancela las reservas futuras del menor por la misma vía
    // que cualquier cancelación: reserva.cancelada con canceladaPor = el Adulto
    // Responsable (pagador), y M5 aplica FR-RES-008 sin reglas nuevas. Sin esto,
    // el Tutor iba a una clase con un "Perfil dado de baja" y el escrow quedaba
    // retenido para siempre.

    private record Futura(String token, UUID menorId, UUID reservaId, UUID transaccionId) {}

    private Futura menorConReservaFutura(String dniBase, Instant horario, EstadoReserva estado)
            throws Exception {
        String token = registrarAdultoYToken(dniBase + "1", "Maria", "Perez");
        UUID menorId = registrarMenor(token, dniBase + "2", "Sofia");
        Usuario ar = usuarioRepository.findByDni(dniBase + "1").orElseThrow();

        Usuario tutor = new Usuario();
        tutor.setDni(dniBase + "3");
        tutor.setNombre("Pablo");
        tutor.setApellido("Sosa");
        tutor.setFechaNacimiento(LocalDate.of(1988, 9, 9));
        tutor.setTipo(TipoUsuario.TUTOR);
        tutor.setEmail(dniBase + "3@tinku.test");
        tutor.setPasswordHash("x");
        tutor = usuarioRepository.save(tutor);

        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(usuarioRepository.findById(menorId).orElseThrow());
        reserva.setTutor(tutor);
        reserva.setHorario(horario);
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(estado);
        reserva = reservaRepository.save(reserva);

        UUID transaccionId = null;
        if (estado == EstadoReserva.CONFIRMADA) {
            Transaccion t = new Transaccion();
            t.setReservaId(reserva.getId());
            t.setMpPaymentId("mp-" + UUID.randomUUID());
            t.setMontoBruto(BigDecimal.valueOf(15000));
            t.setComisionPlataforma(BigDecimal.valueOf(4050));
            t.setEstado(EstadoTransaccion.RETENIDO_ESCROW);
            t.setEnBypass(true);
            transaccionId = transaccionRepository.save(t).getId();
        }
        return new Futura(token, menorId, reserva.getId(), transaccionId);
    }

    private void darDeBajaConfirmando(Futura f) throws Exception {
        mockMvc.perform(delete("/api/usuarios/menores/{id}", f.menorId())
                        .param("confirmar", "true")
                        .header("Authorization", "Bearer " + f.token()))
                .andExpect(status().isNoContent());
    }

    @Test
    void bajaConfirmada_reservaFuturaConMargen_seCancelaYSeReembolsa() throws Exception {
        Futura f = menorConReservaFutura("4211500",
                Instant.now().plus(3, ChronoUnit.DAYS), EstadoReserva.CONFIRMADA);

        darDeBajaConfirmando(f);

        Reserva cancelada = reservaRepository.findById(f.reservaId()).orElseThrow();
        assertThat(cancelada.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(cancelada.getMotivoCancelacion())
                .isEqualTo(com.tinku.reservas.model.MotivoCancelacion.VOLUNTARIA);
        assertThat(transaccionRepository.findById(f.transaccionId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);
    }

    /** FR-RES-008: la baja es un acto del pagador. Con <24hs aplica la misma
     *  asimetría que su cancelación tardía — si no, borrar al menor sería la vía
     *  para esquivar la penalidad. */
    @Test
    void bajaConfirmada_reservaFuturaSinMargen_elTutorCobra() throws Exception {
        Futura f = menorConReservaFutura("4211600",
                Instant.now().plus(2, ChronoUnit.HOURS), EstadoReserva.CONFIRMADA);

        darDeBajaConfirmando(f);

        assertThat(reservaRepository.findById(f.reservaId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
        assertThat(transaccionRepository.findById(f.transaccionId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.LIBERADO);
    }

    @Test
    void bajaConfirmada_reservaPendienteDePago_seCancelaSinTransaccion() throws Exception {
        Futura f = menorConReservaFutura("4211700",
                Instant.now().plus(3, ChronoUnit.DAYS), EstadoReserva.PENDIENTE_PAGO);

        darDeBajaConfirmando(f);

        assertThat(reservaRepository.findById(f.reservaId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
        assertThat(transaccionRepository.existsByReservaId(f.reservaId())).isFalse();
    }

    /** Una Solicitud pendiente del menor no sobrevive a la baja: si quedara
     *  pendiente, el Adulto Responsable podría aprobarla después (aprobarSolicitud
     *  no re-chequea al menor) y crear una Reserva paga para un perfil dado de baja.
     *  La baja es un acto del AR, así que la Solicitud queda rechazada (US-3). */
    @Test
    void bajaConfirmada_solicitudPendiente_quedaRechazadaYNoSePuedeAprobar() throws Exception {
        String token = registrarAdultoYToken("42118001", "Maria", "Perez");
        UUID menorId = registrarMenor(token, "42118002", "Sofia");

        Usuario tutor = new Usuario();
        tutor.setDni("42118003");
        tutor.setNombre("Pablo");
        tutor.setApellido("Sosa");
        tutor.setFechaNacimiento(LocalDate.of(1988, 9, 9));
        tutor.setTipo(TipoUsuario.TUTOR);
        tutor.setEmail("42118003@tinku.test");
        tutor.setPasswordHash("x");
        tutor = usuarioRepository.save(tutor);

        SolicitudSesion solicitud = new SolicitudSesion();
        solicitud.setMenor(usuarioRepository.findById(menorId).orElseThrow());
        solicitud.setTutor(tutor);
        solicitud.setHorarioPropuesto(Instant.now().plus(3, ChronoUnit.DAYS));
        solicitud.setExpiraAt(Instant.now().plus(48, ChronoUnit.HOURS));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        UUID solicitudId = solicitudSesionRepository.save(solicitud).getId();

        mockMvc.perform(delete("/api/usuarios/menores/{id}", menorId)
                        .param("confirmar", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(solicitudSesionRepository.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.RECHAZADA);
        Usuario ar = usuarioRepository.findByDni("42118001").orElseThrow();
        assertThatThrownBy(() -> reservaService.aprobarSolicitud(ar, solicitudId))
                .isInstanceOf(SolicitudNoPendienteException.class);
    }
}
