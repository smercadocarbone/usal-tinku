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
import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.SolicitudSesion;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.repository.SolicitudSesionRepository;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.reservas.service.SolicitudService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    private record Escenario(String tokenAr, String tokenMenor, String tokenTutor, UUID menorId,
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
        return new Escenario(tokenAr, tokenMenor, tokenTutor, menorId, tutorId, fecha, dentroDeFranja(fecha));
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

        // Franja puntual de TODO el día de hoy: cubre cualquier horario de hoy.
        LocalDate hoy = LocalDate.now(ReservasZonaHoraria.ZONA);
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", hoy.toString(),
                                "horaInicio", "00:00:00",
                                "horaFin", "23:59:59"))))
                .andExpect(status().isCreated());
        autorizar(tutorId, menorId, tokenAr);
        String tokenMenor = login(dniMenor);

        // Horario en 5 minutos: dentro de la franja, pero fuera de la ventana
        // mínima de 15 min (FR-RES-013). Si el horario cayera pasada la
        // medianoche el test no aplica — correr lejos de las 23:55.
        Instant pronto = Instant.now().plusSeconds(5 * 60);
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
}