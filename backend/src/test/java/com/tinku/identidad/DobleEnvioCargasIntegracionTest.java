package com.tinku.identidad;

import com.tinku.identidad.model.TipoCredencial;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.service.CertificadoService;
import com.tinku.identidad.service.CredencialService;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Doble envío de "Subir credencial" / "Subir CAP" (dos POST casi juntos): el chequeo de
 * "ya tenés una en revisión" era leer-y-después-insertar, y el segundo no veía la fila del
 * primero todavía sin commitear. Quedaban dos pendientes: cada una contaba como el mismo
 * intento del ciclo y, desde ahí, la búsqueda de "la pendiente" (Optional) fallaba.
 */
class DobleEnvioCargasIntegracionTest extends IntegracionBase {

    @Autowired CredencialService credencialService;
    @Autowired CertificadoService certificadoService;
    @Autowired PlatformTransactionManager txManager;

    /** El primero carga y se frena antes del commit; mientras tanto entra el segundo. */
    private Throwable dobleEnvio(Callable<?> carga) throws Exception {
        CountDownLatch cargado = new CountDownLatch(1);
        CountDownLatch segundoTermino = new CountDownLatch(1);
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> primero = pool.submit(() -> tx.execute(st -> {
                try {
                    carga.call();
                    cargado.countDown();
                    segundoTermino.await(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }));
            assertThat(cargado.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> segundo = pool.submit(() -> {
                try {
                    return carga.call();
                } finally {
                    segundoTermino.countDown();
                }
            });
            primero.get(30, TimeUnit.SECONDS);
            try {
                segundo.get(30, TimeUnit.SECONDS);
                return null;
            } catch (ExecutionException e) {
                return e.getCause();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void subirCredencialDosVecesALaVez_quedaUnaSolaPendiente() throws Exception {
        Usuario tutor = tutor(null, null);
        Throwable error = dobleEnvio(() ->
                credencialService.cargarCredencial(tutor, TipoCredencial.TITULO, "test/titulo.pdf"));

        assertThat(error).isInstanceOf(
                com.tinku.identidad.service.YaExisteCredencialPendienteException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identidad.credenciales_academicas "
                + "WHERE tutor_id = ? AND estado = 'PENDIENTE'", Integer.class, tutor.getId())).isEqualTo(1);
    }

    @Test
    void subirCapDosVecesALaVez_quedaUnoSoloPendiente() throws Exception {
        Usuario tutor = tutor(null, null);
        Throwable error = dobleEnvio(() ->
                certificadoService.cargarCap(tutor, "test/cap.pdf", LocalDate.now().minusDays(5)));

        assertThat(error).isInstanceOf(
                com.tinku.identidad.service.YaExisteCredencialPendienteException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identidad.certificados_antecedentes_penales "
                + "WHERE tutor_id = ? AND estado = 'PENDIENTE'", Integer.class, tutor.getId())).isEqualTo(1);
    }
}
