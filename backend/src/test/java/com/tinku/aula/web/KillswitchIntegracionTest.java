package com.tinku.aula.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.CierreSalaService;
import com.tinku.aula.LiveKitService;
import com.tinku.aula.SesionService;
import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.aula.model.ConfirmacionKillswitch;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.ConfirmacionKillswitchRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.evento.SesionEvento;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.evento.SesionKillswitchAdultosEvent;
import com.tinku.pagos.evento.SesionKillswitchMenorEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.seguridad.model.DecisionAlerta;
import com.tinku.seguridad.service.AlertaSeguridadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.quartz.Scheduler;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chunk M3-E (T-M3-07/08/09/10/11): kill-switch de punta a punta.
 *
 * (T-M3-11) La rama la decide SIEMPRE el backend con datos de M1 — el request
 * NUNCA la acepta: un body manipulador tipo {@code {"rama":"adultos", ...}} en
 * una sesión con un menor se ignora y el sistema ejecuta la rama menor (corte
 * directo + suspensión preventiva del Tutor + {@code sesion.killswitch_menor}).
 *
 * Los datos se arman por repositorio (mismo patrón que
 * LiveKitWebhookIntegracionTest) y los tokens se acuñan con {@link JwtUtil}
 * sobre el DNI de usuarios reales; los listener de evento capturan los
 * {@code sesion.*} que M5 consume.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class KillswitchIntegracionTest {

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
    @Autowired AlertaSeguridadRepository alertaRepository;
    @Autowired ConfirmacionKillswitchRepository confirmacionRepository;
    @Autowired SesionService sesionService;
    @Autowired JwtUtil jwtUtil;
    @Autowired CierreSalaService cierreSalaService;
    @Autowired AlertaSeguridadService alertaSeguridadService;
    @Autowired Scheduler scheduler;
    // Sin credenciales de LiveKit en CI (T-000-06): se mockea el borde HTTP.
    @MockBean LiveKitService liveKitService;
    // FASE2-03: espía del outbox para forzar su falla (el corte no puede depender del aviso).
    @org.springframework.boot.test.mock.mockito.SpyBean com.tinku.admin.notificacion.NotificadorOutbox notificador;
    @Autowired com.tinku.admin.notificacion.NotificacionRepository notificacionRepository;

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
    void limpiar() {
        EVENTOS.clear();
        reset(liveKitService);
    }

    // ------------------------------------------------ helpers de datos

    private String dniUnico() {
        return String.format("%08d", 30_700_000 + CONTADOR_DNIS.incrementAndGet());
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String dni) {
        return guardarUsuario(tipo, dni, null);
    }

    /** Un MENOR nunca se crea a sí mismo: exige su Adulto Responsable (FR-ID-020). */
    private Usuario guardarUsuario(TipoUsuario tipo, String dni, Usuario adultoResponsable) {
        Usuario u = new Usuario();
        u.setDni(dni);
        u.setNombre("Nombre");
        u.setApellido("Apellido");
        u.setFechaNacimiento(tipo == TipoUsuario.MENOR
                ? LocalDate.of(2015, 7, 20)
                : LocalDate.of(1990, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.MENOR) {
            u.setAdultoResponsable(adultoResponsable);
        } else if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        } else if (tipo == TipoUsuario.TUTOR) {
            u.setActivoParaMatching(true); // así la suspensión preventiva se ve
        }
        return usuarioRepository.save(u);
    }

    private String tokenDe(Usuario u) {
        return jwtUtil.generateToken(u);
    }

    /** Sesión CONFIRMADA con beneficiario = {@code usuario} (adulto o menor). */
    private Reserva reservaConfirmada(Usuario pagador, Usuario beneficiario, Usuario tutor) {
        Reserva r = new Reserva();
        r.setPagador(pagador);
        r.setBeneficiario(beneficiario);
        r.setTutor(tutor);
        r.setHorario(Instant.now().plusSeconds(3600));
        r.setPrecio(BigDecimal.valueOf(15000));
        r.setEstado(EstadoReserva.CONFIRMADA);
        return reservaRepository.save(r);
    }

    /** Sesión creada directo (sin jobs de Quartz: no hacen falta acá), con la
     *  duración agendada fijada — la misma que fija {@code programarSesion}. */
    private SesionAprendizaje sesionDirecta(Reserva reserva, int duracionAgendadaSegundos) {
        SesionAprendizaje s = new SesionAprendizaje();
        s.setReservaId(reserva.getId());
        s.setEstado(SesionAprendizaje.ESTADO_NO_INICIADA);
        s.setDuracionAgendadaSegundos(duracionAgendadaSegundos);
        return sesionRepository.save(s);
    }

    private Usuario tutorConId(UUID tutorId) {
        return usuarioRepository.findById(tutorId).orElseThrow();
    }

    // ------------------------------------------------ k switch HTTP helpers

    private void postKillswitch(UUID sesionId, String token, Map<String, Object> body)
            throws Exception {
        mockMvc.perform(post("/api/sesiones/{id}/killswitch", sesionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful());
    }

    // ------------------------------------------------ FASE2-03 (AUD-014, D2-bis): aviso al AR

    @Test
    void killswitchMenor_notificaAlAdultoResponsable_sinDatosDelContenido() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        postKillswitch(sesion.getId(), tokenDe(ar), Map.of("detectadoId", tutor.getId().toString()));

        List<com.tinku.admin.notificacion.Notificacion> avisos = notificacionRepository.findByDestinatarioId(ar.getId());
        assertThat(avisos).hasSize(1);
        com.tinku.admin.notificacion.Notificacion aviso = avisos.get(0);
        assertThat(aviso.getTipo()).isEqualTo(com.tinku.shared.notificacion.TipoNotificacion.KILLSWITCH_MENOR);
        // D2-bis: solo qué sesión y cuándo; nada del Tutor ni de lo detectado.
        assertThat(aviso.getDatos()).containsOnlyKeys("sesionId", "fecha");
        assertThat(aviso.getDatos().get("sesionId")).isEqualTo(sesion.getId().toString());
        assertThat(aviso.getDatos().values()).noneMatch(v -> v.contains(tutor.getId().toString())
                || v.contains(tutor.getNombre()) || v.contains(tutor.getApellido()));
        // Ni el tutor ni el menor reciben este aviso.
        assertThat(notificacionRepository.findByDestinatarioId(tutor.getId())).isEmpty();
        assertThat(notificacionRepository.findByDestinatarioId(menor.getId())).isEmpty();
    }

    @Test
    void killswitchMenor_siFallaLaNotificacion_elCorteIgualSePersiste() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        org.mockito.Mockito.doThrow(new IllegalStateException("outbox caído"))
                .when(notificador).notificar(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        postKillswitch(sesion.getId(), tokenDe(ar), Map.of("detectadoId", tutor.getId().toString()));

        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado()).isEqualTo("finalizada");
        assertThat(alertaRepository.findBySesionId(sesion.getId())).isPresent();
        assertThat(tutorConId(tutor.getId()).isActivoParaMatching()).isFalse();
    }

    // ------------------------------------------------ T-M3-11 — la rama la decide el backend

    @Test
    void tM311_menor_killswitchForzandoRamaAdultos_ejecutaRamaMenor() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        // El cliente intenta forzar la rama "adultos" — el backend la ignora:
        // el body solo declara detectadoId y la decisión sale de M1 (menor → rama menor).
        postKillswitch(sesion.getId(), tokenDe(ar),
                Map.of("rama", "adultos", "detectadoId", tutor.getId().toString()));

        SesionAprendizaje cerrada = sesionRepository.findById(sesion.getId()).orElseThrow();
        assertThat(cerrada.getEstado()).isEqualTo("finalizada");
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);

        // Sin confirmación (la rama menor corta directo, Artículo II).
        assertThat(confirmacionRepository.findBySesionId(sesion.getId())).isNotPresent();

        AlertaSeguridad alerta = alertaRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(alerta.getRama()).isEqualTo("menor");
        assertThat(alerta.getDetectadoId()).isEqualTo(tutor.getId());
        assertThat(alerta.getEstado()).isEqualTo("pendiente_revision");

        assertThat(tutorConId(tutor.getId()).isActivoParaMatching()).isFalse();

        // Evento exacto hacia M5, una sola vez.
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionKillswitchMenorEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.killswitch_menor");
        assertThat(((SesionKillswitchMenorEvent) EVENTOS.get(0)).getDetectadoId())
                .isEqualTo(tutor.getId());

        // La menor sesión no admite confirmación: 422.
        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(ar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", true))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(EVENTOS).hasSize(1); // nada nuevo
    }

    @Test
    void tM311_menor_dobleKillswitch_noReemiteNiReCorta() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        postKillswitch(sesion.getId(), tokenDe(ar),
                Map.of("detectadoId", tutor.getId().toString()));
        assertThat(EVENTOS).hasSize(1);
        postKillswitch(sesion.getId(), tokenDe(ar),
                Map.of("detectadoId", tutor.getId().toString()));

        assertThat(EVENTOS).hasSize(1);
        assertThat(alertaRepository.findBySesionId(sesion.getId())).isPresent();
    }

    // ------------------------------------------------ AUD-006 — se suspende al detectado

    @Test
    void aud006_menor_detectadoEsElMenor_noSuspendeAlTutorYLaResolucionRevierte() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        menor.setActivoParaMatching(true);
        usuarioRepository.save(menor);
        SesionAprendizaje sesion = sesionDirecta(reservaConfirmada(ar, menor, tutor), 3600);

        postKillswitch(sesion.getId(), tokenDe(ar), Map.of("detectadoId", menor.getId().toString()));

        // El corte es incondicional (Art. II) — eso no cambia.
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada");
        // D2: se suspende solo al detectado. El Tutor no generó la detección.
        assertThat(tutorConId(tutor.getId()).isActivoParaMatching()).isTrue();
        assertThat(usuarioRepository.findById(menor.getId()).orElseThrow().isActivoParaMatching())
                .isFalse();

        // La Alerta apunta a quien fue suspendido: resolver REACTIVAR lo revierte.
        AlertaSeguridad alerta = alertaRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(alerta.getDetectadoId()).isEqualTo(menor.getId());
        alertaSeguridadService.resolver(alerta.getId(), UUID.randomUUID(),
                DecisionAlerta.REACTIVAR, null, null);
        assertThat(usuarioRepository.findById(menor.getId()).orElseThrow().isActivoParaMatching())
                .isTrue();
        assertThat(tutorConId(tutor.getId()).isActivoParaMatching()).isTrue();
    }

    // ------------------------------------------------ AUD-001 — el corte cierra la sala

    /** Rama menor con la sala ya creada (T-5 alcanzado) — el caso real de un corte en vivo. */
    private SesionAprendizaje sesionMenorConSala(Usuario ar, Usuario tutor) {
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        SesionAprendizaje sesion = sesionDirecta(reservaConfirmada(ar, menor, tutor), 3600);
        sesion.setLivekitRoomId("sesion-" + sesion.getId());
        return sesionRepository.save(sesion);
    }

    @Test
    void aud001_menor_killswitch_cierraLaSalaDeLiveKit() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        SesionAprendizaje sesion = sesionMenorConSala(ar, tutor);

        postKillswitch(sesion.getId(), tokenDe(ar), Map.of("detectadoId", tutor.getId().toString()));

        verify(liveKitService).eliminarSala("sesion-" + sesion.getId());
        assertThat(scheduler.checkExists(CierreSalaService.triggerCierre(sesion.getId()))).isFalse();
    }

    @Test
    void aud001_menor_trasKillswitch_tokenResponde422() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        SesionAprendizaje sesion = sesionMenorConSala(ar, tutor);

        postKillswitch(sesion.getId(), tokenDe(ar), Map.of("detectadoId", tutor.getId().toString()));

        mockMvc.perform(post("/api/sesiones/{id}/token", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(tutor)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void aud001_menor_liveKitCaido_elCorteSePersisteIgualYSeAgendaReintento() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        SesionAprendizaje sesion = sesionMenorConSala(ar, tutor);
        doThrow(new IllegalStateException("LiveKit no responde"))
                .when(liveKitService).eliminarSala(anyString());

        postKillswitch(sesion.getId(), tokenDe(ar), Map.of("detectadoId", tutor.getId().toString()));

        // D4 / ADR-M3-03: un fallo de LiveKit NO revierte el corte en la base.
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada");
        assertThat(alertaRepository.findBySesionId(sesion.getId())).isPresent();
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionKillswitchMenorEvent.class);
        assertThat(scheduler.checkExists(CierreSalaService.triggerCierre(sesion.getId()))).isTrue();
    }

    @Test
    void aud001_reintentoDeCierre_exitoQuitaElJobYAgotadoNoReprograma() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        SesionAprendizaje sesion = sesionMenorConSala(ar, tutor);
        UUID id = sesion.getId();

        doThrow(new IllegalStateException("LiveKit no responde"))
                .when(liveKitService).eliminarSala(anyString());
        cierreSalaService.ejecutarCierre(id, 1);
        assertThat(scheduler.checkExists(CierreSalaService.triggerCierre(id))).isTrue();

        // 3° reintento fallido: se agotan los reintentos, no se reprograma.
        cierreSalaService.ejecutarCierre(id, 3);
        assertThat(scheduler.checkExists(CierreSalaService.triggerCierre(id))).isFalse();

        reset(liveKitService);
        cierreSalaService.ejecutarCierre(id, 1);
        cierreSalaService.ejecutarCierre(id, 2);
        verify(liveKitService, org.mockito.Mockito.times(2)).eliminarSala("sesion-" + id);
        assertThat(scheduler.checkExists(CierreSalaService.triggerCierre(id))).isFalse();
    }

    // ------------------------------------------------ T-M3-07 — rama adultos: no corta

    @Test
    void tM307_dosAdultos_killswitchRegistraConfirmacionEsperandoYNoCorta() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        postKillswitch(sesion.getId(), tokenDe(estudiante),
                Map.of("detectadoId", tutor.getId().toString()));

        // NO corta: la sesión y la reserva siguen igual, sin evento todavía.
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("no_iniciada");
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(alertaRepository.findBySesionId(sesion.getId())).isNotPresent();
        assertThat(EVENTOS).isEmpty();

        ConfirmacionKillswitch conf =
                confirmacionRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(conf.getDetectadoId()).isEqualTo(tutor.getId());
        assertThat(conf.getRespondidoId()).isNull();
        assertThat(conf.getVio()).isNull();
    }

    @Test
    void tM307_killswitch_terceroNoParticipante_403() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        Usuario tercero = guardarUsuario(TipoUsuario.ADULTO, dniUnico());

        mockMvc.perform(post("/api/sesiones/{id}/killswitch", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(tercero))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("detectadoId", tutor.getId().toString()))))
                .andExpect(status().isForbidden());
        assertThat(EVENTOS).isEmpty();
    }

    @Test
    void tM307_killswitch_detectadoFueraDeLaSesion_422() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        mockMvc.perform(post("/api/sesiones/{id}/killswitch", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("detectadoId", UUID.randomUUID().toString()))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(EVENTOS).isEmpty();
    }

    // ------------------------------------------------ T-M3-09 — confirmación rama adultos

    @Test
    void tM309_dosAdultos_confirmacionSi_cortaBloqueaDetectadoYEmiteEvento() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        postKillswitch(sesion.getId(), tokenDe(estudiante),
                Map.of("detectadoId", tutor.getId().toString()));

        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", true))))
                .andExpect(status().isOk());

        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada");
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);
        assertThat(tutorConId(tutor.getId()).isActivoParaMatching()).isFalse();

        AlertaSeguridad alerta = alertaRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(alerta.getRama()).isEqualTo("adultos");
        assertThat(alerta.getDetectadoId()).isEqualTo(tutor.getId());

        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionKillswitchAdultosEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.killswitch_adultos");
        assertThat(((SesionKillswitchAdultosEvent) EVENTOS.get(0)).getDetectadoId())
                .isEqualTo(tutor.getId());
    }

    @Test
    void tM309_dosAdultos_confirmacionNo_noCorta() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        postKillswitch(sesion.getId(), tokenDe(estudiante),
                Map.of("detectadoId", tutor.getId().toString()));

        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", false))))
                .andExpect(status().isOk());

        // La sesión continúa; la respuesta queda registrada (log interno).
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("no_iniciada");
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(alertaRepository.findBySesionId(sesion.getId())).isNotPresent();
        assertThat(EVENTOS).isEmpty();
        ConfirmacionKillswitch conf =
                confirmacionRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(conf.getVio()).isFalse();
        assertThat(conf.getRespondidoId()).isEqualTo(estudiante.getId());
    }

    @Test
    void tM309_dosAdultos_falsoPositivoResuelto_segundoDisparoRealVuelveAGenerarConfirmacion()
            throws Exception {
        // Auditoría 2026-09-18: regresión del bug donde ejecutarKillswitch
        // quedaba inutilizado para el resto de la sesión tras un primer
        // disparo resuelto como falso positivo (vio=false) — Spec_M3 US-7
        // exige que la sesión "continúe el monitoreo normal".
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        // Primer disparo: falso positivo, resuelto con "No".
        postKillswitch(sesion.getId(), tokenDe(estudiante),
                Map.of("detectadoId", tutor.getId().toString()));
        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", false))))
                .andExpect(status().isOk());
        assertThat(EVENTOS).isEmpty();

        // Segundo disparo, más tarde en la misma sesión: debe volver a
        // registrar una confirmación pendiente, no ser ignorado.
        postKillswitch(sesion.getId(), tokenDe(estudiante),
                Map.of("detectadoId", tutor.getId().toString()));
        ConfirmacionKillswitch conf =
                confirmacionRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(conf.getRespondidoId()).isNull();
        assertThat(conf.getVio()).isNull();

        // Y esa segunda confirmación pendiente sí puede resolverse con corte real.
        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", true))))
                .andExpect(status().isOk());
        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada");
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionKillswitchAdultosEvent.class);
    }

    @Test
    void tM309_elDetectadoNoPuedeConfirmarseASiMismo_422() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        postKillswitch(sesion.getId(), tokenDe(estudiante),
                Map.of("detectadoId", tutor.getId().toString()));
        assertThat(EVENTOS).isEmpty();

        // US-7: responde el OTRO participante — el detectado no se confirma solo.
        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", true))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(EVENTOS).isEmpty();
    }

    @Test
    void tM309_confirmacionSinKillswitchPrevioso_422() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        mockMvc.perform(post("/api/sesiones/{id}/killswitch/confirmacion", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("vio", true))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------ T-M3-08 — evidencia

    @Test
    void tM308_evidencia_sinKillswitchRegistrado_404() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);

        mockMvc.perform(post("/api/sesiones/{id}/evidencia", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("clipUrl", "https://cdn.tinku.test/clip.mp4"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void tM308_evidencia_trasKillswitch_grabaclipEnLaAlerta() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        postKillswitch(sesion.getId(), tokenDe(ar),
                Map.of("detectadoId", tutor.getId().toString()));

        mockMvc.perform(post("/api/sesiones/{id}/evidencia", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(ar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clipUrl", "https://cdn.tinku.test/clip-x.mp4",
                                "duracionSegundos", 30))))
                .andExpect(status().isOk());

        AlertaSeguridad alerta = alertaRepository.findBySesionId(sesion.getId()).orElseThrow();
        assertThat(alerta.getClipUrl()).isEqualTo("https://cdn.tinku.test/clip-x.mp4");

        // El buffer es rotativo de 30s: un clip más largo se rechaza (422).
        mockMvc.perform(post("/api/sesiones/{id}/evidencia", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(ar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "clipUrl", "https://cdn.tinku.test/clip-largo.mp4",
                                "duracionSegundos", 45))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tM308_evidencia_urlNoHttp_422() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        postKillswitch(sesion.getId(), tokenDe(ar),
                Map.of("detectadoId", tutor.getId().toString()));

        mockMvc.perform(post("/api/sesiones/{id}/evidencia", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(ar))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("clipUrl", "no-es-url"))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void tM308_evidencia_terceroNoParticipante_403() throws Exception {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Usuario menor = guardarUsuario(TipoUsuario.MENOR, dniUnico(), ar);
        Reserva reserva = reservaConfirmada(ar, menor, tutor);
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        postKillswitch(sesion.getId(), tokenDe(ar),
                Map.of("detectadoId", tutor.getId().toString()));
        Usuario tercero = guardarUsuario(TipoUsuario.ADULTO, dniUnico());

        mockMvc.perform(post("/api/sesiones/{id}/evidencia", sesion.getId())
                        .header("Authorization", "Bearer " + tokenDe(tercero))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("clipUrl", "https://cdn.tinku.test/clip.mp4"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------ T-M3-10 — sesion.interrumpida (US-5)

    @Test
    void tM310_corteAutomatico_antesDel50_emiteSesionInterrumpida() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        // Sesión de 60 min agendados; la clase arrancó hace ~16 min → 27% < 50%.
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        sesion.setEstado(SesionAprendizaje.ESTADO_EN_CURSO);
        sesion.setInicioReal(Instant.now().minusSeconds(1000));
        sesionRepository.save(sesion);

        sesionService.ejecutarCorteAutomatico(sesion.getId());

        SesionAprendizaje interrumpida = sesionRepository.findById(sesion.getId()).orElseThrow();
        assertThat(interrumpida.getEstado()).isEqualTo("interrumpida");
        assertThat(reservaRepository.findById(reserva.getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.FINALIZADA);

        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionInterrumpidaEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.interrumpida");

        // Idempotente: repetir el corte no re-emite.
        sesionService.ejecutarCorteAutomatico(sesion.getId());
        assertThat(EVENTOS).hasSize(1);
    }

    @Test
    void tM310_corteAutomatico_despuesDel50_emiteSesionFinalizada() throws Exception {
        Usuario pagador = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario estudiante = guardarUsuario(TipoUsuario.ADULTO, dniUnico());
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, dniUnico());
        Reserva reserva = reservaConfirmada(pagador, estudiante, tutor);
        // La clase arrancó hace ~34 min de 60 agendados → 57% ≥ 50%: cierre normal.
        SesionAprendizaje sesion = sesionDirecta(reserva, 3600);
        sesion.setEstado(SesionAprendizaje.ESTADO_EN_CURSO);
        sesion.setInicioReal(Instant.now().minusSeconds(2000));
        sesionRepository.save(sesion);

        sesionService.ejecutarCorteAutomatico(sesion.getId());

        assertThat(sesionRepository.findById(sesion.getId()).orElseThrow().getEstado())
                .isEqualTo("finalizada");
        assertThat(EVENTOS).hasSize(1);
        assertThat(EVENTOS.get(0)).isInstanceOf(SesionFinalizadaEvent.class);
        assertThat(EVENTOS.get(0).getNombre()).isEqualTo("sesion.finalizada");
    }
}