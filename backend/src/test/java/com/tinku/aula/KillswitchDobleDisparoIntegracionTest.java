package com.tinku.aula;

import com.tinku.identidad.model.Usuario;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los dos clientes de la clase (Tutor y alumno) detectan lo mismo y disparan el kill-switch a
 * la vez. Sin lock de la sesión, cada uno creaba su Alerta: dos alertas para una sesión rompen
 * todo lo que la busca de a una (la subida del clip, la cola de Moderación).
 */
class KillswitchDobleDisparoIntegracionTest extends IntegracionBase {

    @Autowired SesionService sesionService;
    @Autowired ReservaRepository reservaRepo;
    @Autowired PlatformTransactionManager txManager;

    @Test
    void dosDisparosALaVezConMenor_unaSolaAlerta() throws Exception {
        Usuario ar = adulto(true);
        Usuario menor = menor(ar);
        Usuario tutor = tutor(null, null);
        Reserva reserva = new Reserva();
        reserva.setPagador(ar);
        reserva.setBeneficiario(menor);
        reserva.setTutor(tutor);
        reserva.setHorario(Instant.now().minusSeconds(600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.CONFIRMADA);
        reservaRepo.saveAndFlush(reserva);
        UUID sesionId = jdbc.queryForObject("INSERT INTO aula.sesiones_aprendizaje (reserva_id, estado, inicio_real) "
                + "VALUES (?, 'en_curso', now() - interval '10 minutes') RETURNING id", UUID.class, reserva.getId());

        CountDownLatch disparado = new CountDownLatch(1);
        CountDownLatch segundoTermino = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> delTutor = pool.submit(() -> tx.execute(st -> {
                sesionService.ejecutarKillswitch(tutor, sesionId, tutor.getId());
                disparado.countDown();
                try {
                    segundoTermino.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));
            assertThat(disparado.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> delAlumno = pool.submit(() -> {
                try {
                    return sesionService.ejecutarKillswitch(menor, sesionId, tutor.getId());
                } finally {
                    segundoTermino.countDown();
                }
            });
            delTutor.get(30, TimeUnit.SECONDS);
            delAlumno.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM aula.alertas_seguridad WHERE sesion_id = ?",
                Integer.class, sesionId)).isEqualTo(1);
    }
}
