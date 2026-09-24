package com.tinku.aula.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.LiveKitService;
import com.tinku.aula.SesionService;
import com.tinku.pagos.evento.SesionEvento;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.evento.SesionNoShowDobleEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.MatchingServiceClient;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.ReservasZonaHoraria;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk M3-B (T-M3-03/04/05) de punta a punta: una Historia por cada job/endpoint.
 * El LiveKit real se mockea (no hay credenciales en CI, T-000-06); el resto es
 * real (Spring Security + JPA + Flyway + PostgreSQL + el JOB_STORE de Quartz).
 * Los eventos de sesión hacia M5 se capturan con un listener de prueba y se
 * verifica que se emitan una única vez.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
// T-TES-10/DT7: siembra Reservas con beneficiario menor vía repositorio (es la
// unidad de M3); la flag en true mantiene ese escenario activo mientras el
// piloto corre con el gate OFF (GateMenoresPilotoIntegracionTest).
@TestPropertySource(properties = "tinku.menores.sesiones-habilitadas=true")
class SesionesIntegracionTest {

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
    @Autowired ReservaService reservaService;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired SesionService sesionService;
    @Autowired Scheduler scheduler;

    @MockBean OcrService ocrService;
    @MockBean MatchingServiceClient matchingClient;
    @MockBean ReputacionSignalProvider reputacion;
    @MockBean LiveKitService liveKitService;

    @Value("${tinku.livekit.api-key}") String apiKey;
    @Value("${tinku.livekit.api-secret}") String apiSecret;

    private static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private static final List<SesionEvento> EVENTOS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class ConfigCapturaEventos {
        @Bean
        ApplicationListener<SesionEvento> capturarEventos() {
            return EVENTOS::add;
        }
    }

    @BeforeEach
    void programarMocks() {
        EVENTOS.clear();
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado("10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        when(liveKitService.crearSala(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(liveKitService.generarTokenParticipante(anyString(), anyString(), anyString()))
                .thenReturn("jwt-test-token");
        when(liveKitService.getBaseUrl()).thenReturn("wss://test.livekit.cloud");
    }

    private String dniUnico() {
        return String.format("%08d", 30_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    private ResultadoOcr resultado(String dni, String nombre, String apellido, LocalDate nac) {
        return new ResultadoOcr(true, dni, nombre, apellido, nac);
    }

    // ------------------------------------------------ helpers HTTP (registro/franjas)

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

    private void registrarMenor(String dniMenor, String tokenAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(resultado(dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(
                                dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20),
                                PASSWORD, true, "v1")))
                        .file(foto())
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());
    }

    private void autorizar(String tokenAr, UUID tutorId, UUID menorId) throws Exception {
        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    /** Franja PUNTUAL {fecha} 15:00-16:00 (60 min → el fin agendado es 16:00). */
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

    private Instant dentroDeFranja(LocalDate fecha) {
        return ZonedDateTime.of(fecha, LocalTime.of(15, 30), ReservasZonaHoraria.ZONA).toInstant();
    }

    private record Escenario(String tokenAr, String tokenMenor, String tokenTutor,
                             String dniAr, String dniTutor, String dniMenor,
                             UUID menorId, UUID tutorId, Instant horario) {
    }

    /** AR + Tutor + menor (autorizado), con franja puntual dentro de 2 días. */
    private Escenario escenarioBase() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdultoYToken(dniAr, "Ana", "Lopez");
        String tokenTutor = registrarTutorYToken(dniTutor, "Pablo", "Sosa");
        UUID tutorId = usuarioRepository.findByDni(dniTutor).orElseThrow().getId();
        registrarMenor(dniMenor, tokenAr);
        UUID menorId = usuarioRepository.findByDni(dniMenor).orElseThrow().getId();
        LocalDate fecha = LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(2);
        publicarFranjaPuntual(tokenTutor, fecha);
        autorizar(tokenAr, tutorId, menorId);
        String tokenMenor = login(dniMenor);
        return new Escenario(tokenAr, tokenMenor, tokenTutor,
                dniAr, dniTutor, dniMenor, menorId, tutorId, dentroDeFranja(fecha));
    }

    // ------------------------------------------------ helpers de sesión

    /** Reserva CONFIRMADA creada directo en la BD (para ejercitar jobs aislados). */
    private Reserva reservaConfirmadaDirecta(Escenario e) {
        Reserva reserva = new Reserva();
        reserva.setPagador(usuarioRepository.findByDni(e.dniAr()).orElseThrow());
        reserva.setBeneficiario(usuarioRepository.findByDni(e.dniMenor()).orElseThrow());
        reserva.setTutor(usuarioRepository.findByDni(e.dniTutor()).orElseThrow());
        reserva.setHorario(e.horario());
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        return reservaRepository.save(reserva);
    }

    private SesionAprendizaje programarYCargar(Reserva reserva) {
        UUID sesionId = sesionService.programarSesion(reserva.getId()).getId();
        return sesionRepository.findById(sesionId).orElseThrow();
    }

    private boolean existeTrigger(String prefijo, UUID sesionId) throws SchedulerException {
        return scheduler.checkExists(org.quartz.TriggerKey.triggerKey(
                prefijo + "-trigger-" + sesionId, "m3-aula"));
    }

    // webhooks firmados de LiveKit (same helper as LiveKitWebhookIntegracionTest)

    private String cuerpoWebhook(String evento, String identidad, String sala) throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("id", "evt_" + UUID.randomUUID());
        payload.put("event", evento);
        payload.putObject("room").put("name", sala).put("sid", "RO_abc");
        payload.putObject("participant").put("identity", identidad).put("name", identidad);
        return objectMapper.writeValueAsString(payload);
    }

    private String firmar(byte[] body) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(apiKey)
                .claim("sha256", Base64.getEncoder().encodeToString(hash))
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private void joinWebhook(String sala, UUID idUsuario) throws Exception {
        String body = cuerpoWebhook("participant_joined", idUsuario.toString(), sala);
        mockMvc.perform(post("/api/webhooks/livekit")
                        .contentType("application/webhook+json")
                        .header("Authorization", "Bearer " + firmar(body.getBytes(StandardCharsets.UTF_8)))
                        .content(body))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------ T-M3-03: sala a T-5

    @Test
    void us1_reservaConfirmada_creaSesionNoIniciadaYAgendaTresJobs() throws Exception {
        Escenario e = escenarioBase();
        UUID solicitudId = crearSolicitud(e, e.horario());
        MvcResult aprobada = mockMvc.perform(post("/api/solicitudes/{id}/aprobar", solicitudId)
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isCreated())
                .andReturn();
        UUID reservaId = UUID.fromString(
                objectMapper.readTree(aprobada.getResponse().getContentAsString()).get("id").asText());

        Reserva confirmada = reservaService.confirmarPagoSimulado(reservaId);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);

        SesionAprendizaje sesion = sesionRepository.findByReservaId(reservaId).orElseThrow();
        assertThat(sesion.getEstado()).isEqualTo("no_iniciada");
        assertThat(sesion.getLivekitRoomId()).isNull();

        assertThat(sesionRepository.findByReservaId(reservaId).orElseThrow().getEstado())
                .isEqualTo("no_iniciada");
        assertThat(existeTrigger("sala", sesion.getId())).isTrue();
        assertThat(existeTrigger("no-show", sesion.getId())).isTrue();
        assertThat(existeTrigger("corte", sesion.getId())).isTrue();
    }

    @Test
    void tM303_crearSalaDiferida_creaSalaEnLiveKitYGrabaLivekitRoomId() throws Exception {
        Escenario e = escenarioBase();
        SesionAprendizaje sesion = programarYCargar(reservaConfirmadaDirecta(e));

        sesionService.crearSalaDiferida(sesion.getId());

        verify(liveKitService).crearSala("sesion-" + sesion.getId());
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getLivekitRoomId())
                .isEqualTo("sesion-" + sesion.getId());
    }

    @Test
    void tM303_crearSalaDiferida_reservaCerradaAntesDeT5_noCreaSala() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        reserva.setEstado(EstadoReserva.NO_SHOW_DOBLE);
        reservaRepository.save(reserva);

        sesionService.crearSalaDiferida(sesion.getId());

        verify(liveKitService, never()).crearSala(anyString());
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getLivekitRoomId())
                .isNull();
    }

    // ------------------------------------------------ T-M3-04: no-show a T+10

    @Test
    void tM304_noShowDoble_niUnoSeUne() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);

        EstadoReserva estado = sesionService.ejecutarNoShow(sesion.getId());

        assertThat(estado).isEqualTo(EstadoReserva.NO_SHOW_DOBLE);
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.NO_SHOW_DOBLE);
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada_anticipada");
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getDuracionEfectivaSegundos())
                .isZero();

        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionNoShowDobleEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.no_show_doble");
    }

    @Test
    void tM304_noShowEstudiante_soloSeUneElTutor() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        sesion.setTutorJoinedAt(Instant.now());
        sesionRepository.save(sesion);

        EstadoReserva estado = sesionService.ejecutarNoShow(sesion.getId());

        assertThat(estado).isEqualTo(EstadoReserva.NO_SHOW_ESTUDIANTE);
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionNoShowEstudianteEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.no_show_estudiante");
    }

    @Test
    void tM304_noShowTutor_soloSeUneElEstudiante() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        sesion.setEstudianteJoinedAt(Instant.now());
        sesionRepository.save(sesion);

        EstadoReserva estado = sesionService.ejecutarNoShow(sesion.getId());

        assertThat(estado).isEqualTo(EstadoReserva.NO_SHOW_TUTOR);
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionNoShowTutorEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.no_show_tutor");
    }

    @Test
    void tM304_ambosSeUnenAntesDeT10_seCancelaElJobYNoAplicaNoShow() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        // El webhook resuelve la sesión por livekitRoomId: la fija antes de los joins.
        String sala = "sesion-" + sesion.getId();
        sesion.setLivekitRoomId(sala);
        sesionRepository.save(sesion);

        joinWebhook(sala, e.tutorId());
        joinWebhook(sala, e.menorId());

        SesionAprendizaje trasJoins = sesionRepository.findById(sesion.getId()).orElseThrow();
        assertThat(trasJoins.getEstado()).isEqualTo("en_curso");
        assertThat(trasJoins.getInicioReal()).isNotNull();
        assertThat(trasJoins.getTutorJoinedAt()).isNotNull();
        assertThat(trasJoins.getEstudianteJoinedAt()).isNotNull();
        // El job T+10 fue removido del scheduler (Plan M3 §3.2 punto 4).
        assertThat(existeTrigger("no-show", sesion.getId())).isFalse();

        // Aunque el job disparara de todos modos, es idempotente: no aplica no-show.
        assertThat(sesionService.ejecutarNoShow(sesion.getId()))
                .isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(EVENTOS).isEmpty();
    }

    // ------------------------------------------------ T-M3-05: finalización

    @Test
    void tM305_finalizar_tutorYBeneficiario_cierranYEmiteUnSoloEvento() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        String sala = "sesion-" + sesion.getId();
        sesion.setLivekitRoomId(sala);
        sesionRepository.save(sesion);
        // El tutor entró 10 segundos antes del final → duración efectiva > 0.
        // (inicio_real lo marca el primer join del webhook; acá se simula directo.)
        sesion.setTutorJoinedAt(Instant.now().minusSeconds(10));
        sesion.setInicioReal(Instant.now().minusSeconds(10));
        sesionRepository.save(sesion);
        Instant previo = Instant.now();
        sesionService.finalizar(usuarioRepository.findByDni(e.dniTutor()).orElseThrow(),
                sesion.getId());

        SesionAprendizaje cerrada = sesionRepository.findById(sesion.getId()).orElseThrow();
        assertThat(cerrada.getEstado()).isEqualTo("finalizada");
        assertThat(cerrada.getFinReal()).isAfterOrEqualTo(previo);
        assertThat(cerrada.getDuracionEfectivaSegundos()).isPositive();
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);

        assertThat(EVENTOS).hasSize(1);
        SesionFinalizadaEvent evento = (SesionFinalizadaEvent) EVENTOS.get(0);
        assertThat(evento.getNombre()).isEqualTo("sesion.finalizada");
        assertThat(evento.getReservaId()).isEqualTo(reserva.getId());
        // el evento lleva el Instant in-memory (nanos); la columna TIMESTAMP(6) al
        // persistir redondea a micros. Se compara a milisegundos, estable ante el
        // redondeo de la BD.
        assertThat(evento.getTimestampFin().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(cerrada.getFinReal().truncatedTo(ChronoUnit.MILLIS));

        // Idempotente: repetir (botón o job que dispara después) no re-emite.
        sesionService.ejecutarCorteAutomatico(sesion.getId());
        sesionService.ejecutarNoShow(sesion.getId());
        assertThat(EVENTOS).hasSize(1);
    }

    @Test
    void tM305_finalizarEndPoint_participanteConJwt_200() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);

        // El menor (beneficiario, US-8 "cualquiera de las dos partes") finaliza.
        mockMvc.perform(post("/api/sesiones/{id}/finalizar", sesion.getId())
                        .header("Authorization", "Bearer " + e.tokenMenor()))
                .andExpect(status().isOk());
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada");
    }

    @Test
    void tM305_finalizarEndPoint_terceroNoParticipante_403() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        String tokenTercero = registrarAdultoYToken(dniUnico(), "Pepe", "Garcia");

        mockMvc.perform(post("/api/sesiones/{id}/finalizar", sesion.getId())
                        .header("Authorization", "Bearer " + tokenTercero))
                .andExpect(status().isForbidden());
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("no_iniciada");
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void tM305_corteAutomatico_sinQueNadiePresioneFinalizar() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);

        sesionService.ejecutarCorteAutomatico(sesion.getId());

        // Nadie se unió (inicioReal null → duración efectiva 0 < 50% de la
        // agendada de 60min): con la regla de US-5/FR-AULA-005 (T-M3-10) el
        // corte automático emite sesion.interrumpida, no finalizada.
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("interrumpida");
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getDuracionEfectivaSegundos())
                .isZero(); // nadie se unió → sin duración efectiva
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionInterrumpidaEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.interrumpida");
        // Idempotente: otro disparo del corte no re-emite ni re-marca.
        sesionService.ejecutarCorteAutomatico(sesion.getId());
        assertThat(EVENTOS).hasSize(1);
    }

    @Test
    void tM305_corteAutomatico_sesionInexistente_noRompe() throws Exception {
        sesionService.ejecutarCorteAutomatico(UUID.randomUUID());
        assertThat(EVENTOS).isEmpty();
    }

    // ------------------------------------------------ token de acceso (frontend M3)

    @Test
    void tM3Token_participanteConSalaCreada_obtieneTokenYUrl() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        sesion.setLivekitRoomId("sesion-" + sesion.getId());
        sesionRepository.save(sesion);

        MvcResult res = mockMvc.perform(post("/api/sesiones/{id}/token", sesion.getId())
                        .header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("token").asText()).isEqualTo("jwt-test-token");
        assertThat(body.get("livekitUrl").asText()).isEqualTo("wss://test.livekit.cloud");
        assertThat(body.get("livekitRoomId").asText()).isEqualTo("sesion-" + sesion.getId());
        // AUD-003: la identity de LiveKit es el UUID, nunca el DNI — LiveKit la difunde al
        // otro participante (con menores, dato sensible bajo Ley 25.326).
        // El claim name lleva solo el nombre de pila (minimización, Art. V): sin apellido.
        verify(liveKitService).generarTokenParticipante(
                e.tutorId().toString(), "Pablo", "sesion-" + sesion.getId());
        verify(liveKitService, never())
                .generarTokenParticipante(eq(e.dniTutor()), anyString(), anyString());
    }

    @Test
    void tM3Token_terceroNoParticipante_403() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);
        sesion.setLivekitRoomId("sesion-" + sesion.getId());
        sesionRepository.save(sesion);
        String tokenTercero = registrarAdultoYToken(dniUnico(), "Pepe", "Garcia");

        mockMvc.perform(post("/api/sesiones/{id}/token", sesion.getId())
                        .header("Authorization", "Bearer " + tokenTercero))
                .andExpect(status().isForbidden());
        verify(liveKitService, never()).generarTokenParticipante(anyString(), anyString(), anyString());
    }

    @Test
    void tM3Token_salonAunNoCreado_422() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);

        mockMvc.perform(post("/api/sesiones/{id}/token", sesion.getId())
                        .header("Authorization", "Bearer " + e.tokenTutor()))
                .andExpect(status().isUnprocessableEntity());
        verify(liveKitService, never()).generarTokenParticipante(anyString(), anyString(), anyString());
    }

    // ------------------------------------------------ GET /sesiones/por-reserva (frontend M4/M3)

    @Test
    void tM3PorReserva_participante_devuelveLaSesion() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        SesionAprendizaje sesion = programarYCargar(reserva);

        MvcResult res = mockMvc.perform(get("/api/sesiones/por-reserva/{reservaId}", reserva.getId())
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isOk())
                .andReturn();

        var body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("id").asText()).isEqualTo(sesion.getId().toString());
        assertThat(body.get("reservaId").asText()).isEqualTo(reserva.getId().toString());
        assertThat(body.get("estado").asText()).isEqualTo("no_iniciada");
    }

    @Test
    void tM3PorReserva_terceroNoParticipante_403() throws Exception {
        Escenario e = escenarioBase();
        Reserva reserva = reservaConfirmadaDirecta(e);
        programarYCargar(reserva);
        String tokenTercero = registrarAdultoYToken(dniUnico(), "Pepe", "Garcia");

        mockMvc.perform(get("/api/sesiones/por-reserva/{reservaId}", reserva.getId())
                        .header("Authorization", "Bearer " + tokenTercero))
                .andExpect(status().isForbidden());
    }

    @Test
    void tM3PorReserva_reservaSinSesionTodavia_404() throws Exception {
        // Una Reserva que nunca se confirmó (pendiente_pago) no tiene Sesión
        // programada — el 404 tiene que distinguirse de una Reserva inexistente
        // solo en el mensaje, nunca en el código (ambos casos son "no hay nada
        // que mostrar todavía", ninguno es un error del cliente).
        Escenario e = escenarioBase();
        Reserva reserva = new Reserva();
        reserva.setPagador(usuarioRepository.findByDni(e.dniAr()).orElseThrow());
        reserva.setBeneficiario(usuarioRepository.findByDni(e.dniMenor()).orElseThrow());
        reserva.setTutor(usuarioRepository.findByDni(e.dniTutor()).orElseThrow());
        reserva.setHorario(e.horario());
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reservaRepository.save(reserva);

        mockMvc.perform(get("/api/sesiones/por-reserva/{reservaId}", reserva.getId())
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isNotFound());
    }

    @Test
    void tM3PorReserva_reservaInexistente_404() throws Exception {
        Escenario e = escenarioBase();

        mockMvc.perform(get("/api/sesiones/por-reserva/{reservaId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + e.tokenAr()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------ helper

    private UUID crearSolicitud(Escenario e, Instant horario) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/solicitudes")
                        .header("Authorization", "Bearer " + e.tokenMenor())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", e.tutorId().toString(),
                                "horarioPropuesto", horario.toString()))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(
                res.getResponse().getContentAsString()).get("id").asText());
    }
}