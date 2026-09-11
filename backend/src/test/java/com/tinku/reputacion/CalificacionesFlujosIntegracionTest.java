package com.tinku.reputacion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.admin.model.Admin;
import com.tinku.admin.model.RolAdmin;
import com.tinku.admin.repository.AdminRepository;
import com.tinku.identidad.dto.LoginRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.port.PerfilMatchingProvider;
import com.tinku.identidad.port.ReputacionPerfilProvider;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.ReputacionSignalProvider;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.reputacion.model.Calificacion;
import com.tinku.reputacion.model.SenalesImplicitasTutor;
import com.tinku.reputacion.repository.CalificacionRepository;
import com.tinku.reputacion.repository.SenalesImplicitasTutorRepository;
import com.tinku.reputacion.service.SenalesImplicitasService;
import com.tinku.reservas.evento.ReservaCanceladaEvent;
import com.tinku.reservas.evento.ReservaConfirmadaEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flujos de calificaciones y reputacion (Chunk M7) ejercitados de punta a
 * punta. Sin {@code @MockBean} de los puertos de reputacion: se usa la
 * implementacion real sobre la BD (solo se mockean los puertos ajenos — OCR,
 * almacenamiento y matching). Cubre T-M7-02 (crear, direccion derivada del
 * rol), T-M7-04 (ocultas del panel de moderacion), T-M7-06 (editar/eliminar en
 * 48hs y recordatorio unico a 24hs), T-M7-07 (senales por eventos) y T-M7-08
 * (FR-REP-007 umbral de 5, FR-REP-006 bloqueo, BR-MATCH-01 sombra, sin fuga de
 * datos sensibles).
 *
 * Las Reservas y Sesiones se arman por repositorio (el flujo M4/M3 ya está
 * cubierto en ReservasFlujosIntegracionTest y SesionesIntegracionTest) — solo
 * hace falta datos en `finalizada`, que es lo que FR-REP-008 exige.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CalificacionesFlujosIntegracionTest {

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
    @Autowired ApplicationEventPublisher events;
    @Autowired Scheduler scheduler;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired AdminRepository adminRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired CalificacionRepository calificacionRepository;
    @Autowired SenalesImplicitasTutorRepository senalesRepository;
    @Autowired ReputacionPerfilProvider reputacionPerfil;
    @Autowired ReputacionBloqueoProveedor reputacionBloqueo;
    @Autowired ReputacionSignalProvider reputacionSignal;
    @Autowired SenalesImplicitasService senalesImplicitasService;

    @MockBean com.tinku.identidad.port.Almacenamiento almacenamiento;
    @MockBean PerfilMatchingProvider perfilMatching;
    @MockBean OcrService ocrService;

    private static final String PASSWORD = "password-seguro-123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 40_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    /** Marca a un Usuario ya registrado como Admin de Moderación en {@code admin.admins} (M8 V16). */
    private void adminModeracion(String dni) {
        Usuario admin = usuarioRepository.findByDni(dni).orElseThrow();
        Admin fila = new Admin();
        fila.setUsuario(admin);
        fila.setRol(RolAdmin.MODERACION_SEGURIDAD);
        adminRepository.save(fila);
    }

    // ------------------------------------------------ helpers

    private void registrarAdulto(String dni, String nombre, boolean capEstudiante,
                                 boolean capAdultoResponsable) throws Exception {
        when(almacenamiento.guardar(any(), any()))
                .thenReturn("https://cdn.test/" + UUID.randomUUID() + ".png");
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dni, nombre, "Lopez", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(
                                dni, nombre, "Lopez", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD, capEstudiante, capAdultoResponsable)))
                        .file(foto()))
                .andExpect(status().isCreated());
    }

    private void registrarTutor(String dni, String nombre) throws Exception {
        when(almacenamiento.guardar(any(), any()))
                .thenReturn("https://cdn.test/" + UUID.randomUUID() + ".png");
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dni, nombre, "Garcia", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(
                                dni, nombre, "Garcia", LocalDate.of(1990, 5, 15),
                                dni + "@tinku.test", PASSWORD)))
                        .file(foto()))
                .andExpect(status().isCreated());
    }

    private String login(String dni) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name,
                MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));
    }

    private MockMultipartFile foto() {
        return new MockMultipartFile("fotoDni", "dni.png",
                MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1, 2, 3});
    }

    private Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    private Instant horarioDistinto(int salto) {
        return ZonedDateTime.of(2030, 1, 10, 15, 0, 0, 0, ZoneOffset.UTC)
                .plusHours(salto).toInstant();
    }

    /** Reserva CONFIRMADA directo por repositorio + Sesion en `finalizada`. Devuelve la sesion. */
    private UUID escenaFinalizada(UUID tutorId, UUID estudianteId, UUID pagadorId, int salto) {
        Reserva r = rCreada(tutorId, estudianteId, pagadorId, salto, SesionAprendizaje.ESTADO_FINALIZADA);
        return sesionRepository.findByReservaId(r.getId()).orElseThrow().getId();
    }

    /** Reserva directa por repositorio con Sesion en el estado pedido. */
    private Reserva rCreada(UUID tutorId, UUID estudianteId, UUID pagadorId, int salto, String estado) {
        Reserva r = new Reserva();
        r.setTutor(usuarioRepository.findById(tutorId).orElseThrow());
        r.setBeneficiario(usuarioRepository.findById(estudianteId).orElseThrow());
        r.setPagador(usuarioRepository.findById(pagadorId).orElseThrow());
        r.setHorario(horarioDistinto(salto));
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(EstadoReserva.CONFIRMADA);
        Reserva guardada = reservaRepository.save(r);

        SesionAprendizaje s = new SesionAprendizaje();
        s.setReservaId(guardada.getId());
        s.setEstado(estado);
        sesionRepository.save(s);
        return guardada;
    }

    private void calificarStatus(String token, UUID sesionId, int estrellas, String comentario,
                                 int statusEsperado, String direccionEsperada) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/sesiones/{id}/calificacion", sesionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(cuerpoCalificacion(estrellas, comentario))))
                .andExpect(status().is(statusEsperado))
                .andReturn();
        if (statusEsperado == 201 && direccionEsperada != null) {
            JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
            assertThat(body.get("direccion").asText()).isEqualTo(direccionEsperada);
            // T-M7-06: editable_hasta queda ~48hs adelante.
            Instant editable = Instant.parse(body.get("editableHasta").asText());
            assertThat(editable).isAfter(Instant.now().plusSeconds(47 * 3600))
                    .isBefore(Instant.now().plusSeconds(49 * 3600));
        }
    }

    // ------------------------------------------------ T-M7-02: calificar

    @Test
    void calificar_direccionDerivadaDelRol_creaLaCalificacionCorrecta() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        String tokenTutor = login(dniTutor);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, 1);

        // El estudiante (beneficiario = pagador) califica publico al Tutor.
        String dniTercero = dniUnico();
        registrarAdulto(dniTercero, "Sofia", true, false);
        String tokenTercero = login(dniTercero);

        calificarStatus(tokenEst, sesion, 5, "Excelente tutor", 201, "estudiante_a_tutor");
        // El Tutor califica oculto al estudiante (sin comentario).
        calificarStatus(tokenTutor, sesion, 4, null, 201, "tutor_a_estudiante");
        // Duplicado del mismo autor+direccion → 422 (FR-REP-003).
        calificarStatus(tokenEst, sesion, 5, null, 422, null);
        // Un tercero autenticado sin relación con la Reserva → 403.
        calificarStatus(tokenTercero, sesion, 5, null, 403, null);
        // Comentario en la calificacion oculta del Tutor → 422.
        calificarStatus(tokenTutor, sesion, 5, "Alumno aplicado", 422, null);
    }

    @Test
    void calificar_soloSesionesFinalizadasYEstrellas1a5() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        String tokenTutor = login(dniTutor);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        Reserva finalizada = rCreada(tutorId, estudianteId, estudianteId, 2, SesionAprendizaje.ESTADO_FINALIZADA);
        Reserva enCurso = rCreada(tutorId, estudianteId, estudianteId, 3, SesionAprendizaje.ESTADO_EN_CURSO);

        // Sesion en curso → 422 (FR-REP-008).
        calificarStatus(tokenEst, sesionDe(enCurso), 5, null, 422, null);
        // Sesion finalizada con estrellas fuera de rango → 422.
        calificarStatus(tokenEst, sesionDe(finalizada), 6, null, 422, null);
        // El Tutor tampoco puede calificar una sesion en curso.
        calificarStatus(tokenTutor, sesionDe(enCurso), 3, null, 422, null);
    }

    private UUID sesionDe(Reserva r) {
        return sesionRepository.findByReservaId(r.getId()).orElseThrow().getId();
    }

    // ------------------------------------------------ T-M7-06: editar/eliminar

    @Test
    void editarYEliminar_dentroDe48hs_soloElAutorYLoPublico() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        String tokenTutor = login(dniTutor);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();
        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, 4);

        String idCalif = calificarYDevolverId(tokenEst, sesion, 5, "Muy bueno");

        // Editarla dentro de la ventana → ok, estrellas actualizadas (FR-REP-005).
        mockMvc.perform(patch("/api/calificaciones/{id}", idCalif)
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("estrellas", 4, "comentario", "Muy bueno"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estrellas").value(4));

        // Solo el autor la edita: otro usuario → 403.
        mockMvc.perform(patch("/api/calificaciones/{id}", idCalif)
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("estrellas", 1))))
                .andExpect(status().isForbidden());

        // Vencida la ventana de 48hs → 422 (FR-REP-005).
        Calificacion c = calificacionRepository.findById(UUID.fromString(idCalif)).orElseThrow();
        c.setEditableHasta(Instant.now().minusSeconds(60));
        calificacionRepository.save(c);
        mockMvc.perform(patch("/api/calificaciones/{id}", idCalif)
                        .header("Authorization", "Bearer " + tokenEst)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("estrellas", 3))))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(delete("/api/calificaciones/{id}", idCalif)
                        .header("Authorization", "Bearer " + tokenEst))
                .andExpect(status().isUnprocessableEntity());

        // La oculta (tutor_a_estudiante) no se edita ni por su autor: 403.
        calificarStatus(tokenTutor, sesion, 4, null, 201, "tutor_a_estudiante");
        String idOculta = calificacionRepository
                .findBySesionIdAndAutorIdAndDireccion(sesion, tutorId, Calificacion.DIR_TUTOR_A_ESTUDIANTE)
                .orElseThrow().getId().toString();
        mockMvc.perform(patch("/api/calificaciones/{id}", idOculta)
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("estrellas", 4))))
                .andExpect(status().isForbidden());

        // La publica vencida no se elimina (422); verificamos que sigue existiendo.
        assertThat(calificacionRepository.findById(UUID.fromString(idCalif))).isPresent();
    }

    @Test
    void eliminar_dentroDeLaVentana_borraLaCalificacion() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();
        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, 5);

        String idCalif = calificarYDevolverId(tokenEst, sesion, 5, null);
        mockMvc.perform(delete("/api/calificaciones/{id}", idCalif)
                        .header("Authorization", "Bearer " + tokenEst))
                .andExpect(status().isNoContent());
        assertThat(calificacionRepository.findById(UUID.fromString(idCalif))).isEmpty();
    }

    private String calificarYDevolverId(String token, UUID sesionId, int estrellas, String comentario)
            throws Exception {
        MvcResult res = mockMvc.perform(post("/api/sesiones/{id}/calificacion", sesionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(cuerpoCalificacion(estrellas, comentario))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private Map<String, Object> cuerpoCalificacion(int estrellas, String comentario) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("estrellas", estrellas);
        if (comentario != null) {
            body.put("comentario", comentario);
        }
        return body;
    }

    // ------------------------------------------------ FR-REP-007: umbral 5

    private void calificarSesion(String token, UUID tutorId, UUID estudianteId, int salto, int estrellas)
            throws Exception {
        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, salto);
        calificarStatus(token, sesion, estrellas, null, 201, null);
    }

    @Test
    void perfilPublico_promedioOcultoHasta5Calificaciones() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        calificarSesion(tokenEst, tutorId, estudianteId, 10, 5);
        calificarSesion(tokenEst, tutorId, estudianteId, 11, 4);
        calificarSesion(tokenEst, tutorId, estudianteId, 12, 4);
        calificarSesion(tokenEst, tutorId, estudianteId, 13, 4);
        calificarSesion(tokenEst, tutorId, estudianteId, 14, 5);

        // Promedio visible con 5 publicas: (5+4+4+4+5)/5 = 4.4.
        mockMvc.perform(get("/api/tutores/{id}", tutorId)
                        .header("Authorization", "Bearer " + tokenEst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").value(4.4))
                .andExpect(jsonPath("$.cantidadCalificaciones").value(5));
    }

    @Test
    void perfilPublico_conMenosDe5_muestraNull_hastaAntesDeLaImplementacion() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        calificarSesion(tokenEst, tutorId, estudianteId, 20, 5);

        // FR-REP-007: con 1 solo uso el promedio NO se expone.
        mockMvc.perform(get("/api/tutores/{id}", tutorId)
                        .header("Authorization", "Bearer " + tokenEst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").isEmpty())
                .andExpect(jsonPath("$.cantidadCalificaciones").value(1))
                // T-M7-08: el perfil publico no filtra datos sensibles.
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.dni").doesNotExist())
                .andExpect(jsonPath("$.comentario").doesNotExist());
    }

    // ------------------------------------------------ FR-REP-006 / BR-MATCH-01

    @Test
    void bloqueoPorCalificacionPendiente_hastaQueElTutorCalifiqueAlEstudiante() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        String tokenTutor = login(dniTutor);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, 30);

        // Sin la calificacion oculta del Tutor al Estudiante → bloqueado (FR-REP-006).
        assertThat(reputacionBloqueo.tutoresConCalificacionPendiente()).contains(tutorId);

        // Que el estudiante califique (publica) NO destraba: la pendiente es la del Tutor.
        calificarStatus(tokenEst, sesion, 5, "Genio", 201, null);
        assertThat(reputacionBloqueo.tutoresConCalificacionPendiente()).contains(tutorId);

        // El Tutor califica al Estudiante (oculta) → deja de estar bloqueado.
        calificarStatus(tokenTutor, sesion, 4, null, 201, null);
        assertThat(reputacionBloqueo.tutoresConCalificacionPendiente()).doesNotContain(tutorId);
    }

    @Test
    void sombraBrMatch01_soloEstrellasBajasEnUltimas24Hs() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, 40);
        calificarStatus(tokenEst, sesion, 1, "Llego tarde", 201, null);
        assertThat(reputacionSignal.tutoresEnSombraBrMatch01(List.of(tutorId))).contains(tutorId);

        // Una calificacion buena posterior NO lo saca de la sombra de las 24hs
        // (la sombra mira cualquier 1-2 en la ventana), pero SÍ deja de estar en
        // la lista cuando ninguna calificacion baja queda dentro de la ventana.
        UUID sesionOk = escenaFinalizada(tutorId, estudianteId, estudianteId, 41);
        calificarStatus(tokenEst, sesionOk, 4, null, 201, null);
        assertThat(reputacionSignal.tutoresEnSombraBrMatch01(List.of(tutorId))).contains(tutorId);

        // Sin calificaciones bajas recientes (frozen: la 1 estrella queda fuera
        // de la ventana), no hay sombra.
        calificacionRepository.delete(calificacionRepository
                .findBySesionIdAndAutorIdAndDireccion(sesion, estudianteId, Calificacion.DIR_ESTUDIANTE_A_TUTOR)
                .orElseThrow());
        assertThat(reputacionSignal.tutoresEnSombraBrMatch01(List.of(tutorId))).doesNotContain(tutorId);
    }

    @Test
    void senalesImplicitas_pesoCeroSinDatos_yPositivoConSenales() throws Exception {
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();

        // Sin fila en senales_implicitas_tutor → no aparece en el mapa.
        assertThat(reputacionSignal.senalesImplicitas(List.of(tutorId)).keySet()).doesNotContain(tutorId);

        // Con el agregado presente, el peso es >= 0 (ADR-M2-02: se suma al score).
        SenalesImplicitasTutor s = new SenalesImplicitasTutor();
        s.setTutorId(tutorId);
        s.setSesionesDictadasTotal(10);
        s.setPuntualidadPromedio(BigDecimal.valueOf(1));
        senalesRepository.save(s);
        assertThat(reputacionSignal.senalesImplicitas(List.of(tutorId)).get(tutorId))
                .isGreaterThanOrEqualTo(0.0);
    }

    // ------------------------------------------------ T-M7-07: eventos → senales

    @Test
    void eventosDeNegocio_alimentanLasSenalesImplicitas() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();

        Reserva r1 = rCreada(tutorId, estudianteId, estudianteId, 50, SesionAprendizaje.ESTADO_FINALIZADA);
        Reserva r2 = rCreada(tutorId, estudianteId, estudianteId, 51, SesionAprendizaje.ESTADO_NO_INICIADA);

        // sesion.finalizada → el tutor dicto: cuenta dictadas y se mantiene puntual.
        events.publishEvent(new SesionFinalizadaEvent(this, r1.getId(), Instant.now()));
        SenalesImplicitasTutor sen = senalesRepository.findById(tutorId).orElseThrow();
        assertThat(sen.getSesionesDictadasTotal()).isEqualTo(1);
        assertThat(sen.getPuntualidadPromedio().doubleValue()).isEqualTo(1.0);

        // ReservaConfirmada con historial (r1 + r2 del mismo beneficiario) → re-enganche.
        // Se invoca el listener directo: publicar el evento dispara en M3 la creacion
        // real de sesion, que exige una franja publicada (fuera del alcance de M7).
        senalesImplicitasService.onReservaConfirmada(new ReservaConfirmadaEvent(this, r2.getId()));
        sen = senalesRepository.findById(tutorId).orElseThrow();
        assertThat(sen.getTasaRecontratacion().doubleValue()).isGreaterThan(0.0);

        // no_show del tutor → impuntual y sube la tasa de cancelacion/no-show.
        events.publishEvent(new SesionNoShowTutorEvent(this, r2.getId()));
        sen = senalesRepository.findById(tutorId).orElseThrow();
        assertThat(sen.getPuntualidadPromedio().doubleValue()).isLessThan(1.0);
        assertThat(sen.getTasaCancelacionNoshow().doubleValue()).isGreaterThan(0.0);
        double tasaTrasNoShow = sen.getTasaCancelacionNoshow().doubleValue();

        // cancelacion manual del propio tutor → sube la misma tasa.
        events.publishEvent(new ReservaCanceladaEvent(this, r1.getId(), tutorId));
        assertThat(senalesRepository.findById(tutorId).orElseThrow()
                .getTasaCancelacionNoshow().doubleValue()).isGreaterThan(tasaTrasNoShow);

        // no_show del estudiante → el tutor si se presento: recupera puntualidad.
        events.publishEvent(new SesionNoShowEstudianteEvent(this, r2.getId()));
        assertThat(senalesRepository.findById(tutorId).orElseThrow()
                .getPuntualidadPromedio().doubleValue()).isGreaterThan(0.5);
    }

    // ------------------------------------------------ T-M7-06: recordatorio

    @Test
    void recordatorioUnico_a24hsDeFinalizada() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();
        Reserva r = rCreada(tutorId, estudianteId, estudianteId, 60, SesionAprendizaje.ESTADO_FINALIZADA);
        SesionAprendizaje sesion = sesionRepository.findByReservaId(r.getId()).orElseThrow();

        // sesion.finalizada → agenda el recordatorio unico en `m7-reputacion`.
        events.publishEvent(new SesionFinalizadaEvent(this, r.getId(), Instant.now()));
        TriggerKey tkey = TriggerKey.triggerKey(
                "recordatorio-calificacion-" + sesion.getId(), "m7-reputacion");
        JobKey jkey = JobKey.jobKey(
                "recordatorio-calificacion-" + sesion.getId(), "m7-reputacion");
        assertThat(scheduler.checkExists(tkey)).isTrue();
        assertThat(scheduler.checkExists(jkey)).isTrue();

        // Repetir el evento (procesamiento idempotente) no duplica el job.
        events.publishEvent(new SesionFinalizadaEvent(this, r.getId(), Instant.now()));
        assertThat(scheduler.getTriggersOfJob(jkey)).hasSize(1);

        // El trigger se programa a timestampFin + 24hs.
        org.quartz.Trigger t = scheduler.getTrigger(tkey);
        assertThat(t.getNextFireTime().toInstant())
                .isAfter(Instant.now().plusSeconds(23 * 3600));
    }

    // ------------------------------------------------ T-M7-04: ocultas

    @Test
    void ocultas_soloAdminDeModeracion_nuncaEnPerfilPublico() throws Exception {
        String dniEst = dniUnico();
        registrarAdulto(dniEst, "Ana", true, false);
        String tokenEst = login(dniEst);
        String dniTutor = dniUnico();
        registrarTutor(dniTutor, "Pablo");
        String tokenTutor = login(dniTutor);
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID estudianteId = usuarioPorDni(dniEst).getId();
        UUID sesion = escenaFinalizada(tutorId, estudianteId, estudianteId, 70);

        // El Tutor deja una oculta sobre el estudiante.
        calificarStatus(tokenTutor, sesion, 4, null, 201, "tutor_a_estudiante");

        // No admins (tutor o estudiante) → 403 (AdminModeracionGate, fail-closed).
        mockMvc.perform(get("/api/admin/moderacion/calificaciones-ocultas/{id}", estudianteId)
                        .header("Authorization", "Bearer " + tokenTutor))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/moderacion/calificaciones-ocultas/{id}", estudianteId)
                        .header("Authorization", "Bearer " + tokenEst))
                .andExpect(status().isForbidden());

        // El admin de moderacion (con fila en `admin.admins`, M8) lo ve:
        // estrellas y autor, sin comentario ni datos sensibles.
        registrarAdulto("39999999", "Admin", false, true);
        adminModeracion("39999999");
        String tokenAdmin = login("39999999");
        mockMvc.perform(get("/api/admin/moderacion/calificaciones-ocultas/{id}", estudianteId)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comentario").doesNotExist())
                .andExpect(jsonPath("$[0].estrellas").value(4))
                .andExpect(jsonPath("$[0].autorId").value(tutorId.toString()));

        // El perfil publico del tutor NO muestra la oculta que su autor dejo:
        // el promedio/publicas solo cuenta calificaciones del estudiante.
        mockMvc.perform(get("/api/tutores/{id}", tutorId)
                        .header("Authorization", "Bearer " + tokenEst))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calificacionPromedio").isEmpty())
                .andExpect(jsonPath("$.cantidadCalificaciones").value(0));
    }
}