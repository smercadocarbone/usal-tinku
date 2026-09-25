package com.tinku.pagos.service;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.repository.UsuarioRepository;
import com.tinku.seguridad.evento.DenunciaRegistradaEvent;
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
import static org.mockito.Mockito.verify;

/**
 * T-M5-10 — liberación pausada por denuncia: "efectivamente no libera fondos
 * hasta la resolución". Aislado en su propia clase (contexto + scheduler de
 * Quartz propios) para no interferir con los disparos reales de Quartz de
 * {@code LiberacionEscrowIntegracionTest} (timing compartido dentro del mismo
 * JobStore desestabiliza los tests de disparos vencidos).
 *
 * Escenario: la ventana de liberación de 24hs YA venció (sin la denuncia, el
 * job habría liberado) y la denuncia llega igual → el escrow queda congelado;
 * un disparo vencido que sobrevive (toro muerto) DISPARA de verdad por el job
 * real de Quartz pero la pausada lo absorbe sin liberar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class LiberacionDenunciaPausaIntegracionTest {

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
    @Autowired TransaccionRepository transaccionRepo;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired ReservaRepository reservaRepository;
    @Autowired LiberacionEscrowService liberacionEscrow;
    @Autowired Scheduler scheduler;

    @MockitoBean LiberacionProveedor liberacion;
    @MockitoBean ReembolsoProveedor reembolso;
    @MockitoBean AlertaSoporteProveedor alerta;

    private static final AtomicInteger CONTADOR = new AtomicInteger();

    private Usuario guardarUsuario(TipoUsuario tipo, String nombre) {
        Usuario u = new Usuario();
        u.setDni(String.format("%08d", 46_000_000 + CONTADOR.incrementAndGet()));
        u.setNombre(nombre);
        u.setApellido("Lopez");
        u.setFechaNacimiento(LocalDate.of(1988, 5, 15));
        u.setTipo(tipo);
        u.setPasswordHash("hash");
        if (tipo == TipoUsuario.ADULTO) {
            u.setCapacidadEstudiante(true);
            u.setCapacidadAdultoResponsable(true);
        }
        return usuarioRepository.save(u);
    }

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
        transaccion.setMpPaymentId("pago-denuncia-" + CONTADOR.incrementAndGet());
        transaccion.setMontoBruto(BigDecimal.valueOf(15000));
        transaccion.setComisionPlataforma(new BigDecimal("2250.00"));
        transaccionRepo.save(transaccion);

        return new Escena(reserva.getId(), transaccion);
    }

    private boolean triggerExiste(UUID transaccionId) throws Exception {
        return scheduler.checkExists(LiberacionEscrowService.triggerLiberacion(transaccionId));
    }

    private static void esperarHasta(java.util.concurrent.Callable<Boolean> condicion)
            throws Exception {
        long limite = System.currentTimeMillis() + 15_000;
        while (!condicion.call() && System.currentTimeMillis() < limite) {
            Thread.sleep(100);
        }
        assertThat(condicion.call()).as("No se cumplió la condición a tiempo").isTrue();
    }

    // ---------------------------------------------------------------- tests

    @Test
    void denunciaConVentanaYaVencida_elDisparoVencidoNoLiberaFondosHastaResolucion()
            throws Exception {
        // La ventana de 24hs YA venció: sin la denuncia, el job habría liberado.
        Escena e = escena();
        Transaccion t0 = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        t0.setLiberarAt(Instant.now().minusSeconds(30)); // sesión finalizada >24h antes
        transaccionRepo.save(t0);

        // 1) La denuncia llega y congela el escrow. "Hasta la resolución": acá no
        // existe ninguna vía que descongele (denuncia.resuelta es de M9-D, diferido).
        events.publishEvent(new DenunciaRegistradaEvent("M9", e.reservaId()));

        Transaccion t = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
        assertThat(t.getLiberarAt()).isNull();
        assertThat(triggerExiste(e.transaccion().getId())).isFalse();
        verify(liberacion, never()).liberarAlTutor(any(Transaccion.class));

        // 2) Un disparo vencido (toro muerto) DISPARA de verdad vía el job real de
        // Quartz: como es one-shot, Quartz lo limpia del JobStore al completarlo —
        // verlo desaparecer == el job ya se procesó. La pausada lo absorbe sin
        // liberar fondos, sin alerta a Soporte (es un guard, no un fallo).
        liberacionEscrow.programarLiberacion(e.transaccion().getId(),
                Instant.now().minusSeconds(1));
        esperarHasta(() -> !triggerExiste(e.transaccion().getId()));

        Transaccion t2 = transaccionRepo.findById(e.transaccion().getId()).orElseThrow();
        assertThat(t2.getEstado()).isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
        assertThat(t2.getLiberarAt()).isNull();
        assertThat(t2.getIntentosLiberacion()).isZero();
        verify(liberacion, never()).liberarAlTutor(any(Transaccion.class));
        verify(alerta, never()).notificarFalloLiberacion(any(Transaccion.class));
    }
}