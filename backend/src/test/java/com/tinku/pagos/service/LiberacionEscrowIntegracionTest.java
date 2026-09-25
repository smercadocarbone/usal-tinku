package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.evento.DenunciaRegistradaEvent;
import com.tinku.aula.evento.SesionFinalizadaEvent;
import com.tinku.aula.evento.SesionInterrumpidaEvent;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Job de liberación automática (T-M5-05) + reintentos con backoff y alerta
 * (T-M5-06, FR-PAG-007; fila "Reintentos de liberación de pago — 3, backoff
 * 5min/15min/1h" de Tabla_Tiempos).
 *
 * Los archivos del provider real (LiberacionProveedorMercadoPago) se prueban en
 * MercadoPagoClientHttpTest; acá el port y la alerta van mockeados — el job real
 * de Quartz (disparo vencido en el JOB_STORE) se verifica en el último test.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LiberacionEscrowIntegracionTest {

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

    @Autowired LiberacionEscrowService liberacionEscrow;
    @Autowired TransaccionRepository transaccionRepo;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired ApplicationEventPublisher events;
    @Autowired Scheduler scheduler;

    @MockBean LiberacionProveedor liberacion;
    @MockBean ReembolsoProveedor reembolso;
    @MockBean AlertaSoporteProveedor alerta;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    /** Escena base: reserva confirmada + escrow retenido. */
    private record Escena(UUID reservaId, Transaccion transaccion) {
    }

    private Escena escena() {
        Usuario ar = guardarUsuario(TipoUsuario.ADULTO, "Ana");
        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(ar);
        reserva.setTutor(guardarUsuario(TipoUsuario.TUTOR, "Pablo"));
        reserva.setHorario(Instant.now().plusSeconds(3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepository.save(reserva);

        Transaccion transaccion = new Transaccion();
        transaccion.setReservaId(reserva.getId());
        transaccion.setMpPaymentId("pago-liberacion-" + CONTADOR.incrementAndGet());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(new BigDecimal("2250.00"));
        transaccionRepo.save(transaccion);

        return new Escena(reserva.getId(), transaccion);
    }

    private Usuario guardarUsuario(TipoUsuario tipo, String nombre) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 41_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre(nombre);
        u.setApellido("Lopez");
        u.setFechaNacimiento(
                tipo == TipoUsuario.TUTOR ? LocalDate.of(1999, 5, 15) : LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

    // ------------------------------------------------ T-M5-05 (liberación)

    @Test
    void liberacionExitosaMarcaLiberadoSinDejarDisparo() throws Exception {
        Escena e = escena();

        liberacionEscrow.ejecutarLiberacion(e.transaccion().getId());

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.LIBERADO);
        assertThat(t.getLiberarAt()).isNull();
        assertThat(t.getIntentosLiberacion()).isZero();
        verify(liberacion).liberarAlTutor(any(Transaccion.class));
        assertThat(triggerExiste(e.transaccion().getId())).isFalse();
    }

    @Test
    void jobRealDeQuartzDisparaAlVencerElDisparo() throws Exception {
        Escena e = escena();

        liberacionEscrow.programarLiberacion(e.transaccion().getId(),
                Instant.now().minusSeconds(1));
        esperarHasta(() -> {
            Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
            return t.getEstado() == EstadoTransaccion.LIBERADO;
        });

        verify(liberacion).liberarAlTutor(any(Transaccion.class));
        // El disparo del JOB_STORE se auto-limpia con la liberación.
        esperarHasta(() -> !triggerExiste(e.transaccion().getId()));
    }

    // ------------------------------------------------ T-M5-06 (backoff y alerta)

    @Test
    void falloDelProviderIncrementaIntentosYReagendaConBackoff() throws Exception {
        Escena e = escena();
        // Simula la caída de MercadoPago (FR-PAG-007).
        org.mockito.Mockito.doThrow(new MercadoPagoNoDisponibleException())
                .when(liberacion).liberarAlTutor(any(Transaccion.class));

        liberacionEscrow.ejecutarLiberacion(e.transaccion().getId());

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(t.getIntentosLiberacion()).isEqualTo(1);
        // Alerta inmediata en paralelo + trigger reprogramado a ~5 min.
        verify(alerta).notificarFalloLiberacion(any(Transaccion.class));
        var proximo = proximoDisparo(e.transaccion().getId());
        assertThat(proximo).isPresent();
        assertThat(proximo.get())
                .isBetween(Instant.now().plus(Duration.ofMinutes(4)), Instant.now().plus(Duration.ofMinutes(6)));
    }

    @Test
    void segundoFalloAcumulaBackoffDe15Min() throws Exception {
        Escena e = escena();
        org.mockito.Mockito.doThrow(new MercadoPagoNoDisponibleException())
                .when(liberacion).liberarAlTutor(any(Transaccion.class));

        liberacionEscrow.ejecutarLiberacion(e.transaccion().getId()); // → +5min
        liberacionEscrow.ejecutarLiberacion(e.transaccion().getId()); // → +15min

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getIntentosLiberacion()).isEqualTo(2);
        var proximo = proximoDisparo(e.transaccion().getId());
        assertThat(proximo).isPresent();
        assertThat(proximo.get())
                .isBetween(Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void trasLos3ReintentosSeDetieneYQuedaEnColaDelAdmin() throws Exception {
        Escena e = escena();
        org.mockito.Mockito.doThrow(new MercadoPagoNoDisponibleException())
                .when(liberacion).liberarAlTutor(any(Transaccion.class));

        // 4 ejecuciones = 3 reintentos fallidos: la alerta se manda en CADA fallo.
        for (int i = 0; i < 4; i++) {
            liberacionEscrow.ejecutarLiberacion(e.transaccion().getId());
        }

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.RETENIDO_ESCROW);
        assertThat(t.getIntentosLiberacion()).isEqualTo(4);
        verify(alerta, times(4)).notificarFalloLiberacion(any(Transaccion.class));
        // Sin disparos: los reintentos automáticos se detuvieron (cola de M8).
        assertThat(triggerExiste(e.transaccion().getId())).isFalse();
        // La cola de intervención manual (Spec M8) la lee por estado+intentos.
        assertThat(transaccionRepo.findByEstadoAndIntentosLiberacion(
                EstadoTransaccion.RETENIDO_ESCROW, 4))
                .extracting(Transaccion::getId)
                .contains(t.getId());
    }

    // ------------------------------------------------ hooks del escrow (M5-C)

    @Test
    void sesionFinalizadaAgendaElJobALiberarAt() throws Exception {
        Escena e = escena();
        Instant fin = Instant.parse("2099-01-01T10:00:00Z");

        events.publishEvent(new SesionFinalizadaEvent("M3", e.reservaId(), fin));

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getLiberarAt()).isEqualTo(fin.plus(Duration.ofHours(24)));
        var proximo = proximoDisparo(e.transaccion().getId());
        assertThat(proximo).isPresent();
        assertThat(proximo.get()).isEqualTo(t.getLiberarAt());
        verify(liberacion, never()).liberarAlTutor(any(Transaccion.class));
    }

    @Test
    void denunciaRegistradaCancelaElJobProgramado() throws Exception {
        Escena e = escena();
        liberacionEscrow.programarLiberacion(e.transaccion().getId(),
                Instant.now().plusMillis(500));
        assertThat(triggerExiste(e.transaccion().getId())).isTrue();

        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
        assertThat(t.getLiberarAt()).isNull();
        assertThat(triggerExiste(e.transaccion().getId())).isFalse();
        // Job sobre pausada no hace nada (T-M5-05: estado != pausado_denuncia).
        liberacionEscrow.ejecutarLiberacion(e.transaccion().getId());
        verify(liberacion, never()).liberarAlTutor(any(Transaccion.class));
    }

    @Test
    void reembolsoCancelaElJobProgramado() throws Exception {
        Escena e = escena();
        liberacionEscrow.programarLiberacion(e.transaccion().getId(),
                Instant.now().plusSeconds(3600));

        events.publishEvent(new SesionInterrumpidaEvent("M3", e.reservaId()));

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.REEMBOLSADO);
        assertThat(triggerExiste(e.transaccion().getId())).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private boolean triggerExiste(UUID transaccionId) throws Exception {
        return scheduler.checkExists(LiberacionEscrowService.triggerLiberacion(transaccionId));
    }

    private java.util.Optional<Instant> proximoDisparo(UUID transaccionId) throws Exception {
        var trigger = scheduler.getTrigger(
                LiberacionEscrowService.triggerLiberacion(transaccionId));
        return trigger == null || trigger.getNextFireTime() == null
                ? java.util.Optional.empty()
                : java.util.Optional.of(trigger.getNextFireTime().toInstant());
    }

    private static void esperarHasta(java.util.concurrent.Callable<Boolean> condicion)
            throws Exception {
        long limite = System.currentTimeMillis() + 10_000;
        while (!condicion.call() && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }
        assertThat(condicion.call()).as("No se cumplió la condición a tiempo").isTrue();
    }
}