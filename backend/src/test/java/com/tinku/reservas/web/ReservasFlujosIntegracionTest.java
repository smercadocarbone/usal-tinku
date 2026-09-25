package com.tinku.reservas.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.MatchingServiceClient;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.aula.SesionService;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.seguridad.evento.DenunciaResueltaEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.reservas.service.SolicitudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del Chunk M4-B (T-M4-02, T-M4-03, T-M4-04): las Historias
 * de Usuario del Spec M4 ejercitadas de punta a punta (HTTP + Spring Security +
 * JPA + Flyway + PostgreSQL via Testcontainers), con el mismo patrón de
 * MatchingFlujosIntegracionTest: OCR mockeado, resto real.
 *
 * La tarifa se resuelve por la implementación real (M5-H,
 * {@code TarifaProveedorTutor}) leyendo pagos.tarifas_tutor; sin fila, el
 * fallback de dev aplica tinku.reservas.tarifa-stub=15000 (application-test.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
// T-TES-10/DT7: ejercita el flujo existente de sesiones con menores (US-2/US-3,
// FR-RES-003), que el gate del piloto bloquea salvo que la flag esté en true.
// La flag solo se abre con T-M3-06 y T02 cerradas (AGENTS §3) — ver el test
// crearReserva_beneficiarioMenor_conFlagTrue_ok y GateMenoresPilotoIntegracionTest.
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class ReservasFlujosIntegracionTest {

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
    @Autowired SolicitudSesionRepository solicitudRepo;
    @Autowired ReservaRepository reservaRepo;
    @Autowired SolicitudService solicitudService;
    @Autowired ReservaService reservaService;
    @Autowired Scheduler scheduler;
    @Autowired SesionAprendizajeRepository sesionRepo;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired ApplicationEventPublisher eventos;

    @MockBean OcrService ocrService;
    @MockBean MatchingServiceClient matchingClient;
    @MockBean ReputacionSignalProvider reputacion;
    @MockBean ReputacionBloqueoProveedor reputacionBloqueo;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    /** DNI numérico único por invocación: evita colisiones de existsByDni en la BD
     *  Testcontainers compartida entre los tests de esta clase. */
    private String dniUnico() {
        return String.format("%08d", 30_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocks() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        // FR-REP-006: por defecto ningún Tutor bloqueado (M7 no existe; el test
        // puntual T-M4-10 stubbee el bloqueo del Tutor afectado).
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of());
    }

    // ------------------------------------------------ helpers

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    private String registrarAdultoYToken(String dni, String nombre, String apellido,
                                         boolean capEst, boolean capAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, capEst, capAr)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private String registrarTutorYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name, MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png", MediaType.APPLICATION_OCTET_STREAM_VALUE,
                new byte[]{1, 2, 3});
    }

    private String login(String dni) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.tinku.identidad.dto.LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    private UUID registrarMenor(String dniMenor, String tokenAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(
                                dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());
        return usuarioPorDni(dniMenor).getId();
    }

    private void autorizar(UUID tutorId, UUID menorId, String tokenAr) throws Exception {
        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    /** Franja PUNTUAL: {fecha} 15:00-16:00. */
    private void publicarFranjaPuntual(String tokenTutor, LocalDate fecha) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(),
                                "horaInicio", "15:00",
                                "horaFin", "16:00"))))
                .andExpect(status().isCreated());
    }

    /** Franja SEMANAL: {diaSemana 0-6} 15:00-16:00. */
    private void publicarFranjaSemanal(String tokenTutor, short diaSemana) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "diaSemana", diaSemana,
                                "horaInicio", "15:00",
                                "horaFin", "16:00"))))
                .andExpect(status().isCreated());
    }

    private Instant dentroDeFranja(LocalDate fecha) {
        return ZonedDateTime.of(fecha, LocalTime.of(15, 30), ReservasZonaHoraria.ZONA).toInstant();
    }

    private UUID crearSolicitud(String tokenMenor, UUID tutorId, Instant horario) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + tokenMenor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horarioPropuesto", horario.toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    /** Escenario base reutilizado: tutor con franja puntual 15:00-16:00 en `fecha`. */
    private record Escenario(String dniAr, String tokenAr, String tokenMenor, String tokenTutor, UUID menorId,
                             UUID tutorId, LocalDate fecha, Instant horario) {
    }

    private Escenario escenarioBase() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID menorId = registrarMenor(dniMenor, tokenAr);
        // Franja para pasado mañana (futuro no ambiguo; 15:30 en AR no choca con DST).
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        autorizar(tutorId, menorId, tokenAr);
        String tokenMenor = login(dniMenor);
        return new Escenario(dniAr, tokenAr, tokenMenor, tokenTutor, menorId, tutorId, fecha, dentroDeFranja(fecha));
    }

    // ------------------------------------------------ tests

    @Test
    void us2_menorGeneraSolicitud_quedaPendienteSinTocarReservas() throws Exception {
        Escenario e = escenarioBase();

        UUID solicitudId = crearSolicitud(e.tokenMenor(), e.tutorId(), e.horario());

        assertThat(solicitudRepo.findById(solicitudId)).isPresent();
        SolicitudSesion s = solicitudRepo.findById(solicitudId).orElseThrow();
        assertThat(s.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(s.getExpiraAt()).isAfter(s.getCreatedAt().plusSeconds(40 * 3600));
        assertThat(reservaRepo.findAll()).isEmpty();

        // El AR las ve en /pendientes (US-3).
        mockMvc.perform(get("/api/solicitudes/pendientes")
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(solicitudId.toString()));
    }

    @Test
    void frRes021_menorSolicitaTutorNoAutorizado_quedaProhibido() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        registrarMenor(dniMenor, tokenAr);
        publicarFranjaPuntual(tokenTutor, LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2));
        String tokenMenor = login(dniMenor);

        mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + tokenMenor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horarioPropuesto", dentroDeFranja(
                                        LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2)).toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void frRes012_solicitudFueraDeFranja_quedaRechazada() throws Exception {
        Escenario e = escenarioBase();

        // Horario del día siguiente: la franja puntual no lo cubre.
        Instant fuera = dentroDeFranja(e.fecha().plusDays(1));
        mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + e.tokenMenor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horarioPropuesto", fuera.toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void us3_arAprueba_creaReservaPendientePagoConPrecioCongelado() throws Exception {
        Escenario e = escenarioBase();

        UUID solicitudId = crearSolicitud(e.tokenMenor(), e.tutorId(), e.horario());

        MvcResult res = mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente_pago"))
                .andExpect(jsonPath("$.beneficiarioId").value(e.menorId().toString()))
                // D6: tarifa-stub 15000 es POR HORA → 30 min = 7500.
                .andExpect(jsonPath("$.precio").value(7500.00))
                .andReturn();

        UUID reservaId = UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        assertThat(reservaRepo.findById(reservaId)).isPresent();
        assertThat(solicitudRepo.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.CONVERTIDA);
    }

    @Test
    void frRes013_aprobarConMenosDe15MinDeAnticipacion_quedaRechazada() throws Exception {
        // Sin depender de la hora del día: la Solicitud se crea en un horario tranquilo
        // (pasado mañana 15:30) y después su horario se acerca a "ahora + 5 min", que es
        // lo que ve el AR si aprueba tarde. Antes la franja se armaba desde now() y cerca
        // de la medianoche quedaba con menos de 30 min (FR-RES-024) o sin lugar para la clase.
        Escenario e = escenarioBase();
        UUID solicitudId = crearSolicitud(e.tokenMenor(), e.tutorId(), e.horario());
        SolicitudSesion solicitud = solicitudRepo.findById(solicitudId).orElseThrow();
        solicitud.setHorarioPropuesto(Instant.now().plusSeconds(5 * 60));
        solicitudRepo.save(solicitud);

        mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("15 minutos")));
    }

    @Test
    void solicitudDeOtroAdulto_noSePuedeAprobar_quedaProhibido() throws Exception {
        String dniAr = dniUnico();
        String dniAr2 = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenAr2 = registrarAdultoYToken(dniAr2, "Pepe", "Garcia", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        registrarMenor(dniMenor, tokenAr);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        // AR1 autoriza; AR2 no es el AR del menor.
        autorizar(tutorId, usuarioPorDni(dniMenor).getId(), tokenAr);
        String tokenMenor = login(dniMenor);

        UUID solicitudId = crearSolicitud(tokenMenor, tutorId, dentroDeFranja(fecha));

        mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + tokenAr2))
                .andExpect(status().isForbidden());
    }

    @Test
    void frRes022_solicitudVencida_seExpiraSinConsecuencias() throws Exception {
        Escenario e = escenarioBase();
        UUID solicitudId = crearSolicitud(e.tokenMenor(), e.tutorId(), e.horario());

        // Simulo el paso del tiempo por SQL: el CHECK exige expira_at > created_at,
        // así que retrocedo AMBAS columnas (created_at = -49hs, expira_at = -1hs).
        assertThat(jdbcTemplate.update(
                "UPDATE reservas.solicitudes_sesion SET created_at = now() - interval '49 hours', "
                        + "expira_at = now() - interval '1 hour' WHERE id = ?",
                solicitudId)).isEqualTo(1);

        assertThat(solicitudService.expirarVencidas()).isGreaterThanOrEqualTo(1);
        assertThat(solicitudRepo.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.EXPIRADA);
    }

    @Test
    void frRes007_dosReservasMismoTutorYHorario_soloLaPrimeraGana() throws Exception {
        String dniAr = dniUnico();
        String dniAr2 = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor1 = dniUnico();
        String dniMenor2 = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenAr2 = registrarAdultoYToken(dniAr2, "Maria", "Fernandez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);

        // Dos menores distintos (mismos AR y horario) → dos solicitudes válidas.
        UUID menor1 = registrarMenor(dniMenor1, tokenAr);
        UUID menor2 = registrarMenor(dniMenor2, tokenAr2);
        autorizar(tutorId, menor1, tokenAr);
        autorizar(tutorId, menor2, tokenAr2);
        String tokenMenor1 = login(dniMenor1);
        String tokenMenor2 = login(dniMenor2);

        UUID solicitud1 = crearSolicitud(tokenMenor1, tutorId, dentroDeFranja(fecha));
        UUID solicitud2 = crearSolicitud(tokenMenor2, tutorId, dentroDeFranja(fecha));

        mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitud1)
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());

        // La segunda choca con la EXCLUDE (tutor, horario) → 409 (FR-RES-007).
        mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitud2)
                        .header("Authorization", "Bearer " + tokenAr2))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("ya está reservado")));
    }

    @Test
    void frRes024_franjaFueraDe30A180Minutos_quedaRechazadaCon422() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        registrarMenor(dniMenor, tokenAr);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);

        // 5 horas (300 min) supera el tope de 180 min (FR-RES-024) → 422.
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(),
                                "horaInicio", "09:00",
                                "horaFin", "14:00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("30 a 180")));

        // 15 min está por debajo del mínimo → 422.
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(),
                                "horaInicio", "15:00",
                                "horaFin", "15:15"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void stubConfirmacion_marcaConfirmada_deFormaIdempotente() throws Exception {
        Escenario e = escenarioBase();

        UUID solicitudId = crearSolicitud(e.tokenMenor(), e.tutorId(), e.horario());
        MvcResult aprobada = mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente_pago"))
                .andReturn();
        UUID reservaId = UUID.fromString(objectMapper.readTree(
                aprobada.getResponse().getContentAsString()).get("id").asText());

        Reserva confirmada = reservaService.confirmarPagoSimulado(reservaId);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        // Los webhooks de MP se reintentan: repetir la confirmación no es un error.
        Reserva idempotente = reservaService.confirmarPagoSimulado(reservaId);
        assertThat(idempotente.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void stubConfirmacion_reservaInexistente_lanzaExcepcion() {
        assertThatThrownBy(() -> reservaService.confirmarPagoSimulado(UUID.randomUUID()))
                .isInstanceOf(com.tinku.reservas.service.ReservaNoEncontradaException.class);
    }

    // ------------------------------------------------ Chunk M4-C (T-M4-05 / T-M4-06)

    /** Estudiante adulto (capacidad estudiante, sin capacidad AR) reservando para sí. */
    private record EscenarioAdulto(String tokenEstudiante, UUID estudianteId, String tokenTutor,
                                   UUID tutorId, LocalDate fecha, Instant horario) {
    }

    private EscenarioAdulto escenarioAdulto() throws Exception {
        String dniEst = dniUnico();
        String dniTutor = dniUnico();
        String tokenEst = registrarAdultoYToken(dniEst, "Lucas", "Diaz", true, false);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID estudianteId = usuarioPorDni(dniEst).getId();
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        return new EscenarioAdulto(tokenEst, estudianteId, tokenTutor, tutorId, fecha, dentroDeFranja(fecha));
    }

    private UUID crearReservaDirecta(String token, UUID tutorId, UUID beneficiarioId, Instant horario) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "beneficiarioId", beneficiarioId == null ? "" : beneficiarioId.toString(),
                                "horario", horario.toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    // ------------------------------------------------ FASE2-01 (AUD-009 / AUD-020, D6)

    /** Franja PUNTUAL {fecha} 10:00-12:00 (4 bloques de 30'). */
    private void publicarFranja10a12(String tokenTutor, LocalDate fecha) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(), "horaInicio", "10:00", "horaFin", "12:00"))))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.ResultActions postReserva(
            String token, UUID tutorId, Instant horario, int duracionMinutos) throws Exception {
        return mockMvc.perform(post("/api/reservas")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "tutorId", tutorId.toString(),
                        "horario", horario.toString(),
                        "duracionMinutos", duracionMinutos))));
    }

    private Instant a(LocalDate dia, int hora, int minuto) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, minuto), ReservasZonaHoraria.ZONA).toInstant();
    }

    @Test
    void aud009_reservasSuperpuestasDelMismoTutor_laSegundaDa409() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);
        String otroEstudiante = registrarAdultoYToken(dniUnico(), "Ana", "Paz", true, false);

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 60).andExpect(status().isCreated());
        // 10:30-11:30 pisa a 10:00-11:00 del mismo tutor: antes entraba (igualdad exacta).
        postReserva(otroEstudiante, e.tutorId(), a(dia, 10, 30), 60).andExpect(status().isConflict());
    }

    /** Tarifa del Tutor vía el endpoint de M5 (la cotización de la Reserva sale de acá). */
    private void fijarTarifa(String tokenTutor, String precio) throws Exception {
        mockMvc.perform(put("/api/pagos/tarifa")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"precioHora\": " + precio + "}"))
                .andExpect(status().isOk());
    }

    private String idDe(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void reservasContiguas_10a11_y_11a12_ambas201() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);
        String otroEstudiante = registrarAdultoYToken(dniUnico(), "Ana", "Paz", true, false);

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 60)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracionMinutos").value(60))
                .andExpect(jsonPath("$.horarioFin").value(a(dia, 11, 0).toString()));
        postReserva(otroEstudiante, e.tutorId(), a(dia, 11, 0), 60).andExpect(status().isCreated());
    }

    @Test
    void reservaQueSeSaleDeLaFranja_422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 11, 30), 60)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reservaDesalineada_10h15_422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 15), 30)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duracionNoMultiploDe30_422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 45)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void duracionMayorA180_422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja(e.tokenTutor(), dia, LocalTime.of(8, 0), LocalTime.of(11, 0));
        publicarFranja(e.tokenTutor(), dia, LocalTime.of(11, 0), LocalTime.of(14, 0));

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 8, 0), 210)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void beneficiarioConDosReservasSuperpuestasConDistintosTutores_409() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);
        String dniOtroTutor = dniUnico();
        String tokenOtroTutor = registrarTutorYToken(dniOtroTutor, "Rosa", "Gil");
        UUID otroTutorId = usuarioPorDni(dniOtroTutor).getId();
        publicarFranja10a12(tokenOtroTutor, dia);

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 60).andExpect(status().isCreated());
        postReserva(e.tokenEstudiante(), otroTutorId, a(dia, 10, 30), 30).andExpect(status().isConflict());
    }

    @Test
    void precio_90min_conPrecioHora1000_es1500() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);
        fijarTarifa(e.tokenTutor(), "1000");

        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 90)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.precio").value(1500.00));
    }

    @Test
    void precio_30min_conPrecioHora999_es499_50() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);
        fijarTarifa(e.tokenTutor(), "999");

        String id = idDe(postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 30)
                .andExpect(status().isCreated()));
        assertThat(reservaRepo.findById(UUID.fromString(id)).orElseThrow().getPrecio())
                .isEqualByComparingTo("499.50");
    }

    @Test
    void solicitudDelMenor_conDuracion_alAprobarseLaReservaLaHereda() throws Exception {
        Escenario e = escenarioBase();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);

        MvcResult sol = mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + e.tokenMenor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horarioPropuesto", a(dia, 10, 30).toString(),
                                "duracionMinutos", 90))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID solicitudId = UUID.fromString(
                objectMapper.readTree(sol.getResponse().getContentAsString()).get("id").asText());
        assertThat(solicitudRepo.findById(solicitudId).orElseThrow().getDuracionMinutos()).isEqualTo(90);

        mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duracionMinutos").value(90))
                .andExpect(jsonPath("$.horarioFin").value(a(dia, 12, 0).toString()))
                .andExpect(jsonPath("$.precio").value(22500.00)); // 15000/h × 1,5 h
    }

    @Test
    void reprogramar_conservaLaDuracionYElPrecio() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);
        UUID reservaId = UUID.fromString(idDe(
                postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 0), 60)
                        .andExpect(status().isCreated())));
        confirmarPago(e.tokenEstudiante(), reservaId);

        reprogramar(e.tokenEstudiante(), reservaId, a(dia, 11, 0));

        Reserva r = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(r.getDuracionMinutos()).isEqualTo(60);
        assertThat(r.getHorarioFin()).isEqualTo(a(dia, 12, 0));
        assertThat(r.getPrecio()).isEqualByComparingTo("15000");
    }

    @Test
    void tM412_horariosDelDia_bloqueLibreDisponible_yOcupadoTrasReservar() throws Exception {
        // Auditoría 2026-09-18 (gap del frontend, T-M4-12): la franja puntual
        // de escenarioAdulto() es 15:00-16:00 — un solo bloque de 60 min.
        EscenarioAdulto e = escenarioAdulto();

        mockMvc.perform(get("/api/tutores/{id}/horarios", e.tutorId())
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .param("fecha", e.fecha().toString())
                        .param("duracionMinutos", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isAvailable").value(true))
                .andExpect(jsonPath("$[0].startTime").exists())
                .andExpect(jsonPath("$[0].endTime").exists());

        crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());

        mockMvc.perform(get("/api/tutores/{id}/horarios", e.tutorId())
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .param("fecha", e.fecha().toString())
                        .param("duracionMinutos", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isAvailable").value(false));
    }

    @Test
    void tM412_horarios_duracionInvalida_422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        mockMvc.perform(get("/api/tutores/{id}/horarios", e.tutorId())
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .param("fecha", e.fecha().toString())
                        .param("duracionMinutos", "0"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void horariosDelDia_bloqueDe60_ofreceInicioCada30Minutos() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        LocalDate dia = e.fecha().plusDays(1);
        publicarFranja10a12(e.tokenTutor(), dia);

        mockMvc.perform(get("/api/tutores/{id}/horarios", e.tutorId())
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .param("fecha", dia.toString())
                        .param("duracionMinutos", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].startTime").value(a(dia, 10, 0).toString()))
                .andExpect(jsonPath("$[1].startTime").value(a(dia, 10, 30).toString()))
                .andExpect(jsonPath("$[2].startTime").value(a(dia, 11, 0).toString()));

        // Una reserva de 30 min a las 10:30 ocupa [10:30, 11:00): tapa los bloques de
        // 60 que empiezan 10:00 y 10:30, pero el de 11:00 queda libre (contigua).
        postReserva(e.tokenEstudiante(), e.tutorId(), a(dia, 10, 30), 30).andExpect(status().isCreated());

        mockMvc.perform(get("/api/tutores/{id}/horarios", e.tutorId())
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .param("fecha", dia.toString())
                        .param("duracionMinutos", "60"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isAvailable").value(false))
                .andExpect(jsonPath("$[1].isAvailable").value(false))
                .andExpect(jsonPath("$[2].isAvailable").value(true));
    }

    @Test
    void horariosDelDia_duracionNoMultiploDe30_422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        mockMvc.perform(get("/api/tutores/{id}/horarios", e.tutorId())
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .param("fecha", e.fecha().toString())
                        .param("duracionMinutos", "45"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes001_estudianteAdulto_reservaParaSiMismo() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", e.horario().toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("pendiente_pago"))
                .andExpect(jsonPath("$.beneficiarioId").value(e.estudianteId().toString()))
                .andReturn();

        UUID reservaId = UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        assertThat(reservaRepo.findById(reservaId)).isPresent();
    }

    @Test
    void frRes003_arReservaPorSuMenor_conTutorAutorizado() throws Exception {
        Escenario e = escenarioBase(); // AR + menor + tutor autorizado + franja

        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());

        Reserva r = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
        assertThat(r.getBeneficiario().getId()).isEqualTo(e.menorId());
        // El pagador es el AR del menor (mismo que creó el escenario).
        assertThat(r.getPagador().getId()).isEqualTo(usuarioPorDni(e.dniAr()).getId());
    }

    @Test
    void crearReserva_beneficiarioMenor_conFlagTrue_ok() throws Exception {
        // T-TES-10: con la flag en true (habilitada vía @TestPropertySource en
        // esta clase) el gate deja pasar el flujo existente FR-RES-003.
        Escenario e = escenarioBase();

        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());

        assertThat(reservaRepo.findById(reservaId).orElseThrow().getBeneficiario().getId())
                .isEqualTo(e.menorId());
    }

    @Test
    void frRes021_arReservaParaMenor_conTutorNoAutorizado_queda403() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID menorId = registrarMenor(dniMenor, tokenAr);
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        // NO se autoriza al tutor para el menor.

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "beneficiarioId", menorId.toString(),
                                "horario", dentroDeFranja(fecha).toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void articuloII_menorNuncaPuedeCrearReservaDirecta_queda403() throws Exception {
        Escenario e = escenarioBase(); // tokenMenor disponible

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenMenor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", e.horario().toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void frRes013_directaFueraDeVentana15Min_queda422() throws Exception {
        // La ventana mínima (FR-RES-013) se valida antes que la franja: no hace falta
        // publicar una franja desde now() (cerca de la medianoche no entraba). El
        // mensaje confirma que el rechazo es por la ventana y no por otra regla.
        EscenarioAdulto e = escenarioAdulto();
        Instant pronto = Instant.now().plusSeconds(5 * 60);

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", pronto.toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("15 minutos")));
    }

    @Test
    void frRes012_directaFueraDeFranja_queda422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        Instant fuera = dentroDeFranja(e.fecha().plusDays(1));

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", fuera.toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void directa_tutorInexistente_queda404() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", UUID.randomUUID().toString(),
                                "horario", e.horario().toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isNotFound());
    }

    @Test
    void frRes020_timeoutDePago_cancelaYLiberaElHorario() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        assertThat(reservaRepo.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.PENDIENTE_PAGO);

        // Simulo el paso del tiempo por SQL (como frRes022) y hago correr el barrido.
        assertThat(jdbcTemplate.update(
                "UPDATE reservas.reservas SET created_at = now() - interval '16 minutes' WHERE id = ?",
                reservaId)).isEqualTo(1);

        assertThat(reservaService.expirarPendientesDePago()).isGreaterThanOrEqualTo(1);
        Reserva expirada = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(expirada.getEstado()).isEqualTo(EstadoReserva.CANCELADA);

        // La EXCLUDE ignora canceladas → el mismo horario queda libre (2ª reserva gana).
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", e.horario().toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated());
    }

    @Test
    void frRes020_confirmarPago_cancelaElTimeout() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());

        Reserva confirmada = reservaService.confirmarPagoSimulado(reservaId);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        // El job ya no está programado: confirmar canceló el timeout (T-M4-06).
        assertThat(scheduler.checkExists(
                reservaService.triggerTimeoutPago(reservaId))).isFalse();
    }

    // ------------------------------------------------ Chunk M4-D (T-M4-07 / T-M4-08)

    private void confirmarPago(String token, UUID reservaId) {
        Reserva confirmada = reservaService.confirmarPagoSimulado(reservaId);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    private void reprogramar(String token, UUID reservaId, Instant nuevoHorario) throws Exception {
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", reservaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nuevoHorario", nuevoHorario.toString()))))
                .andExpect(status().isOk());
    }

    private void publicarFranja(String tokenTutor, LocalDate fecha, LocalTime inicio, LocalTime fin) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(),
                                "horaInicio", inicio.toString(),
                                "horaFin", fin.toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void frRes015_reprogramarConfirmada_conservaPrecio_actualizaHorarioYReAgendaJobs() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        confirmarPago(e.tokenEstudiante(), reservaId);

        // La Reserva confirmada ya tiene su Sesión en M3 con los jobs al horario viejo.
        SesionAprendizaje sesion = sesionRepo.findByReservaId(reservaId).orElseThrow();
        assertThat(scheduler.checkExists(SesionService.triggerSala(sesion.getId()))).isTrue();

        // Franja nueva para +4 días y reprogramación a 15:30 de ese día.
        LocalDate nuevaFecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(4);
        publicarFranjaPuntual(e.tokenTutor(), nuevaFecha);
        Instant nuevoHorario = dentroDeFranja(nuevaFecha);
        long reservasAntes = reservaRepo.count();

        reprogramar(e.tokenEstudiante(), reservaId, nuevoHorario);

        Reserva r = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(r.getHorario()).isEqualTo(nuevoHorario);
        assertThat(r.getPrecio()).isEqualByComparingTo("7500"); // precio original intacto (30 min a 15000/h)
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reservaRepo.count()).isEqualTo(reservasAntes); // misma fila: sin transacción ni reserva nueva

        // M3 re-agendó la Sesión al nuevo horario: el trigger de sala cambió.
        assertThat(scheduler.checkExists(SesionService.triggerSala(sesion.getId()))).isTrue();
        Instant startSala = scheduler.getTrigger(SesionService.triggerSala(sesion.getId()))
                .getStartTime().toInstant();
        assertThat(startSala).isEqualTo(nuevoHorario.minus(java.time.Duration.ofMinutes(5)));
    }

    @Test
    void frRes016_reprogramarConMenosDe24hs_seTrataComoCancelacionTardia() throws Exception {
        String dni = dniUnico();
        String dniTutor = dniUnico();
        String token = registrarAdultoYToken(dni, "Lucas", "Diaz", true, false);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();

        // Franja HOY que cubre `pronto` (≈2hs → <24hs de anticipación), respetando
        // FR-RES-024 (30-180 min) sin depender de la hora del día.
        Instant pronto = Instant.now().plus(2, java.time.temporal.ChronoUnit.HOURS)
                .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        if (pronto.atZone(ReservasZonaHoraria.ZONA).toLocalTime().isAfter(LocalTime.of(23, 0))) {
            // Cerca de la medianoche la franja de 2 h no entra en el día: las 9:00 de mañana
            // siguen estando a menos de 24 hs (ahora son más de las 21:00).
            pronto = pronto.atZone(ReservasZonaHoraria.ZONA).toLocalDate().plusDays(1)
                    .atTime(9, 0).atZone(ReservasZonaHoraria.ZONA).toInstant();
        }
        ZonedDateTime punto = pronto.atZone(ReservasZonaHoraria.ZONA);
        // D6: la franja arranca en `pronto` (minuto entero) → horario alineado a 30'.
        LocalTime inicio = punto.toLocalTime();
        LocalTime fin = punto.toLocalTime().plusHours(2);
        if (inicio.isAfter(punto.toLocalTime())) inicio = LocalTime.MIDNIGHT;
        if (fin.isBefore(inicio)) fin = LocalTime.of(23, 59, 59);
        publicarFranja(tokenTutor, punto.toLocalDate(), inicio, fin);

        UUID reservaId = crearReservaDirecta(token, tutorId, null, pronto);
        confirmarPago(token, reservaId);

        // El intento de reprogramación con <24hs se trata como cancelación tardía.
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", reservaId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nuevoHorario", dentroDeFranja(
                                        LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2)).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelada"));

        Reserva r = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.VOLUNTARIA);
    }

    @Test
    void cancelar_confirmada_porPagador_cancelaYDesagendaLaSesionEnM3() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        confirmarPago(e.tokenEstudiante(), reservaId);
        SesionAprendizaje sesion = sesionRepo.findByReservaId(reservaId).orElseThrow();

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelada"))
                .andExpect(jsonPath("$.motivoCancelacion").value("voluntaria"));

        Reserva r = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        // M3 desagendó los jobs de la Sesión derivada (listener reserva.cancelada).
        assertThat(scheduler.checkExists(SesionService.triggerSala(sesion.getId()))).isFalse();
        assertThat(scheduler.checkExists(SesionService.triggerNoShow(sesion.getId()))).isFalse();
        assertThat(scheduler.checkExists(SesionService.triggerCorte(sesion.getId()))).isFalse();
    }

    @Test
    void cancelar_confirmada_porElTutor_quedaPermitida() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        confirmarPago(e.tokenEstudiante(), reservaId);

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelada"));
    }

    @Test
    void cancelar_pendientePago_cancelaSinEventoYLiberaHorario() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("cancelada"));

        assertThat(reservaRepo.findById(reservaId).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CANCELADA);
        // El timeout de pago queda desprogramado y el horario se libera.
        assertThat(scheduler.checkExists(reservaService.triggerTimeoutPago(reservaId))).isFalse();
        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", e.horario().toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isCreated());
    }

    @Test
    void cancelar_reservaDeOtro_queda403() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        String otroDni = dniUnico();
        String tokenOtro = registrarAdultoYToken(otroDni, "Pepe", "Garcia", true, false);

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelar_reservaYaCancelada_queda422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void articuloII_menorNuncaReprogramaNiCancela_queda403() throws Exception {
        Escenario e = escenarioBase();
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());
        confirmarPago(e.tokenAr(), reservaId);

        mockMvc.perform(post("/api/reservas/{id}/reprogramar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenMenor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nuevoHorario", dentroDeFranja(
                                        LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(4)).toString()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenMenor()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reprogramar_reservaPendiente_queda422() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());

        mockMvc.perform(post("/api/reservas/{id}/reprogramar", reservaId)
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nuevoHorario", dentroDeFranja(
                                        LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(4)).toString()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void frRes007_reprogramar_aHorarioOcupadoDelMismoTutor_queda409() throws Exception {
        // Dos estudiantes distintos reservan al mismo Tutor en horarios distintos...
        String dniA = dniUnico();
        String dniB = dniUnico();
        String dniTutor = dniUnico();
        String tokenA = registrarAdultoYToken(dniA, "Ana", "Lopez", true, false);
        String tokenB = registrarAdultoYToken(dniB, "Maria", "Fernandez", true, false);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);

        UUID reservaA = crearReservaDirecta(tokenA, tutorId, null, dentroDeFranja(fecha));
        confirmarPago(tokenA, reservaA);

        // ...B queda en +3 días 15:30 (no hago confirm: es la misma franja puntual).
        LocalDate fechaB = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(3);
        publicarFranjaPuntual(tokenTutor, fechaB);
        UUID reservaB = crearReservaDirecta(tokenB, tutorId, null, dentroDeFranja(fechaB));
        confirmarPago(tokenB, reservaB);

        // Reprogramar B al horario de A → EXCLUDE (tutor, horario) → 409 (FR-RES-007).
        mockMvc.perform(post("/api/reservas/{id}/reprogramar", reservaB)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "nuevoHorario", dentroDeFranja(fecha).toString()))))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------ Chunk M4-E (T-M4-09 / T-M4-10)

    @Test
    void frRep006_tutorConCalificacionPendiente_quedaBloqueadaSuNuevaReserva() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        // M7 (aún no existe) reporta al Tutor con una calificación pendiente.
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of(e.tutorId()));

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", e.horario().toString(),
                                "duracionMinutos", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void frSec008_sancionTutor_cancelaSoloReservasFuturas_conMotivoSancion() throws Exception {
        EscenarioAdulto e = escenarioAdulto(); // franja puntual en e.fecha() (+2 días)
        publicarFranjaPuntual(e.tokenTutor(), e.fecha().plusDays(1));
        publicarFranjaPuntual(e.tokenTutor(), e.fecha().plusDays(2));

        // Futura sin pagar → se cancela sin evento (mismo criterio que FR-RES-017).
        UUID pendiente = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null,
                dentroDeFranja(e.fecha().plusDays(1)));
        // Futura confirmada → se cancela y M3 desagenda su Sesión.
        UUID confirmada = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null,
                dentroDeFranja(e.fecha().plusDays(2)));
        confirmarPago(e.tokenEstudiante(), confirmada);
        UUID sesionId = sesionRepo.findByReservaId(confirmada).orElseThrow().getId();
        // Ya ocurrida (horario corrido al pasado por SQL): es trabajo hecho, FR-PAG-011
        // lo paga igual — la sanción NO la toca.
        UUID pasada = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());
        confirmarPago(e.tokenEstudiante(), pasada);
        assertThat(jdbcTemplate.update(
                "UPDATE reservas.reservas SET horario = now() - interval '2 days' WHERE id = ?",
                pasada)).isEqualTo(1);

        // M9 no existe aún: publico el evento como lo hará (Chunk M9-D).
        eventos.publishEvent(new DenunciaResueltaEvent(this, UUID.randomUUID(), e.tutorId()));

        Reserva p = reservaRepo.findById(pendiente).orElseThrow();
        assertThat(p.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(p.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.SANCION);

        Reserva c = reservaRepo.findById(confirmada).orElseThrow();
        assertThat(c.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(c.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.SANCION);
        // El listener reserva.cancelada llegó a M3 y desagendó los jobs.
        assertThat(scheduler.checkExists(SesionService.triggerSala(sesionId))).isFalse();

        Reserva r = reservaRepo.findById(pasada).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(r.getMotivoCancelacion()).isNull();
    }

    @Test
    void frSec012_sancionAlAdultoResponsable_cancelaReservasFuturasQuePago() throws Exception {
        Escenario e = escenarioBase(); // AR pagando por su menor
        UUID reservaId = crearReservaDirecta(e.tokenAr(), e.tutorId(), e.menorId(), e.horario());
        confirmarPago(e.tokenAr(), reservaId);
        UUID sesionId = sesionRepo.findByReservaId(reservaId).orElseThrow().getId();

        eventos.publishEvent(new DenunciaResueltaEvent(
                this, UUID.randomUUID(), usuarioPorDni(e.dniAr()).getId()));

        Reserva r = reservaRepo.findById(reservaId).orElseThrow();
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(r.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.SANCION);
        assertThat(scheduler.checkExists(SesionService.triggerSala(sesionId))).isFalse();
    }

    // ------------------------------------------------ Chunk M4-F (T-M4-11)

    private int crearReservaDirectaStatus(String token, UUID tutorId, UUID beneficiarioId,
                                          Instant horario, java.util.concurrent.CountDownLatch largada) throws Exception {
        if (largada != null) {
            largada.await(30, TimeUnit.SECONDS);
        }
        return mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "beneficiarioId", beneficiarioId == null ? "" : beneficiarioId.toString(),
                                "horario", horario.toString(),
                                "duracionMinutos", 30))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void frRes007_dosReservasSimultaneasMismoHorario_laResuelveLaExcludeConstraint() throws Exception {
        EscenarioAdulto e = escenarioAdulto();
        long reservasAntes = reservaRepo.count();

        // Dos requests idénticos (mismo Tutor, mismo horario) que parten al unísono:
        // la validación de aplicación no alcanza (ninguna ve a la otra antes de
        // insertar) — la EXCLUDE constraint de V9 decide: una gana, la otra 409.
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> res1 = pool.submit(() -> crearReservaDirectaStatus(
                    e.tokenEstudiante(), e.tutorId(), null, e.horario(), largada));
            Future<Integer> res2 = pool.submit(() -> crearReservaDirectaStatus(
                    e.tokenEstudiante(), e.tutorId(), null, e.horario(), largada));
            largada.countDown();

            Integer s1 = res1.get(30, TimeUnit.SECONDS);
            Integer s2 = res2.get(30, TimeUnit.SECONDS);
            // Una sola reserva creada: quién ganó no importa, el resultado es único.
            assertThat(Set.of(s1, s2)).isEqualTo(Set.of(201, 409));
            assertThat(reservaRepo.count()).isEqualTo(reservasAntes + 1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------ Endpoints para el front (T-M4-GET)

    @Test
    void getReservas_visibilidadPorRol_pagadorTutorYCiertoParticipanteCadaUno() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        UUID r1 = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());

        // El pagador (estudiante) ve su reserva en el listado y por id.
        mockMvc.perform(get("/api/reservas").header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(r1.toString()))
                .andExpect(jsonPath("$[0].pagadorId").value(e.estudianteId().toString()));
        mockMvc.perform(get("/api/reservas/" + r1).header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(r1.toString()));

        // El tutor de esa reserva también es participante y puede verla.
        mockMvc.perform(get("/api/reservas/" + r1).header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(r1.toString()));

        // Un tercero autenticado sin relación con la reserva: no la lista ni la ve.
        String tokenOtro = registrarAdultoYToken(dniUnico(), "Sofia", "Gomez", true, false);
        mockMvc.perform(get("/api/reservas").header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/reservas/" + r1).header("Authorization", "Bearer " + tokenOtro))
                .andExpect(status().isNotFound());

        // Sin autenticación: nada de esto es público (403, ver SecurityHttpTest).
        mockMvc.perform(get("/api/reservas")).andExpect(status().isForbidden());
    }

    @Test
    void getTutores_perfilPublicoYfranjasActivas_ocultanDatosSensiblesHastaM2M7() throws Exception {
        EscenarioAdulto e = escenarioAdulto(); // franja puntual publicada en e.fecha()

        // Perfil público del tutor: sin passwordHash ni DNI; M2/M7 ausentes → materias
        // vacías y calificación oculta (FR-REP-007: promedio null si count < 5).
        mockMvc.perform(get("/api/tutores/" + e.tutorId()).header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(e.tutorId().toString()))
                .andExpect(jsonPath("$.nombre").value("Pablo"))
                .andExpect(jsonPath("$.tipo").value("TUTOR"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.materias").isEmpty())
                .andExpect(jsonPath("$.nivel").isEmpty())
                .andExpect(jsonPath("$.calificacionPromedio").isEmpty())
                .andExpect(jsonPath("$.cantidadCalificaciones").value(0));

        // Franjas activas publicadas, visibles para un participante autenticado.
        mockMvc.perform(get("/api/tutores/" + e.tutorId() + "/franjas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fechaEspecifica").value(e.fecha().toString()))
                .andExpect(jsonPath("$[0].horaInicio").value("15:00:00"))
                .andExpect(jsonPath("$[0].activa").value(true));

        // Tutor inexistente → 404.
        mockMvc.perform(get("/api/tutores/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isNotFound());
    }
}