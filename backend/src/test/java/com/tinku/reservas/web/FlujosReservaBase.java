package com.tinku.reservas.web;

import tools.jackson.databind.ObjectMapper;
import com.tinku.identidad.dto.AutorizarTutorRequest;
import com.tinku.identidad.dto.RegistroAdultoRequest;
import com.tinku.identidad.dto.RegistroMenorRequest;
import com.tinku.identidad.dto.RegistroTutorRequest;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.ocr.OcrService;
import com.tinku.identidad.ocr.ResultadoOcr;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.matching.port.MatchingServiceClient;
import com.tinku.matching.port.ReputacionSignalProvider;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.port.ReputacionBloqueoProveedor;
import com.tinku.reservas.service.ReservaService;
import com.tinku.reservas.service.ReservasZonaHoraria;
import com.tinku.shared.notificacion.TipoNotificacion;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Andamiaje común de los tests de integración de la enmienda v2.5 (pedido previo, nota, pedido de
 * reprogramación, paquete). Cada subclase declara su propio contenedor de Postgres y su
 * {@code @DynamicPropertySource}; acá quedan los beans, los mocks y los helpers.
 */
abstract class FlujosReservaBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaService reservaService;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.tinku.admin.notificacion.NotificacionRepository notificaciones;

    @MockitoBean OcrService ocrService;
    @MockitoBean MatchingServiceClient matchingClient;
    @MockitoBean ReputacionSignalProvider reputacion;
    @MockitoBean ReputacionBloqueoProveedor reputacionBloqueo;

    static final String PASSWORD = "password123";
    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    /** Rango distinto por subclase para no chocar DNIs en la base compartida por el contexto. */
    abstract int baseDnis();

    String dniUnico() {
        return String.format("%08d", baseDnis() + CONTADOR_DNIS.incrementAndGet());
    }

    @BeforeEach
    void programarMocksBase() {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, "10000000", "Juan", "Perez", LocalDate.of(1990, 5, 15)));
        when(reputacion.senalesImplicitas(anyCollection())).thenReturn(Map.of());
        when(reputacion.tutoresEnSombraBrMatch01(anyCollection())).thenReturn(Set.of());
        when(reputacionBloqueo.tutoresConCalificacionPendiente()).thenReturn(Set.of());
    }

    List<TipoNotificacion> avisosDe(UUID usuarioId) {
        return notificaciones.findByDestinatarioId(usuarioId).stream()
                .map(com.tinku.admin.notificacion.Notificacion::getTipo).toList();
    }

    String registrarAdulto(String dni, String nombre, boolean capEst, boolean capAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dni, nombre, "Lopez", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/usuarios/registro")
                        .file(jsonPart("datos", new RegistroAdultoRequest(dni, nombre, "Lopez",
                                LocalDate.of(1990, 5, 15), dni + "@tinku.test", PASSWORD, capEst, capAr)))
                        .file(fotoDni()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    String registrarTutor(String dni, String nombre) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dni, nombre, "Sosa", LocalDate.of(1990, 5, 15)));
        mockMvc.perform(multipart("/api/tutores/registro")
                        .file(jsonPart("datos", new RegistroTutorRequest(dni, nombre, "Sosa",
                                LocalDate.of(1990, 5, 15), dni + "@tinku.test", PASSWORD)))
                        .file(fotoDni()))
                .andExpect(status().isCreated());
        return login(dni);
    }

    UUID registrarMenor(String dniMenor, String tokenAr) throws Exception {
        when(ocrService.procesarDocumento(any(), any()))
                .thenReturn(new ResultadoOcr(true, dniMenor, "Sofia", "Perez", LocalDate.of(2015, 7, 20)));
        mockMvc.perform(multipart("/api/usuarios/menores")
                        .file(jsonPart("datos", new RegistroMenorRequest(dniMenor, "Sofia", "Perez",
                                LocalDate.of(2015, 7, 20), PASSWORD, true, "v1")))
                        .file(fotoDni())
                        .header("Authorization", "Bearer " + tokenAr))
                .andExpect(status().isCreated());
        return usuarioPorDni(dniMenor).getId();
    }

    void autorizar(UUID tutorId, UUID menorId, String tokenAr) throws Exception {
        com.tinku.testsupport.CapVigente.para(jdbc, tutorId);
        mockMvc.perform(post("/api/autorizaciones")
                        .header("Authorization", "Bearer " + tokenAr)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AutorizarTutorRequest(menorId, tutorId))))
                .andExpect(status().isCreated());
    }

    void publicarFranja(String tokenTutor, LocalDate fecha, String desde, String hasta) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fechaEspecifica", fecha.toString(), "horaInicio", desde, "horaFin", hasta))))
                .andExpect(status().isCreated());
    }

    void publicarFranjaSemanal(String tokenTutor, LocalDate unDia, String desde, String hasta) throws Exception {
        mockMvc.perform(post("/api/tutores/franjas")
                        .header("Authorization", "Bearer " + tokenTutor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "diaSemana", unDia.getDayOfWeek().getValue() % 7, "horaInicio", desde, "horaFin", hasta))))
                .andExpect(status().isCreated());
    }

    Instant a(LocalDate dia, int hora, int minuto) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, minuto), ReservasZonaHoraria.ZONA).toInstant();
    }

    LocalDate enDias(int dias) {
        return LocalDate.now(ReservasZonaHoraria.ZONA).plusDays(dias);
    }

    UUID reservar(String token, UUID tutorId, UUID beneficiarioId, Instant horario, int minutos) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/reservas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "tutorId", tutorId.toString(),
                                "beneficiarioId", beneficiarioId == null ? "" : beneficiarioId.toString(),
                                "horario", horario.toString(),
                                "duracionMinutos", minutos))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText());
    }

    void confirmarPago(UUID reservaId) {
        Reserva confirmada = reservaService.confirmarPagoSimulado(reservaId);
        assertThat(confirmada.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    Usuario usuarioPorDni(String dni) {
        return usuarioRepository.findByDni(dni).orElseThrow();
    }

    String login(String dni) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/usuarios/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new com.tinku.identidad.dto.LoginRequest(dni, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    MockMultipartFile jsonPart(String name, Object dto) throws Exception {
        return new MockMultipartFile(name, name, MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(dto));
    }

    static MockMultipartFile fotoDni() {
        return new MockMultipartFile("fotoDni", "dni.png", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[]{1, 2, 3});
    }

    /** Un adulto que se reserva a sí mismo con un Tutor (franja pasado mañana 15:00-16:00). */
    record Adulto(String tokenAlumno, UUID alumnoId, String tokenTutor, UUID tutorId, LocalDate fecha) {
    }

    Adulto adultoConTutor() throws Exception {
        String dniAlumno = dniUnico();
        String dniTutor = dniUnico();
        String tokenAlumno = registrarAdulto(dniAlumno, "Lucas", true, false);
        String tokenTutor = registrarTutor(dniTutor, "Pablo");
        LocalDate fecha = enDias(2);
        publicarFranja(tokenTutor, fecha, "15:00", "16:00");
        return new Adulto(tokenAlumno, usuarioPorDni(dniAlumno).getId(), tokenTutor, usuarioPorDni(dniTutor).getId(), fecha);
    }

    /** Un AR con un Menor autorizado para el Tutor (franja pasado mañana 15:00-16:00). */
    record ConMenor(String tokenAr, UUID arId, String tokenMenor, UUID menorId, String tokenTutor, UUID tutorId,
                    LocalDate fecha) {
    }

    ConMenor arConMenor() throws Exception {
        String dniAr = dniUnico();
        String dniTutor = dniUnico();
        String dniMenor = dniUnico();
        String tokenAr = registrarAdulto(dniAr, "Ana", true, true);
        String tokenTutor = registrarTutor(dniTutor, "Pablo");
        UUID tutorId = usuarioPorDni(dniTutor).getId();
        UUID menorId = registrarMenor(dniMenor, tokenAr);
        autorizar(tutorId, menorId, tokenAr);
        LocalDate fecha = enDias(2);
        publicarFranja(tokenTutor, fecha, "15:00", "16:00");
        return new ConMenor(tokenAr, usuarioPorDni(dniAr).getId(), login(dniMenor), menorId, tokenTutor, tutorId, fecha);
    }
}
