package com.tinku.pagos.service;

import com.tinku.pagos.model.EstadoTransaccion;
import com.tinku.pagos.model.Transaccion;
import com.tinku.pagos.port.AlertaSoporteProveedor;
import com.tinku.pagos.port.LiberacionProveedor;
import com.tinku.pagos.repository.TransaccionRepository;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Liberación automática del escrow al Tutor (Chunk M5-C, T-M5-05 y T-M5-06).
 *
 * <p><b>T-M5-05</b> — el job de Quartz {@code liberación} (JobStore persistido,
 * Artículo IV/X) se agenda a {@code liberar_at = sesion.finalizada + 24h}
 * (FR-PAG-002) y al dispararse ejecuta {@link #ejecutarLiberacion}. Guard de
 * FR-PAG-002: solo avanza una transacción {@code retenido_escrow} — un
 * {@code pausado_denuncia} (o ya {@code liberado}/{@code reembolsado}) es no-op,
 * que es el chequeo de {@code estado != pausado_denuncia} de T-M5-05.</p>
 *
 * <p><b>T-M5-06</b> — si la liberación ante MercadoPago falla (FR-PAG-007):
 * cada fallo incrementa {@code intentos_liberacion}, alerta al Soporte
 * Financiero en paralelo y reprograma el job con backoff {@code 5min → 15min →
 * 1h} (Tabla_Tiempos). Al fallar el tercer reintento (4° intento total), se
 * detienen los reintentos automáticos y la transacción queda en
 * {@code retenido_escrow} con {@code intentos_liberacion} agotados: esa es la
 * cola de intervención manual del admin (Spec M8, {@code pagos-fallidos}).</p>
 *
 * <p>Mismo patrón de scheduling que M3/M4 (SesionService / ReservaService):
 * jobs one-shot con misfire-ignore, cancelación explícita en cada salida del
 * escrow (reembolso, denuncia) y fail-closed al agendar — una liberación con
 * deadline no puede nacer sin su disparo.</p>
 */
@Service
public class LiberacionEscrowService {

    /** Grupo de jobs de liberación en el JOB_STORE. */
    public static final String GRUPO_JOB = "m5-pagos";

    /** Tabla_Tiempos: reintentos de liberación — 3, backoff 5/15/1h. */
    static final int MAX_REINTENTOS = 3;

    static final Duration[] BACKOFF =
            {Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1)};

    private final TransaccionRepository transaccionRepo;
    private final LiberacionProveedor liberacion;
    private final AlertaSoporteProveedor alerta;
    private final Scheduler scheduler;

    public LiberacionEscrowService(TransaccionRepository transaccionRepo,
                                   LiberacionProveedor liberacion,
                                   AlertaSoporteProveedor alerta,
                                   Scheduler scheduler) {
        this.transaccionRepo = transaccionRepo;
        this.liberacion = liberacion;
        this.alerta = alerta;
        this.scheduler = scheduler;
    }

    // -------------------------------------------------- programación (T-M5-05)

    /**
     * Agenda (o re-agenda) el job de liberación al instante dado. Idempotente:
     * si el trigger ya existe se re-programa (re-programar la cuenta de liberación
     * no deja disparos viejos colgando).
     */
    @Transactional
    public void programarLiberacion(UUID transaccionId, Instant disparo) {
        String nombre = nombreDe(transaccionId);
        TriggerKey triggerKey = TriggerKey.triggerKey(nombre, GRUPO_JOB);
        try {
            if (scheduler.checkExists(triggerKey)) {
                scheduler.rescheduleJob(triggerKey, trigger(triggerKey, disparo));
                return;
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException(
                    "No se pudo consultar el job de liberación de la transacción "
                            + transaccionId, e);
        }
        JobDetail detail = JobBuilder.newJob(com.tinku.pagos.jobs.LiberacionEscrowJob.class)
                .withIdentity(new JobKey("liberacion-job-" + transaccionId, GRUPO_JOB))
                .usingJobData(com.tinku.pagos.jobs.LiberacionEscrowJob.PARAM_TRANSACCION_ID,
                        transaccionId.toString())
                .storeDurably()
                .build();
        try {
            scheduler.scheduleJob(detail, trigger(triggerKey, disparo));
        } catch (SchedulerException e) {
            // Fail-closed: la transacción que fija liberar_at no puede quedar
            // sin su disparo en el JOB_STORE.
            throw new IllegalStateException(
                    "No se pudo agendar el job de liberación de la transacción "
                            + transaccionId, e);
        }
    }

    /** Quita el job de liberación (reembolso, denuncia, o éxito). Benigno por los
     * guards de estado: un disparo que quede ya sería no-op. */
    @Transactional
    public void cancelarLiberacion(UUID transaccionId) {
        String nombre = nombreDe(transaccionId);
        try {
            scheduler.unscheduleJob(TriggerKey.triggerKey(nombre, GRUPO_JOB));
            scheduler.deleteJob(new JobKey("liberacion-job-" + transaccionId, GRUPO_JOB));
        } catch (SchedulerException e) {
            // Benigno — ver javadoc: los guards de estado cubren el tiro muerto.
        }
    }

    // -------------------------------------------------- ejecución (T-M5-05/06)

    /**
     * Intenta liberar el escrow de una transacción (lo invoca {@code LiberacionEscrowJob}
     * al disparar, y {@code EscrowService} para la liberación inmediata del
     * no-show del Estudiante). Idempotente y fail-closed.
     */
    @Transactional
    public void ejecutarLiberacion(UUID transaccionId) {
        Transaccion transaccion = transaccionRepo.findById(transaccionId).orElse(null);
        if (transaccion == null || transaccion.getEstado() != EstadoTransaccion.RETENIDO_ESCROW) {
            return; // pausado_denuncia / liberado / reembolsado o inexistente → no-op
        }
        try {
            // Bypass (V22): la transacción nació sin dinero real — la liberación
            // es solo el cambio de estado local, jamás una llamada al proveedor.
            if (!transaccion.isEnBypass()) {
                liberacion.liberarAlTutor(transaccion);
            }
        } catch (RuntimeException e) {
            reintentarOAgotar(transaccion);
            return;
        }
        transaccion.setEstado(EstadoTransaccion.LIBERADO);
        transaccion.setLiberarAt(null);
        transaccionRepo.save(transaccion);
        cancelarLiberacion(transaccionId);
    }

    /**
     * FR-PAG-007: registro el fallo, alerto al Soporte Financiero en paralelo y
     * reprogramo con backoff; tras los 3 reintentos dejo de reprogramar (cola M8).
     */
    private void reintentarOAgotar(Transaccion transaccion) {
        int intentos = transaccion.getIntentosLiberacion() + 1;
        transaccion.setIntentosLiberacion(intentos);
        transaccionRepo.save(transaccion);

        alerta.notificarFalloLiberacion(transaccion);

        if (intentos <= MAX_REINTENTOS) {
            programarLiberacion(transaccion.getId(),
                    Instant.now().plus(BACKOFF[intentos - 1]));
        } else {
            // 4° fallo = los 3 reintentos (5/15/1h) fallaron: a la cola del admin.
            cancelarLiberacion(transaccion.getId());
        }
    }

    // ---------------------------------------------------------------- Quartz

    private static String nombreDe(UUID transaccionId) {
        return "liberacion-trigger-" + transaccionId;
    }

    /** Clave del trigger — visible para los tests (patrón de SesionService). */
    public static TriggerKey triggerLiberacion(UUID transaccionId) {
        return TriggerKey.triggerKey(nombreDe(transaccionId), GRUPO_JOB);
    }

    private static Trigger trigger(TriggerKey key, Instant disparo) {
        return TriggerBuilder.newTrigger()
                .withIdentity(key)
                .startAt(Date.from(disparo))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();
    }
}