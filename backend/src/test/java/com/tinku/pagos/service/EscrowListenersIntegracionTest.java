package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.evento.DenunciaRegistradaEvent;
import com.tinku.aula.evento.SesionFinalizadaEvent;
import com.tinku.aula.evento.SesionInterrumpidaEvent;
import com.tinku.seguridad.evento.AlertaResueltaEvent;
import com.tinku.aula.evento.SesionKillswitchAdultosEvent;
import com.tinku.aula.evento.SesionKillswitchMenorEvent;
import com.tinku.aula.evento.SesionNoShowDobleEvent;
import com.tinku.aula.evento.SesionNoShowEstudianteEvent;
import com.tinku.aula.evento.SesionNoShowTutorEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.seguridad.evento.DenunciaResueltaEvent;
import com.tinku.reservas.evento.ReservaCanceladaEvent;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.shared.ResolucionDenuncia;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Listeners de eventos entrantes (T-M5-04, tabla del Plan M5 §2) probados uno
 * por uno: cada evento de {code sesion.*} y {code denuncia.registrada} dispara
 * exactamente el efecto de M5 esperado sobre el escrow. Los eventos se publican
 * con {@code ApplicationEventPublisher} directamente (los publishers reales de
 * M3-E y M9-D no existen todavía) y los puertos de liberación/reembolso van
 * mockeados — fail-closed hasta Chunk M5-C/M5-D.
 *
 * Convención del chunk: transición SOLO desde {@code retenido_escrow}; un evento
 * repetido o de una Reserva sin escrow no produce nada. Los datos se arman por
 * repositorio (no corre el flujo de M4 — ya cubierto en ReservasFlujosIntegracionTest).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EscrowListenersIntegracionTest {

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

    @Autowired ApplicationEventPublisher events;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired TransaccionRepository transaccionRepository;
    @Autowired Scheduler scheduler;

    @MockitoBean LiberacionProveedor liberacion;
    @MockitoBean ReembolsoProveedor reembolso;

    private static final AtomicInteger CONTADOR_DNIS = new AtomicInteger();

    private String dniUnico() {
        return String.format("%08d", 40_000_000 + CONTADOR_DNIS.incrementAndGet());
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String nombre) {
        Usuario u = new Usuario();
        u.setDni(dniUnico());
        u.setNombre(nombre);
        u.setApellido("Lopez");
        u.setFechaNacimiento(LocalDate.of(1990, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    /** Escena base: reserva pendiente de pagar + escrow retenido sobre ella. */
    private record Escena(UUID reservaId, UUID pagadorId, UUID tutorId, Transaccion transaccion) {
    }

    private Escena escena() {
        return escena(Instant.now().plusSeconds(3600));
    }

    private Escena escena(Instant horario) {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, "Pablo");

        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(ar);
        reserva.setTutor(tutor);
        reserva.setHorario(horario);
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reservaRepository.save(reserva);

        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId("pago-" + CONTADOR_DNIS.get());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(new BigDecimal("2250.00"));
        transaccionRepository.save(transaccion);

        return new Escena(reserva.getId(), ar.getId(), tutor.getId(), transaccion);
    }

    /** La cancelación manual solo aplica a Reservas confirmadas (FR-RES-008);
     * el listener de M5 además necesita horario y pagador, que ya están. */
    private void confirmarReserva(UUID reservaId) {
        Reserva reserva = reservaRepository.findById(reservaId).orElseThrow();
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);
    }

    // ------------------------------------------------ tests

    @Test
    void sesionFinalizada_iniciaLaCuentaDe24hsParaLiberar() {
        Escena e = escena();
        Instant fin = Instant.parse("2099-01-01T10:00:00Z");

        events.publishEvent(new SesionFinalizadaEvent("M3", e.reservaId(), fin));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(t.getLiberarAt()).isEqualTo(fin.plus(EscrowService.VENTANA_LIBERACION));
        verifyNoInteractions(liberacion, reembolso);
    }

    @Test
    void sesionInterrumpida_reembolsaTotal() {
        Escena e = escena();

        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(t.getLiberarAt()).isNull();
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
        verifyNoInteractions(liberacion);
    }

    @Test
    void sesionNoShowEstudiante_liberaAlTutorDeInmediato() {
        Escena e = escena();

        events.publishEvent(new SesionNoShowEstudianteEvent("M4", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.LIBERADO);
        verify(liberacion).liberarAlTutor(any(Transaccion.class));
        verifyNoInteractions(reembolso);
    }

    @Test
    void sesionNoShowTutor_reembolsaTotal() {
        Escena e = escena();

        events.publishEvent(new SesionNoShowTutorEvent("M4", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
        verifyNoInteractions(liberacion);
    }

    @Test
    void sesionNoShowDoble_reembolsaSinLiberarNada() {
        Escena e = escena();

        events.publishEvent(new SesionNoShowDobleEvent("M4", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
        verifyNoInteractions(liberacion);
    }

    @Test
    void sesionKillswitchMenor_pausaElEscrowSinReembolsar() {
        // ADR-M3-02 (D3): el corte es inmediato, la plata no. M9 decide al resolver la Alerta.
        // FASE2-10: la pausa del kill-switch es pausado_alerta, no pausado_denuncia.
        Escena e = escena();
        Transaccion t0 = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        t0.setLiberarAt(Instant.now().plusSeconds(60));
        transaccionRepository.save(t0);

        events.publishEvent(new SesionKillswitchMenorEvent("M3", e.reservaId(), e.tutorId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_ALERTA);
        assertThat(t.getLiberarAt()).isNull();
        verifyNoInteractions(reembolso);
    }

    @Test
    void sesionKillswitchAdultos_pausaElEscrowSinReembolsar() {
        Escena e = escena();

        events.publishEvent(new SesionKillswitchAdultosEvent("M3", e.reservaId(), e.pagadorId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_ALERTA);
        verifyNoInteractions(reembolso);
    }

    @Test
    void alertaResuelta_trasKillswitch_reembolsaTotalInclusoSiElDetectadoEsElPagador() {
        // FR-PAG-012: total aunque el pagador haya sido el detectado — ahora, al resolver.
        Escena e = escena();
        events.publishEvent(new SesionKillswitchAdultosEvent("M3", e.reservaId(), e.pagadorId()));

        events.publishEvent(new AlertaResueltaEvent("M9", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
    }

    @Test
    void alertaResuelta_sinEscrowPausado_esNoOp() {
        // Alertas previas a ADR-M3-02 (escrow nunca pausado): no se mueve dinero.
        Escena e = escena();

        events.publishEvent(new AlertaResueltaEvent("M9", e.reservaId()));

        assertThat(transaccionRepository.findById(e.transaccion().getId()).orElseThrow().getEstado())
                .isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        verifyNoInteractions(reembolso);
    }

    @Test
    void denunciaRegistrada_pausaElEscrowYCancelaLaVentanaDeLiberacion() {
        Escena e = escena();
        Transaccion t0 = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        t0.setLiberarAt(Instant.now().plusSeconds(60)); // la cuenta de 24hs ya corría
        transaccionRepository.save(t0);

        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
        assertThat(t.getLiberarAt()).isNull();
        verifyNoInteractions(liberacion, reembolso);
    }

    // ------------------------- FASE2-10: la Alerta manda sobre la Denuncia

    @Test
    void denunciaResueltaInfundada_conAlertaPendiente_noLiberaAlTutor() throws Exception {
        // FASE2-10 (riesgo abierto aceptado en ADR-M3-02): kill-switch con menor →
        // escrow en pausa → además presentan una Denuncia sobre la misma sesión →
        // un Admin la resuelve INFUNDADA. La Alerta de seguridad sigue sin revisar:
        // el Tutor NO debe cobrar antes de que M9 resuelva la Alerta (Art. II).
        Escena e = escena();
        events.publishEvent(new SesionKillswitchMenorEvent("M3", e.reservaId(), e.tutorId()));
        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));
        events.publishEvent(new DenunciaResueltaEvent("M9", UUID.randomUUID(), e.tutorId(),
                e.reservaId(), ResolucionDenuncia.INFUNDADA));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_ALERTA);
        assertThat(t.getLiberarAt()).isNull();
        assertThat(triggerExiste(e.transaccion().getId())).isFalse();
        verify(liberacion, never()).liberarAlTutor(any(Transaccion.class));
        verifyNoInteractions(reembolso);
    }

    @Test
    void alertaResuelta_trasDenunciaResueltaAntes_igualReembolsa() {
        // FASE2-10: la Denuncia se resolvió ANTES que la Alerta; la Alerta manda.
        // Cuando el Admin resuelva la Alerta, igual se reembolsa el total (D3).
        Escena e = escena();
        events.publishEvent(new SesionKillswitchMenorEvent("M3", e.reservaId(), e.tutorId()));
        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));
        events.publishEvent(new DenunciaResueltaEvent("M9", UUID.randomUUID(), e.tutorId(),
                e.reservaId(), ResolucionDenuncia.INFUNDADA));

        events.publishEvent(new AlertaResueltaEvent("M9", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(t.getLiberarAt()).isNull();
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
        verify(liberacion, never()).liberarAlTutor(any(Transaccion.class));
    }

    @Test
    void killswitchSobreEscrowYaPausadoPorDenuncia_pasaAPausadoAlerta() {
        // FASE2-10: la pausa por Alerta es más grave — un kill-switch posterior a
        // una Denuncia sube el estado, no convive en un nivel menor.
        Escena e = escena();
        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));
        events.publishEvent(new SesionKillswitchMenorEvent("M3", e.reservaId(), e.tutorId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_ALERTA);
        assertThat(t.getLiberarAt()).isNull();
        verifyNoInteractions(reembolso, liberacion);
    }

    @Test
    void denunciaRegistradaSobrePausadoAlerta_noCambiaElEstado() {
        // FASE2-10: la Denuncia posterior no baja la gravedad de la pausa.
        Escena e = escena();
        events.publishEvent(new SesionKillswitchMenorEvent("M3", e.reservaId(), e.tutorId()));
        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_ALERTA);
        assertThat(t.getLiberarAt()).isNull();
        verifyNoInteractions(reembolso, liberacion);
    }

    // ---------------------------------------------------------------- /FASE2-10

    @Test
    void eventoDeReservaSinEscrow_noHaceNada() {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(ar);
        reserva.setTutor(guardarUsuario(TipoUsuario.TUTOR, "Pablo"));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        events.publishEvent(new SesionFinalizadaEvent("M3", reserva.getId(), Instant.now()));
        events.publishEvent(new DenunciaRegistradaEvent("M9", reserva.getId()));

        verifyNoInteractions(liberacion, reembolso);
    }

    @Test
    void eventoRepetidoSobreTransaccionYaResuelta_noVuelveACambiarNada() {
        Escena e = escena();

        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));
        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        // El segundo evento no re-embolsa ni toca el estado.
        verify(reembolso, times(1)).reembolsarTotal(any(Transaccion.class));
    }

    // ------------------------------------------------ M5-D (T-M5-07/08)

    @Test
    void reservaCancelada_menosDe24hs_cancelaElTutor_reembolsaTotal() {
        Escena e = escena(Instant.now().plusSeconds(3600));
        confirmarReserva(e.reservaId());

        events.publishEvent(new ReservaCanceladaEvent("M4", e.reservaId(), e.tutorId()));

        // FR-RES-008: cancela el Tutor a último momento → reembolso total, aunque
        // la Sesión se pierda por responsabilidad del Tutor.
        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(t.getLiberarAt()).isNull();
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
        verifyNoInteractions(liberacion);
    }

    @Test
    void reservaCancelada_menosDe24hs_cancelaElPagador_liberaAlTutor() {
        Escena e = escena(Instant.now().plusSeconds(3600));
        confirmarReserva(e.reservaId());

        events.publishEvent(new ReservaCanceladaEvent("M4", e.reservaId(), e.pagadorId()));

        // FR-RES-008/016 (US-7): quien pagó cancela con <24hs → el dinero ya
        // retenido se libera al Tutor (no es transacción nueva, es el mismo escrow).
        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.LIBERADO);
        assertThat(t.getLiberarAt()).isNull();
        verify(liberacion).liberarAlTutor(any(Transaccion.class));
        verifyNoInteractions(reembolso);
    }

    @Test
    void reservaCancelada_masDe24hs_reembolsaTotal_aunqueCanceleElPagador() {
        Escena e = escena(Instant.now().plusSeconds(25 * 3600L));
        confirmarReserva(e.reservaId());

        events.publishEvent(new ReservaCanceladaEvent("M4", e.reservaId(), e.pagadorId()));

        // US-6: con margen, quien pague cancela sin penalidad → reembolso.
        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
        verifyNoInteractions(liberacion);
    }

    private boolean triggerExiste(UUID transaccionId) throws Exception {
        return scheduler.checkExists(LiberacionEscrowService.triggerLiberacion(transaccionId));
    }
}