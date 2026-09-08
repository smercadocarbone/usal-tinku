package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.pagos.evento.DenunciaRegistradaEvent;
import com.tinku.pagos.evento.SesionFinalizadaEvent;
import com.tinku.pagos.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.evento.SesionKillswitchAdultosEvent;
import com.tinku.pagos.evento.SesionKillswitchMenorEvent;
import com.tinku.pagos.evento.SesionNoShowDobleEvent;
import com.tinku.pagos.evento.SesionNoShowEstudianteEvent;
import com.tinku.pagos.evento.SesionNoShowTutorEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
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
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"))
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

    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;

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
    private record Escena(UUID reservaId, UUID pagadorId, Transaccion transaccion) {
    }

    private Escena escena() {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Usuario tutor = guardarUsuario(TipoUsuario.TUTOR, "Pablo");

        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(ar);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        reservaRepository.save(reserva);

        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId("pago-" + CONTADOR_DNIS.get());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(new BigDecimal("2250.00"));
        transaccionRepository.save(transaccion);

        return new Escena(reserva.getId(), ar.getId(), transaccion);
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
    void sesionKillswitchMenor_reembolsaAlEstudiante() {
        Escena e = escena();

        events.publishEvent(new SesionKillswitchMenorEvent("M3", e.reservaId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
    }

    @Test
    void sesionKillswitchAdultos_reembolsaTotalInclusoSiElDetectadoEsElPagador() {
        // FR-PAG-012: el reembolso por kill-switch es total aunque el propio
        // pagador haya sido el detectado — la plataforma no usa dinero como castigo.
        Escena e = escena();

        events.publishEvent(new SesionKillswitchAdultosEvent("M3", e.reservaId(), e.pagadorId()));

        Transaccion t = transaccionRepository.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        verify(reembolso).reembolsarTotal(any(Transaccion.class));
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
}