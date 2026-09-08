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
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.model.SolicitudSesion;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración del Chunk M4-B (T-M4-02, T-M4-03, T-M4-04): las Historias
 * de Usuario del Spec M4 ejercitadas de punta a punta (HTTP + Spring Security +
 * JPA + Flyway + PostgreSQL via Testcontainers), con el mismo patrón de
 * MatchingFlujosIntegracionTest: OCR mockeado, resto real.
 *
 * La tarifa se resuelve por el stub {@link com.tinku.reservas.port.TarifaProveedorStub}
 * con tinku.reservas.tarifa-stub=15000 (application-test.yml) — M5 no existe aún.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
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

    @MockBean OcrService ocrService;
    @MockBean MatchingServiceClient matchingClient;
    @MockBean ReputacionSignalProvider reputacion;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    /** DNI numérico único por invocación: evita colisiones de existsByDni en la BD
     *  Testcontainers compartida entre los tests de esta clase. */
    private String dniUnico() {
        return String.format("%08d", 30_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocks() {
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
    }

    // ------------------------------------------------ helpers

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    private String registrarAdultoYToken(String dni, String nombre, String apellido,
                                         boolean capEst, boolean capAr) throws Exception {
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15),
                                PASSWORD, capEst, capAr)))
                        .file(foto()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    private String registrarTutorYToken(String dni, String nombre, String apellido) throws Exception {
        when(ocrService.procesarDocumento(any()))
                .thenReturn(resultado(dni, nombre, apellido, LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(
                                dni, nombre, apellido, LocalDate.of(1990, 5, 15), PASSWORD)))
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
        when(ocrService.procesarDocumento(any()))
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
                                "horarioPropuesto", horario.toString()))))
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
                                        LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2)).toString()))))
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
                                "horarioPropuesto", fuera.toString()))))
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
                .andExpect(jsonPath("$.precio").value(15000.00))
                .andReturn();

        UUID reservaId = UUID.fromString(
                objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
        assertThat(reservaRepo.findById(reservaId)).isPresent();
        assertThat(solicitudRepo.findById(solicitudId).orElseThrow().getEstado())
                .isEqualTo(EstadoSolicitud.CONVERTIDA);
    }

    @Test
    void frRes013_aprobarConMenosDe15MinDeAnticipacion_quedaRechazada() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez", true, true);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID menorId = registrarMenor(dniMenor, tokenAr);

        // Franja PUNTUAL que cubre "ahora+5min" respetando FR-RES-024 (30-180
        // min), sin depender de la hora del día: arranco 1h antes del horario y
        // termino 2h después; si el intervalo cruzara la medianoche lo recorto
        // al borde del día (la duración resultante sigue dentro de 30-180 min).
        Instant pronto = Instant.now().plusSeconds(5 * 60);
        ZonedDateTime punto = pronto.atZone(ReservasZonaHoraria.ZONA);
        LocalDate hoy = punto.toLocalDate();
        LocalTime hora = punto.toLocalTime();
        LocalTime inicioFranja = hora.minusHours(1);
        LocalTime finFranja = hora.plusHours(2);
        if (inicioFranja.isAfter(hora)) {
            inicioFranja = LocalTime.MIDNIGHT; // inicio cayó en el día anterior
        } else if (finFranja.isBefore(inicioFranja)) {
            finFranja = LocalTime.of(23, 59, 59); // fin cruzó la medianoche
        }
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", hoy.toString(),
                                "horaInicio", inicioFranja.toString(),
                                "horaFin", finFranja.toString()))))
                .andExpect(status().isCreated());
        autorizar(tutorId, menorId, tokenAr);
        String tokenMenor = login(dniMenor);

        UUID solicitudId = crearSolicitud(tokenMenor, tutorId, pronto);

        mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isUnprocessableEntity());
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

        mockMvc.perform(post("/api/test/reservas/{id}/confirmar-pago-simulado", reservaId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("confirmada"));

        // Los webhooks de MP se reintentan: repetir la confirmación no es un error.
        mockMvc.perform(post("/api/test/reservas/{id}/confirmar-pago-simulado", reservaId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("confirmada"));
    }

    @Test
    void stubConfirmacion_reservaInexistente_queda404() throws Exception {
        Escenario e = escenarioBase();

        mockMvc.perform(post("/api/test/reservas/{id}/confirmar-pago-simulado", UUID.randomUUID())
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isNotFound());
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
                                "horario", horario.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    @Test
    void frRes001_estudianteAdulto_reservaParaSiMismo() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + e.tokenEstudiante())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horario", e.horario().toString()))))
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
                                "horario", dentroDeFranja(fecha).toString()))))
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
                                "horario", e.horario().toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void frRes013_directaFueraDeVentana15Min_queda422() throws Exception {
        String dniEst = dniUnico();
        String dniTutor = dniUnico();
        String tokenEst = registrarAdultoYToken(dniEst, "Lucas", "Diaz", true, false);
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioPorDni(dniTutor).getId();

        Instant pronto = Instant.now().plusSeconds(5 * 60);
        ZonedDateTime punto = pronto.atZone(ReservasZonaHoraria.ZONA);
        LocalDate hoy = punto.toLocalDate();
        LocalTime hora = punto.toLocalTime();
        LocalTime inicioFranja = hora.minusHours(1);
        LocalTime finFranja = hora.plusHours(2);
        if (inicioFranja.isAfter(hora)) {
            inicioFranja = LocalTime.MIDNIGHT;
        } else if (finFranja.isBefore(inicioFranja)) {
            finFranja = LocalTime.of(23, 59, 59);
        }
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", hoy.toString(),
                                "horaInicio", inicioFranja.toString(),
                                "horaFin", finFranja.toString()))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "horario", pronto.toString()))))
                .andExpect(status().isUnprocessableEntity());
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
                                "horario", fuera.toString()))))
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
                                "horario", e.horario().toString()))))
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
                                "horario", e.horario().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void frRes020_confirmarPago_cancelaElTimeout() throws Exception {
        EscenarioAdulto e = escenarioAdulto();

        UUID reservaId = crearReservaDirecta(e.tokenEstudiante(), e.tutorId(), null, e.horario());

        mockMvc.perform(post("/api/test/reservas/{id}/confirmar-pago-simulado", reservaId)
                        .header("Authorization", "Bearer " + e.tokenEstudiante()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("confirmada"));

        // El job ya no está programado: confirmar canceló el timeout (T-M4-06).
        assertThat(scheduler.checkExists(
                reservaService.triggerTimeoutPago(reservaId))).isFalse();
    }

    // ------------------------------------------------ Chunk M4-D (T-M4-07 / T-M4-08)

    private void confirmarPago(String token, UUID reservaId) throws Exception {
        mockMvc.perform(post("/api/test/reservas/{id}/confirmar-pago-simulado", reservaId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("confirmada"));
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
        assertThat(r.getPrecio()).isEqualByComparingTo("15000"); // precio original intacto
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
        Instant pronto = Instant.now().plus(2, java.time.temporal.ChronoUnit.HOURS);
        ZonedDateTime punto = pronto.atZone(ReservasZonaHoraria.ZONA);
        LocalTime inicio = punto.toLocalTime().minusHours(1);
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
                                "horario", e.horario().toString()))))
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
}