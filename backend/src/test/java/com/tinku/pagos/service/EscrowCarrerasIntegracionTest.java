package com.tinku.pagos.service;

import com.tinku.identidad.model.Usuario;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.port.ReembolsoProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import com.tinku.reservas.repository.ReservaRepository;
import com.tinku.seguridad.evento.DenunciaRegistradaEvent;
import com.tinku.soporte.IntegracionBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Carreras sobre el escrow con hilos reales contra Postgres: dos operaciones que mueven el
 * estado de la MISMA {@code Transaccion} al mismo tiempo no pueden pisarse (lost update).
 */
class EscrowCarrerasIntegracionTest extends IntegracionBase {

    @Autowired LiberacionEscrowService liberacionEscrow;
    @Autowired EscrowService escrowService;
    @Autowired TransaccionRepository transaccionRepo;
    @Autowired ReservaRepository reservaRepo;

    @MockitoBean LiberacionProveedor liberacion;
    @MockitoBean ReembolsoProveedor reembolso;
    @MockitoBean AlertaSoporteProveedor alerta;
    @MockitoBean com.tinku.pagos.port.ReembolsoParcialProveedor reembolsoParcial;
    @MockitoBean com.tinku.pagos.port.MercadoPagoClient mercadopago;
    @Autowired org.springframework.transaction.PlatformTransactionManager txManager;
    @Autowired com.tinku.reservas.service.ReservaService reservaService;

    private Transaccion escrowRetenido() {
        Usuario alumno = adulto(false);
        Reserva reserva = new Reserva();
        reserva.setPagador(alumno);
        reserva.setBeneficiario(alumno);
        reserva.setTutor(tutor(null, null));
        reserva.setHorario(Instant.now().minusSeconds(26 * 3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.FINALIZADA);
        reservaRepo.saveAndFlush(reserva);

        Transaccion t = new Transaccion();
        t.setReservaId(reserva.getId());
        t.setMpPaymentId("pago-carrera-" + UUID.randomUUID());
        t.setMontoBruto(BigDecimal.valueOf(15000));
        t.setComisionPlataforma(new BigDecimal("4050.00"));
        return transaccionRepo.saveAndFlush(t);
    }

    /**
     * El job de liberación (24 hs después de la clase) consulta el pago a MercadoPago mientras
     * el alumno presenta una denuncia. Sin lock de fila, la denuncia commitea
     * {@code pausado_denuncia} y el job, al terminar, la pisa con {@code liberado}: el alumno
     * pierde la pausa y, con ella, el reembolso de una denuncia escalada.
     */
    @Test
    void liberacionYDenunciaALaVez_laPausaDeLaDenunciaNoSePierde() throws Exception {
        Transaccion t = escrowRetenido();
        CountDownLatch enMercadoPago = new CountDownLatch(1);
        CountDownLatch seguir = new CountDownLatch(1);
        doAnswer(inv -> {
            enMercadoPago.countDown();
            seguir.await(20, TimeUnit.SECONDS);
            return null;
        }).when(liberacion).liberarAlTutor(any());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> job = pool.submit(() -> liberacionEscrow.ejecutarLiberacion(t.getId()));
            assertThat(enMercadoPago.await(20, TimeUnit.SECONDS)).isTrue();

            Future<?> denuncia = pool.submit(() -> escrowService.onDenunciaRegistrada(
                    new DenunciaRegistradaEvent(this, t.getReservaId())));
            // Con el lock, la denuncia espera al job; sin él, termina enseguida.
            boolean denunciaTerminoAntes;
            try {
                denuncia.get(2, TimeUnit.SECONDS);
                denunciaTerminoAntes = true;
            } catch (java.util.concurrent.TimeoutException e) {
                denunciaTerminoAntes = false;
            }
            seguir.countDown();
            job.get(20, TimeUnit.SECONDS);
            denuncia.get(20, TimeUnit.SECONDS);

            EstadoTransaccion fin = transaccionRepo.findById(t.getId()).orElseThrow().getEstado();
            if (denunciaTerminoAntes) {
                // La denuncia commiteó la pausa ANTES de que el job terminara: tiene que quedar.
                assertThat(fin).isEqualTo(EstadoTransaccion.PAUSADO_DENUNCIA);
            } else {
                // Serializado: el job liberó primero y la denuncia lo encontró ya liberado.
                assertThat(fin).isEqualTo(EstadoTransaccion.LIBERADO);
            }
        } finally {
            seguir.countDown();
            pool.shutdownNow();
        }
    }

    /**
     * Doble envío del reembolso parcial de Soporte Financiero (FR-PAG-010): el endpoint no
     * registraba nada ni pasaba una clave de idempotencia, así que dos POST iguales seguidos
     * devolvían dos veces la misma plata al alumno.
     */
    @Test
    void reembolsoParcialDelAdmin_dosVecesSeguidas_devuelveUnaSolaVez() throws Exception {
        Transaccion t = escrowRetenido();
        Usuario soporte = adulto(false);
        hacerAdmin(soporte, "SOPORTE_FINANCIERO");
        String tk = token(soporte);
        String cuerpo = objectMapper.writeValueAsString(java.util.Map.of("monto", 6000));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/financiero/transacciones/" + t.getId() + "/reembolso-parcial")
                        .header("Authorization", bearer(tk))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/admin/financiero/transacciones/" + t.getId() + "/reembolso-parcial")
                        .header("Authorization", bearer(tk))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isUnprocessableEntity());

        org.mockito.Mockito.verify(reembolsoParcial, org.mockito.Mockito.times(1))
                .reembolsarParcial(org.mockito.ArgumentMatchers.anyString(), any(), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(reembolsoParcial, org.mockito.Mockito.never())
                .reembolsarParcial(org.mockito.ArgumentMatchers.anyString(), any());
    }

    private Reserva pendienteDePago(Usuario alumno) {
        Reserva reserva = new Reserva();
        reserva.setPagador(alumno);
        reserva.setBeneficiario(alumno);
        reserva.setTutor(tutor(null, null));
        reserva.setHorario(Instant.now().plusSeconds(3 * 24 * 3600));
        reserva.setPrecio(BigDecimal.valueOf(15000));
        reserva.setEstado(EstadoReserva.PENDIENTE_PAGO);
        return reservaRepo.saveAndFlush(reserva);
    }

    /**
     * Corre {@code cancelacion} y la frena antes del commit (ya leyó pendiente_pago) y,
     * mientras tanto, llega el webhook del pago aprobado. Sin lock de la
     * Reserva, el webhook confirma y crea el escrow, y la cancelación lo pisa: la Reserva queda
     * cancelada sin evento y la plata del alumno retenida para siempre.
     */
    private void cancelacionYPagoALaVez(UUID reservaId, java.util.concurrent.Callable<?> cancelacion)
            throws Exception {
        String mpPaymentId = "pago-carrera-" + UUID.randomUUID();
        org.mockito.Mockito.when(mercadopago.getPago(org.mockito.ArgumentMatchers.eq(mpPaymentId), any()))
                .thenReturn(new com.tinku.pagos.port.MercadoPagoClient.PagoMercadoPago(
                        mpPaymentId, "approved", reservaId.toString(), new BigDecimal("15000")));
        CountDownLatch enFreno = new CountDownLatch(1);
        CountDownLatch webhookTermino = new CountDownLatch(1);
        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(txManager);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // La cancelación corre en una transacción que se frena antes del commit.
            Future<?> cancela = pool.submit(() -> tx.execute(st -> {
                try {
                    cancelacion.call();
                    enFreno.countDown();
                    webhookTermino.await(3, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                return null;
            }));
            assertThat(enFreno.await(20, TimeUnit.SECONDS)).isTrue();
            Future<?> webhook = pool.submit(() -> {
                try {
                    escrowService.procesarPagoAprobado(mpPaymentId);
                } finally {
                    webhookTermino.countDown();
                }
                return null;
            });
            cancela.get(30, TimeUnit.SECONDS);
            webhook.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        EstadoReserva estado = reservaRepo.findById(reservaId).orElseThrow().getEstado();
        java.util.Optional<Transaccion> escrow = transaccionRepo.findByReservaId(reservaId);
        if (estado == EstadoReserva.CANCELADA) {
            // Cancelada: si se cobró, se devolvió. Nunca plata retenida de una clase que no existe.
            assertThat(escrow.map(Transaccion::getEstado).orElse(EstadoTransaccion.REEMBOLSADO))
                    .isEqualTo(EstadoTransaccion.REEMBOLSADO);
        } else {
            assertThat(estado).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(escrow).isPresent();
        }
    }

    @Test
    void cancelarMientrasLlegaElPago_laPlataNoQuedaRetenida() throws Exception {
        Usuario alumno = adulto(false);
        Reserva reserva = pendienteDePago(alumno);
        cancelacionYPagoALaVez(reserva.getId(), () -> reservaService.cancelar(alumno, reserva.getId()));
    }

    @Test
    void vencerPorTimeoutMientrasLlegaElPago_laPlataNoQuedaRetenida() throws Exception {
        Reserva reserva = pendienteDePago(adulto(false));
        cancelacionYPagoALaVez(reserva.getId(), () -> {
            reservaService.expirarPorTimeoutPago(reserva.getId());
            return null;
        });
    }
}
