package com.tinku.seguridad;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.AlertaSeguridadRepository;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import com.tinku.config.security.JwtUtil;
import com.tinku.identidad.model.EstadoCuenta;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.pagos.service.LiberacionEscrowService;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.MotivoCancelacion;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.aula.model.AlertaSeguridad;
import com.tinku.seguridad.model.Denuncia;
import com.tinku.seguridad.model.EstadoDenuncia;
import com.tinku.seguridad.model.OrigenSancion;
import com.tinku.seguridad.model.Sancion;
import com.tinku.seguridad.model.TipoSancion;
import com.tinku.seguridad.repository.DenunciaRepository;
import com.tinku.seguridad.repository.SancionRepository;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-M9-07 — Historias de Usuario del Spec_M9 de punta a punta (HTTP):
 * presentación de Denuncia con rechazo del menor (Artículo II), track de
 * descargo + SLA, resolución por el Admin de Moderación (gate
 * {@code tinku.admin.moderacion.ids}) y propagación de la sanción a M1/M2/M4/M5.
 *
 * Escenario estrella (FR-SEC-011): denuncias CRUZADAS entre las mismas dos
 * cuentas — cada caso pausa y luego libera SOLO el escrow de su propia sesión,
 * jamás el del otro (independencia por reserva, no por par).
 *
 * Los usuarios/libros/escrows se siembran directo en BD (patrón
 * {@code LiberacionDenunciaPausaIntegracionTest}); el auth real pasa por el
 * filter JWT contra {@code UsuarioDetailsService} (cuentas inactivas = 401).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DenunciasModeracionIntegracionTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
                    .withDatabaseName("tinku_test");

    /** DNI fijos del Admin de Moderación y Seguridad — el allowlist es fail-closed
     * y se setea acá (contexto) porque en {code application-test.yml} no existe.
     * Por rango (42.001.000 + i) para que cada test use el suyo sin colisión UNIQUE. */
    static final String DNI_ADMIN_BASE = "4200";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("tinku.admin.moderacion.ids", () -> String.join(",",
                java.util.stream.IntStream.rangeClosed(1, 20)
                        // 42_001_000 + i → "42001001"…"42001020"
                        .mapToObj(i -> DNI_ADMIN_BASE + String.format("%04d", 1000 + i))
                        .toList()));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtUtil jwtUtil;
    @Autowired Scheduler scheduler;

    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired SesionAprendizajeRepository sesionRepository;
    @Autowired DenunciaRepository denunciaRepository;
    @Autowired SancionRepository sancionRepository;
    @Autowired AlertaSeguridadRepository alertaRepository;

    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;
    @MockBean AlertaSoporteProveedor alertaSoporte;

    private static final AtomicInteger CONTADOR = new AtomicInteger();
    private static final AtomicInteger ADMIN_COUNTER = new AtomicInteger();

    // ------------------------------------------------------------- helpers

    private Usuario admin() {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 42_001_000 + ADMIN_COUNTER.incrementAndGet()));
        u.setNombre("Admin" + ADMIN_COUNTER.get());
        u.setApellido("Seguridad");
        u.setFechaNacimiento(LocalDate.of(1985, 1, 2));
        u.setTipo(TipoUsuario.ADULTO);
        u.setPasswordHash("hash");
        u.setCapacidadEstudiante(true);
        u.setCapacidadAdultoResponsable(true);
        return usuarioRepository.save(u);
    }

    private Usuario usuario(TipoUsuario tipo, boolean activoParaMatching) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 42_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre("Nombre" + CONTADOR.get());
        u.setApellido("Lopez");
        u.setFechaNacimiento(LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        u.setActivoParaMatching(activoParaMatching);
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    private Usuario menor(Usuario adultoResponsable) {
        Usuario m = new Usuario();
        m.setDni(String.format("%08d", 30_000_000 + CONTADOR.incrementAndGet()));
        m.setNombre("Menor" + CONTADOR.get());
        m.setApellido("Gomez");
        m.setFechaNacimiento(LocalDate.of(2012, 3, 10));
        m.setTipo(TipoUsuario.MENOR);
        m.setPasswordHash("hash");
        m.setAdultoResponsable(adultoResponsable);
        return usuarioRepository.save(m);
    }

    private String token(Usuario u) {
        return jwtUtil.generateToken(u.getDni(), u.getTipo().name(), true, true);
    }

    private record Cupo(UUID reservaId, UUID sesionId, UUID transaccionId) {
    }

    private Cupo cupoConEscrow(Usuario pagador, Usuario tutor, Instant horario) {
        Reserva reserva = new Reserva();
        reserva.setPagador(pagador);
        reserva.setBeneficiario(pagador);
        reserva.setTutor(tutor);
        reserva.setHorario(horario);
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId("mp-" + CONTADOR.incrementAndGet());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(new BigDecimal("2250.00"));
        transaccionRepository.save(transaccion);

        SesionAprendizaje sesion = new SesionAprendizaje();
        sesion.setReservaId(reserva.getId());
        sesionRepository.save(sesion);
        return new Cupo(reserva.getId(), sesion.getId(), transaccion.getId());
    }

    private UUID presentar(String bearerToken, UUID denunciadoId, UUID sesionId, String motivo)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "denunciadoId", denunciadoId.toString(),
                "sesionId", sesionId.toString(),
                "motivo", motivo));
        String respuesta = mvc.perform(post("/api/denuncias")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(respuesta).get("id").asText());
    }

    private void resolverDenuncia(String bearerToken, UUID denunciaId,
                                  Map<String, Object> payload) throws Exception {
        mvc.perform(post("/api/admin/moderacion/denuncias/" + denunciaId + "/resolver")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());
    }

    private Transaccion transaccion(UUID id) {
        return transaccionRepository.findById(id).orElseThrow();
    }

    private boolean triggerExiste(TriggerKey key) throws Exception {
        return scheduler.checkExists(key);
    }

    // ---------------------------------------------------------------- tests

    @Test
    void menorNoPuedePresentarDenuncia_inclusoConSuTokenValido() throws Exception {
        Usuario ar = usuario(TipoUsuario.ADULTO, false);
        Usuario menor = menor(ar);
        Usuario tutor = usuario(TipoUsuario.TUTOR, true);

        String body = objectMapper.writeValueAsString(Map.of(
                "denunciadoId", tutor.getId().toString(), "motivo", "acoso"));

        mvc.perform(post("/api/denuncias")
                        .header("Authorization", "Bearer " + token(menor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void denunciasCruzadas_cadaCasoPausaYSuelaEscrowPropio_noElDelOtro_yLaInfundadaNoCancelaFuturas()
            throws Exception {
        Usuario estudiante = usuario(TipoUsuario.ADULTO, false);
        Usuario tutor = usuario(TipoUsuario.TUTOR, true);
        Usuario admin = admin();

        Cupo c1 = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(3600));
        Cupo c2 = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(7200));
        // Reserva futura del mismo Tutor — la infundada NO debe cancellarla.
        Cupo future = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(7 * 86400));

        // Denuncias CRUZADAS entre la misma pareja: la A→B sobre la sesión 1 y
        // la B→A sobre la sesión 2 (pares intercambiados, casos independientes).
        UUID d1 = presentar(token(estudiante), tutor.getId(), c1.sesionId(), "fraude");
        UUID d2 = presentar(token(tutor), estudiante.getId(), c2.sesionId(), "incumplimiento");

        // Ambas pausan SU escrow al presentarse (FR-SEC-003).
        assertThat(transaccion(c1.transaccionId()).getEstado())
                .isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
        assertThat(transaccion(c2.transaccionId()).getEstado())
                .isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);

        // Descargo del denunciado (solo él) — se persiste sin bloquear nada.
        mvc.perform(post("/api/denuncias/" + d1 + "/descargo")
                        .header("Authorization", "Bearer " + token(tutor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("descargo", "fue un malentendido"))))
                .andExpect(status().isOk());
        Denuncia d1Persistida = denunciaRepository.findById(d1).orElseThrow();
        assertThat(d1Persistida.getDescargoTexto()).isEqualTo("fue un malentendido");

        // Resuelve SOLO el caso A→B como infundada.
        resolverDenuncia(token(admin), d1, Map.of("resolucion", "infundada"));

        // Su escrow re-cuenta la liberación estándar de 24hs y se reprograma…
        Transaccion t1 = transaccion(c1.transaccionId());
        assertThat(t1.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(t1.getLiberarAt()).isNotNull();
        // … per el caso cruzado SIGUE congelado (FR-SEC-011: por reserva, no por letra).
        Transaccion t2 = transaccion(c2.transaccionId());
        assertThat(t2.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
        assertThat(t2.getLiberarAt()).isNull();
        assertThat(triggerExiste(LiberacionEscrowService.triggerLiberacion(c1.transaccionId())))
                .isTrue();
        assertThat(triggerExiste(LiberacionEscrowService.triggerLiberacion(c2.transaccionId())))
                .isFalse();

        // FR-SEC-011 bis: infundada = sin sanción → reservas futuras intactas,
        // ni la del Tutor ni la suya.
        assertThat(reservaRepository.findById(future.reservaId()).orElseThrow().getEstado())
                .isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(denunciaRepository.findById(d1).orElseThrow().getEstado())
                .isEqualTo(EstadoDenuncia.RESUELTA_INFUNDADA);

        // El segundo caso (B→A) se libera recién cuando SU admin lo resuelve.
        resolverDenuncia(token(admin), d2, Map.of("resolucion", "infundada"));
        assertThat(transaccion(c2.transaccionId()).getEstado())
                .isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(triggerExiste(LiberacionEscrowService.triggerLiberacion(c2.transaccionId())))
                .isTrue();
    }

    @Test
    void sancionATutor_recorreM1M2M4_yM5EnUnaSolaResolucion() throws Exception {
        Usuario estudiante = usuario(TipoUsuario.ADULTO, false);
        Usuario tutor = usuario(TipoUsuario.TUTOR, true);
        Usuario admin = admin();

        Cupo puntual = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(3600));
        Cupo futura = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(7 * 86400));

        UUID d = presentar(token(estudiante), tutor.getId(), puntual.sesionId(), "comportamiento_inapropiado");
        resolverDenuncia(token(admin), d,
                Map.of("resolucion", "fundada", "tipoSancion", "suspension_temporal", "diasSuspension", 7));

        // Sanción persistida con su ventana de vigencia.
        Sancion sancion = sancionRepository.findByDenunciaId(d).orElseThrow();
        assertThat(sancion.getOrigen()).isEqualTo(OrigenSancion.DENUNCIA);
        assertThat(sancion.getTipo()).isEqualTo(TipoSancion.SUSPENSION_TEMPORAL);
        assertThat(sancion.getDiasSuspension()).isEqualTo(7);
        assertThat(sancion.getVigenteHasta()).isNotNull();

        // M1 + M2: cuenta suspendida y afuera del matching (mismo flag que leen
        // tutoresActivosParaMatching/idsActivosParaMatching).
        Usuario trasSancion = usuarioRepository.findById(tutor.getId()).orElseThrow();
        assertThat(trasSancion.getEstadoCuenta()).isEqualTo(EstadoCuenta.SUSPENDIDA);
        assertThat(trasSancion.isActivoParaMatching()).isFalse();

        // M4: toda reserva futura del sancionado se cancela — incluida la PROMO
        // (la sesión donde ocurrió la falta), que deja de dictarse.
        Reserva rPuntual = reservaRepository.findById(puntual.reservaId()).orElseThrow();
        Reserva rFutura = reservaRepository.findById(futura.reservaId()).orElseThrow();
        assertThat(rPuntual.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(rPuntual.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.SANCION);
        assertThat(rFutura.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(rFutura.getMotivoCancelacion()).isEqualTo(MotivoCancelacion.SANCION);

        // M5: la futura se reembolsa vía el encadenamiento reserva.cancelada →
        // reembolsarSiRetenida. La de la sesión denunciada queda reembolsada o
        // liberada según el orden de listeners — ambas son "dinero fuera del escrow".
        assertThat(transaccion(futura.transaccionId()).getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(Set.of(EstadoTransaccion.LIBERADO, EstadoTransaccion.REEMBOLSADO))
                .contains(transaccion(puntual.transaccionId()).getEstado());

        // US-6: reactivación automática agendada a vigente_hasta (Quartz persistido).
        assertThat(triggerExiste(new TriggerKey("reactivacion-trigger-" + tutor.getId(), "m9-seguridad")))
                .isTrue();
    }

    @Test
    void usuarioNoModerador_noResuelve_403() throws Exception {
        Usuario estudiante = usuario(TipoUsuario.ADULTO, false);
        Usuario tutor = usuario(TipoUsuario.TUTOR, true);

        Cupo cupo = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(3600));
        UUID d = presentar(token(estudiante), tutor.getId(), cupo.sesionId(), "fraude");

        mvc.perform(post("/api/admin/moderacion/denuncias/" + d + "/resolver")
                        .header("Authorization", "Bearer " + token(estudiante))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("resolucion", "infundada"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void escalada_fuerzaSuspensionDefinitiva_yReembolsaElEscrow() throws Exception {
        Usuario estudiante = usuario(TipoUsuario.ADULTO, false);
        Usuario tutor = usuario(TipoUsuario.TUTOR, true);
        Usuario admin = admin();

        Cupo cupo = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(3600));
        UUID d = presentar(token(estudiante), tutor.getId(), cupo.sesionId(), "contenido_ilegal");
        resolverDenuncia(token(admin), d, Map.of("resolucion", "escalada"));

        Sancion sancion = sancionRepository.findByDenunciaId(d).orElseThrow();
        assertThat(sancion.getTipo()).isEqualTo(TipoSancion.SUSPENSION_DEFINITIVA);
        assertThat(denunciaRepository.findById(d).orElseThrow().getEstado())
                .isEqualTo(EstadoDenuncia.ESCALADA);
        assertThat(transaccion(cupo.transaccionId()).getEstado())
                .isEqualTo(EstadoTransaccion.REEMBOLSADO);
    }

    @Test
    void alertaKillSwitch_reactivar_restableceMatchingDelTutor() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR, false); // M3 ya lo sacó del matching
        Usuario estudiante = usuario(TipoUsuario.ADULTO, false);
        Usuario admin = admin();
        Cupo cupo = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(3600));

        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(cupo.sesionId());
        alerta.setRama("menor");
        alerta.setDetectadoId(tutor.getId());
        alertaRepository.save(alerta);

        mvc.perform(post("/api/admin/moderacion/alertas-seguridad/" + alerta.getId() + "/resolver")
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("decision", "reactivar"))))
                .andExpect(status().isOk());

        AlertaSeguridad trasRevivir = alertaRepository.findById(alerta.getId()).orElseThrow();
        assertThat(trasRevivir.getEstado())
                .isEqualTo(AlertaSeguridad.ESTADO_RESUELTA_REACTIVACION);
        assertThat(trasRevivir.getClipRetencionHasta()).isNotNull(); // BR-KS-02

        Usuario coreTutor = usuarioRepository.findById(tutor.getId()).orElseThrow();
        assertThat(coreTutor.isActivoParaMatching()).isTrue();
        assertThat(coreTutor.getEstadoCuenta()).isEqualTo(EstadoCuenta.ACTIVA);
    }

    @Test
    void alertaKillSwitch_sancionar_publicaLaSancionDelTutor() throws Exception {
        Usuario tutor = usuario(TipoUsuario.TUTOR, false);
        Usuario estudiante = usuario(TipoUsuario.ADULTO, false);
        Usuario admin = admin();
        Cupo cupo = cupoConEscrow(estudiante, tutor, Instant.now().plusSeconds(3600));

        AlertaSeguridad alerta = new AlertaSeguridad();
        alerta.setSesionId(cupo.sesionId());
        alerta.setRama("adultos");
        alerta.setDetectadoId(tutor.getId());
        alertaRepository.save(alerta);

        mvc.perform(post("/api/admin/moderacion/alertas-seguridad/" + alerta.getId() + "/resolver")
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("decision", "sancionar", "tipoSancion", "suspension_temporal",
                                        "diasSuspension", 15))))
                .andExpect(status().isOk());

        AlertaSeguridad trasBajar = alertaRepository.findById(alerta.getId()).orElseThrow();
        assertThat(trasBajar.getEstado()).isEqualTo(AlertaSeguridad.ESTADO_RESUELTA_BAJA);

        Sancion sancion = sancionRepository.findByAlertaId(alerta.getId()).orElseThrow();
        assertThat(sancion.getOrigen()).isEqualTo(OrigenSancion.ALERTA_SEGURIDAD);
        assertThat(sancion.getTipo()).isEqualTo(TipoSancion.SUSPENSION_TEMPORAL);
        assertThat(sancion.getDiasSuspension()).isEqualTo(15);

        Usuario tutorSancionado = usuarioRepository.findById(tutor.getId()).orElseThrow();
        assertThat(tutorSancionado.getEstadoCuenta()).isEqualTo(EstadoCuenta.SUSPENDIDA);
        assertThat(tutorSancionado.isActivoParaMatching()).isFalse();
    }
}