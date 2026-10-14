package com.tinku.aula.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.tinku.aula.LiveKitService;
import com.tinku.aula.SesionService;
import com.tinku.aula.model.ConfirmacionKillswitch;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.ConfirmacionKillswitchRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M3C-02 (T-M3-06b, ADR-M3-07): lo que el backend le debe al kill-switch del cliente —
 * {@code hayMenor}, la puerta del token (FR-AULA-010 / US-6bis), el registro de eventos
 * del clasificador, el estado de la confirmación y el plazo de la rama adultos (§7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class KillswitchClienteIntegracionTest {

    @Container
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"))
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
    @Autowired AlertaSeguridadRepository alertaRepository;
    @Autowired ConfirmacionKillswitchRepository confirmacionRepository;
    @Autowired SesionService sesionService;
    @Autowired JwtUtil jwtUtil;
    @Autowired Scheduler scheduler;
    @MockitoBean LiveKitService liveKitService;

    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    @BeforeEach
    void limpiar() {
        reset(liveKitService);
        when(liveKitService.generarTokenParticipante(anyString(), anyString(), anyString()))
                .thenReturn("jwt-test-token");
        when(liveKitService.getBaseUrl()).thenReturn("wss://test.livekit.cloud");
    }

    // ------------------------------------------------ helpers

    private Usuario guardarUsuario(TipoUsuario tipo, Usuario adultoResponsable) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 31_700_000 + CONTADOR_DNIS.incrementAndGet()));
        u.setNombre("Nombre");
        u.setApellido("Apellido");
        u.setFechaNacimiento(tipo == TipoUsuario.MENOR ? LocalDate.of(2015, 7, 20) : LocalDate.of(1990, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.MENOR) {
            u.setAdultoResponsable(adultoResponsable);
        } else if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        } else if (tipo == TipoUsuario.TUTOR) {
            u.setActivoParaMatching(true);
        }
        return usuarioRepository.save(u);
    }

    private String tokenDe(Usuario u) {
        return jwtUtil.generateToken(u);
    }

    private SesionAprendizaje sesion(Usuario pagador, Usuario beneficiario, Usuario tutor) {
        Reserva r = new Reserva();
        r.setPagador(pagador);
        r.setBeneficiario(beneficiario);
        r.setTutor(tutor);
        r.setHorario(Instant.now().plusSeconds(3600));
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(EstadoReserva.CONFIRMADA);
        r = reservaRepository.save(r);
        SesionAprendizaje s = new SesionAprendizaje();
        s.setReservaId(r.getId());
        s.setEstado(SesionAprendizaje.ESTADO_NO_INICIADA);
        s.setDuracionAgendadaSegundos(3600);
        s = sesionRepository.save(s);
        s.setLivekitRoomId("sesion-" + s.getId());
        return sesionRepository.save(s);
    }

    /** Clase con un Menor: AR (pagador), menor (beneficiario), tutor. */
    private record Escenario(Usuario ar, Usuario menor, Usuario tutor, SesionAprendizaje sesion) {}

    private Escenario conMenor() {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, null);
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, null);
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, ar);
        return new Escenario(ar, menor, tutor, sesion(ar, menor, tutor));
    }

    /** Clase entre adultos: pagador = estudiante (adulto), tutor. */
    private Escenario entreAdultos() {
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, null);
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, null);
        return new Escenario(estudiante, estudiante, tutor, sesion(estudiante, estudiante, tutor));
    }

    private void dispararAdultos(Escenario e) throws Exception {
        mockMvc.perform(post("/api/sesiones/{id}/killswitch", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(e.ar()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("detectadoId", e.tutor().getId().toString()))))
                .andExpect(status().isOk());
    }

    private TriggerKey triggerVencimiento(UUID sesionId) {
        return new TriggerKey("confirmacion-ks-trigger-" + sesionId, SesionService.GRUPO_JOB);
    }

    // ------------------------------------------------ puerta del token (FR-AULA-010, US-6bis)

    @Test
    void token_conMenor_sinClasificadorListo_409() throws Exception {
        Escenario e = conMenor();
        mockMvc.perform(post("/api/sesiones/{id}/token", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(e.tutor())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CLASIFICADOR_NO_LISTO"))
                .andExpect(jsonPath("$.token").doesNotExist());
        org.mockito.Mockito.verify(liveKitService, org.mockito.Mockito.never())
                .generarTokenParticipante(anyString(), anyString(), anyString());
    }

    @Test
    void token_conMenor_conClasificadorListo_200_hayMenorTrue() throws Exception {
        Escenario e = conMenor();
        mockMvc.perform(post("/api/sesiones/{id}/token", e.sesion().getId())
                        .param("clasificadorListo", "true")
                        .header("Authorization", "Bearer " + tokenDe(e.tutor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayMenor").value(true));
    }

    @Test
    void token_sinMenor_sinClasificadorListo_200_hayMenorFalse() throws Exception {
        Escenario e = entreAdultos();
        mockMvc.perform(post("/api/sesiones/{id}/token", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(e.tutor())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayMenor").value(false));
    }

    @Test
    void token_tercero_conMenor_403() throws Exception {
        Escenario e = conMenor();
        Usuario tercero = guardarUsuario(TipoUsuario.ADULTO, null);
        mockMvc.perform(post("/api/sesiones/{id}/token", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(tercero)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ eventos del clasificador (§6)

    private org.springframework.test.web.servlet.ResultActions postEvento(UUID sesionId, Usuario u, String json)
            throws Exception {
        return mockMvc.perform(post("/api/sesiones/{id}/clasificador", sesionId)
                .header("Authorization", "Bearer " + tokenDe(u))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void eventoClasificador_participante_204() throws Exception {
        Escenario e = conMenor();
        postEvento(e.sesion().getId(), e.tutor(), "{\"evento\":\"CAIDA\"}").andExpect(status().isNoContent());
    }

    @Test
    void eventoClasificador_tercero_403() throws Exception {
        Escenario e = conMenor();
        Usuario tercero = guardarUsuario(TipoUsuario.ADULTO, null);
        postEvento(e.sesion().getId(), tercero, "{\"evento\":\"CAIDA\"}").andExpect(status().isForbidden());
    }

    @Test
    void eventoClasificador_eventoInvalido_400() throws Exception {
        Escenario e = conMenor();
        // TRANS-1: si el handler global traduce los errores de validación a 403, el código real
        // queda documentado acá (ver M3C-10 H-3).
        postEvento(e.sesion().getId(), e.tutor(), "{\"evento\":\"OTRO\"}").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------ estado del kill-switch

    private JsonNode estado(Escenario e, Usuario quien, int http) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/sesiones/{id}/killswitch", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(quien)))
                .andExpect(status().is(http)).andReturn();
        return http == 200 ? objectMapper.readTree(r.getResponse().getContentAsString()) : null;
    }

    @Test
    void estadoKillswitch_sinConfirmacion() throws Exception {
        Escenario e = entreAdultos();
        JsonNode body = estado(e, e.tutor(), 200);
        assertThat(body.get("hayMenor").asBoolean()).isFalse();
        assertThat(body.get("confirmacionPendiente").asBoolean()).isFalse();
        assertThat(body.get("detectadoId") == null || body.get("detectadoId").isNull()).isTrue();
    }

    @Test
    void estadoKillswitch_conConfirmacionPendiente() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);
        JsonNode body = estado(e, e.ar(), 200);
        assertThat(body.get("confirmacionPendiente").asBoolean()).isTrue();
        assertThat(body.get("detectadoId").asText()).isEqualTo(e.tutor().getId().toString());
        Instant vence = Instant.parse(body.get("venceAt").asText());
        assertThat(Duration.between(Instant.now(), vence)).isBetween(Duration.ofMinutes(4), Duration.ofMinutes(5).plusSeconds(5));
    }

    @Test
    void estadoKillswitch_tercero_403() throws Exception {
        Escenario e = entreAdultos();
        estado(e, guardarUsuario(TipoUsuario.ADULTO, null), 403);
    }

    // ------------------------------------------------ plazo de la rama adultos (§7)

    @Test
    void ramaAdultos_programaVencimiento() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);
        assertThat(scheduler.checkExists(triggerVencimiento(e.sesion().getId()))).isTrue();
    }

    @Test
    void vencerConfirmacion_sinRespuesta_quedaVencidaComoNo() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);

        sesionService.vencerConfirmacionRamaAdultos(e.sesion().getId());

        ConfirmacionKillswitch c = confirmacionRepository.findBySesionId(e.sesion().getId()).orElseThrow();
        assertThat(c.isVencida()).isTrue();
        assertThat(c.getVio()).isFalse();
        assertThat(c.getRespondedAt()).isNotNull();
        assertThat(sesionRepository.findById(e.sesion().getId()).orElseThrow().getEstado())
                .isNotEqualTo(SesionAprendizaje.ESTADO_FINALIZADA);
        assertThat(alertaRepository.findBySesionId(e.sesion().getId())).isEmpty();
    }

    @Test
    void vencerConfirmacion_yaRespondida_noHaceNada() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);
        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(e.ar()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", false))))
                .andExpect(status().isOk());

        sesionService.vencerConfirmacionRamaAdultos(e.sesion().getId());

        ConfirmacionKillswitch c = confirmacionRepository.findBySesionId(e.sesion().getId()).orElseThrow();
        assertThat(c.isVencida()).isFalse();
        assertThat(c.getRespondidoId()).isEqualTo(e.ar().getId());
    }

    @Test
    void confirmar_trasVencimiento_422() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);
        sesionService.vencerConfirmacionRamaAdultos(e.sesion().getId());

        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(e.ar()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", true))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void disparo_trasConfirmacionVencida_creaConfirmacionNueva() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);
        Instant primerVence = confirmacionRepository.findBySesionId(e.sesion().getId()).orElseThrow().getVenceAt();
        sesionService.vencerConfirmacionRamaAdultos(e.sesion().getId());

        dispararAdultos(e);

        ConfirmacionKillswitch c = confirmacionRepository.findBySesionId(e.sesion().getId()).orElseThrow();
        assertThat(c.getRespondidoId()).isNull();
        assertThat(c.isVencida()).isFalse();
        assertThat(c.getVenceAt()).isAfterOrEqualTo(primerVence);
        assertThat(scheduler.checkExists(triggerVencimiento(e.sesion().getId()))).isTrue();
    }

    @Test
    void confirmar_cancelaElTriggerDeVencimiento() throws Exception {
        Escenario e = entreAdultos();
        dispararAdultos(e);
        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", e.sesion().getId())
                        .header("Authorization", "Bearer " + tokenDe(e.ar()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", false))))
                .andExpect(status().isOk());
        assertThat(scheduler.checkExists(triggerVencimiento(e.sesion().getId()))).isFalse();
    }
}
