package com.tinku.pagos.service;

import com.tinku.pagos.jobs.ReembolsoAdicionalJob;
import com.tinku.pagos.model.EstadoReembolsoAdicional;
import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.ReembolsoParcialProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import org.quartz.JobBuilder;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Outbox del reembolso del adicional — BR-PAG-11 (T09) con outbox (R4): reembolso parcial por el monto exacto del adicional de resumen.
 *
 * <p>{@link #reembolsarAdicional} solo marca {@code PENDIENTE} y agenda {@link ReembolsoAdicionalJob}
 * en el job store persistido, en la misma transacción. Así una llamada HTTP a MercadoPago no corre
 * dentro del listener de la sesión, y un fallo no se pierde en un log.</p>
 *
 * <p>El job llama a MP con {@code X-Idempotency-Key: adicional-{transaccionId}} (un reintento tras un
 * timeout no devuelve dos veces). Si falla, reintenta con backoff 5/15/60 min (Tabla de Tiempos).
 * Agotados los reintentos, queda {@code FALLIDO} en la cola de Soporte Financiero, que puede
 * reintentar o registrar que lo devolvió por fuera.</p>
 *
 * <p>No aplica si no hubo adicional, si la Reserva ya se reembolsó entera, o si ya se pidió. En
 * Bypass no hay dinero real: se marca {@code HECHO} sin llamar a MP.</p>
 */
@Component
public class ReembolsoAdicionalOutbox {

    public static final String GRUPO_JOB = "m5-pagos";
    /** Tabla de Tiempos: reintentos del reembolso del adicional — 3, backoff 5/15/1h. */
    static final Duration[] BACKOFF = {Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1)};

    private static final Logger LOG = LoggerFactory.getLogger(ReembolsoAdicionalOutbox.class);

    private final TransaccionRepository transaccionRepo;
    private final ReembolsoParcialProveedor reembolsoParcial;
    private final Scheduler scheduler;
    private final AlertaSoporteProveedor alertaSoporte;

    public ReembolsoAdicionalOutbox(TransaccionRepository transaccionRepo,
                                                ReembolsoParcialProveedor reembolsoParcial,
                                                Scheduler scheduler,
                                                AlertaSoporteProveedor alertaSoporte) {
        this.transaccionRepo = transaccionRepo;
        this.reembolsoParcial = reembolsoParcial;
        this.scheduler = scheduler;
        this.alertaSoporte = alertaSoporte;
    }

    /** Marca el reembolso y lo agenda (lo llama el puerto {@link ReembolsoAdicionalResumenMercadoPago}). */
    @Transactional
    public void reembolsarAdicional(UUID reservaId) {
        Transaccion t = transaccionRepo.findByReservaId(reservaId).orElse(null);
        if (t == null || t.getAdicionalReembolsoEstado() != null || t.getAdicionalReembolsadoAt() != null
                || t.getEstado() == EstadoTransaccion.REEMBOLSADO
                || t.getMontoAdicionalResumen() == null
                || t.getMontoAdicionalResumen().compareTo(BigDecimal.ZERO) <= 0) {
            return; // idempotente: un segundo evento no pide dos reembolsos
        }
        if (t.isEnBypass()) {
            marcarHecho(t);
            return;
        }
        t.setAdicionalReembolsoEstado(EstadoReembolsoAdicional.PENDIENTE);
        transaccionRepo.save(t);
        agendar(t.getId(), Instant.now());
    }

    /** Lo invoca el job. Solo actúa sobre un reembolso {@code PENDIENTE}. */
    @Transactional
    public void ejecutar(UUID transaccionId) {
        Transaccion t = transaccionRepo.findById(transaccionId).orElse(null);
        if (t == null || t.getAdicionalReembolsoEstado() != EstadoReembolsoAdicional.PENDIENTE) {
            return;
        }
        if (t.getEstado() == EstadoTransaccion.REEMBOLSADO) {
            // Se reembolsó el total mientras tanto: el adicional ya volvió con él.
            t.setAdicionalReembolsoEstado(EstadoReembolsoAdicional.HECHO);
            transaccionRepo.save(t);
            return;
        }
        try {
            reembolsoParcial.reembolsarParcial(t.getMpPaymentId(), t.getMontoAdicionalResumen(),
                    "adicional-" + t.getId());
        } catch (RuntimeException e) {
            reintentarOAgotar(t, e);
            return;
        }
        marcarHecho(t);
    }

    /** Soporte Financiero: vuelve a intentar un {@code FALLIDO} (con reintentos nuevos). */
    @Transactional
    public Transaccion reintentarManual(UUID transaccionId) {
        Transaccion t = exigirFallido(transaccionId);
        t.setAdicionalReembolsoEstado(EstadoReembolsoAdicional.PENDIENTE);
        t.setAdicionalReembolsoIntentos(0);
        transaccionRepo.save(t);
        agendar(t.getId(), Instant.now());
        return t;
    }

    /** Soporte Financiero: lo devolvió por fuera (panel de MP) y deja constancia. */
    @Transactional
    public Transaccion resolverManual(UUID transaccionId, String nota) {
        Transaccion t = exigirFallido(transaccionId);
        t.setAdicionalReembolsoEstado(EstadoReembolsoAdicional.RESUELTO_MANUAL);
        t.setAdicionalReembolsoNota(nota);
        t.setAdicionalReembolsadoAt(Instant.now());
        return transaccionRepo.save(t);
    }

    private Transaccion exigirFallido(UUID transaccionId) {
        Transaccion t = transaccionRepo.findById(transaccionId)
                .orElseThrow(() -> new ReembolsoAdicionalNoEncontradoException());
        if (t.getAdicionalReembolsoEstado() != EstadoReembolsoAdicional.FALLIDO) {
            throw new ReembolsoAdicionalNoFallidoException();
        }
        return t;
    }

    private void reintentarOAgotar(Transaccion t, RuntimeException e) {
        int intentos = t.getAdicionalReembolsoIntentos() + 1;
        t.setAdicionalReembolsoIntentos(intentos);
        String error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        t.setAdicionalReembolsoError(error.length() > 300 ? error.substring(0, 300) : error);
        if (intentos <= BACKOFF.length) {
            transaccionRepo.save(t);
            agendar(t.getId(), Instant.now().plus(BACKOFF[intentos - 1]));
            LOG.warn("Reembolso del adicional falló (intento {}), se reintenta — transaccion={}", intentos, t.getId());
            return;
        }
        t.setAdicionalReembolsoEstado(EstadoReembolsoAdicional.FALLIDO);
        transaccionRepo.save(t);
        LOG.error("REEMBOLSO_ADICIONAL_FALLIDO transaccion={} reserva={} monto={} — a la cola de Soporte",
                t.getId(), t.getReservaId(), t.getMontoAdicionalResumen());
        alertaSoporte.notificarPagoSinConciliar(t.getReservaId(), t.getMpPaymentId(),
                "no se pudo devolver el adicional de resumen ($" + t.getMontoAdicionalResumen() + ")");
    }

    private void marcarHecho(Transaccion t) {
        t.setAdicionalReembolsoEstado(EstadoReembolsoAdicional.HECHO);
        t.setAdicionalReembolsadoAt(Instant.now());
        transaccionRepo.save(t);
        LOG.info("REEMBOLSO_ADICIONAL reservaId={} monto={}", t.getReservaId(), t.getMontoAdicionalResumen());
    }

    public static TriggerKey trigger(UUID transaccionId) {
        return TriggerKey.triggerKey("adicional-trigger-" + transaccionId, GRUPO_JOB);
    }

    private void agendar(UUID transaccionId, Instant cuando) {
        TriggerKey key = trigger(transaccionId);
        var nuevo = TriggerBuilder.newTrigger().withIdentity(key)
                .forJob(new JobKey("adicional-job-" + transaccionId, GRUPO_JOB))
                .startAt(Date.from(cuando))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
                .build();
        try {
            if (scheduler.checkExists(key)) {
                scheduler.rescheduleJob(key, nuevo);
                return;
            }
            // El job es durable: después del primer disparo sigue guardado sin trigger.
            if (scheduler.checkExists(new JobKey("adicional-job-" + transaccionId, GRUPO_JOB))) {
                scheduler.scheduleJob(nuevo);
                return;
            }
            scheduler.scheduleJob(JobBuilder.newJob(ReembolsoAdicionalJob.class)
                    .withIdentity("adicional-job-" + transaccionId, GRUPO_JOB)
                    .usingJobData(ReembolsoAdicionalJob.PARAM_TRANSACCION_ID, transaccionId.toString())
                    .storeDurably().build(), nuevo);
        } catch (SchedulerException e) {
            // Fail-closed: sin su disparo persistido, el reembolso quedaría PENDIENTE para siempre.
            throw new IllegalStateException("No se pudo agendar el reembolso del adicional " + transaccionId, e);
        }
    }
}
