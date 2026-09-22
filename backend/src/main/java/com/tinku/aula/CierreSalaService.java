package com.tinku.aula;

import com.tinku.aula.jobs.CerrarSalaJob;
import com.tinku.aula.model.SesionAprendizaje;
import com.tinku.aula.repository.SesionAprendizajeRepository;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * Cierre de la sala de LiveKit cuando la sesión se corta (AUD-001, ADR-M3-03).
 *
 * <p><b>Fail-open deliberado:</b> a diferencia del resto del módulo, un fallo de
 * LiveKit (o de Quartz al agendar el reintento) NUNCA propaga: el corte en la base
 * (Alerta, suspensión, evento) tiene que persistirse igual. Si LiveKit no contesta,
 * se reintenta con un job persistido y backoff 5min/15min/1h (Tabla_Tiempos, fila
 * "Reintentos de cierre de sala LiveKit tras corte"). Agotados los 3 reintentos,
 * queda un log ERROR — no hay canal de alerta hasta AUD-014 (FASE 2).</p>
 */
@Service
public class CierreSalaService {

    private static final Logger log = LoggerFactory.getLogger(CierreSalaService.class);

    /** Tabla_Tiempos: reintentos de cierre de sala — 3, backoff 5/15/1h. */
    static final int MAX_REINTENTOS = 3;

    static final Duration[] BACKOFF =
            {Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(1)};

    private final SesionAprendizajeRepository sesionRepo;
    private final LiveKitService liveKitService;
    private final Scheduler scheduler;

    public CierreSalaService(SesionAprendizajeRepository sesionRepo,
                             LiveKitService liveKitService,
                             Scheduler scheduler) {
        this.sesionRepo = sesionRepo;
        this.liveKitService = liveKitService;
        this.scheduler = scheduler;
    }

    /** Intento inicial, desde el corte. Sin sala creada (antes de T-5) no hay nada que cerrar. */
    public void cerrar(SesionAprendizaje sesion) {
        if (sesion.getLivekitRoomId() == null) {
            return;
        }
        intentar(sesion.getId(), sesion.getLivekitRoomId(), 0);
    }

    /** Reintento {@code intento} (1..3) — lo invoca {@link CerrarSalaJob}. */
    public void ejecutarCierre(UUID sesionId, int intento) {
        SesionAprendizaje sesion = sesionRepo.findById(sesionId).orElse(null);
        if (sesion == null || sesion.getLivekitRoomId() == null) {
            cancelar(sesionId);
            return;
        }
        intentar(sesionId, sesion.getLivekitRoomId(), intento);
    }

    private void intentar(UUID sesionId, String sala, int reintentosHechos) {
        try {
            liveKitService.eliminarSala(sala);
        } catch (RuntimeException e) {
            if (reintentosHechos < MAX_REINTENTOS) {
                log.warn("No se pudo cerrar la sala {} de la sesion {} (intento {}): {}",
                        sala, sesionId, reintentosHechos, e.getMessage());
                programar(sesionId, reintentosHechos + 1,
                        Instant.now().plus(BACKOFF[reintentosHechos]));
            } else {
                log.error("Reintentos agotados: la sala {} de la sesion cortada {} puede seguir "
                        + "abierta en LiveKit. Requiere cierre manual.", sala, sesionId, e);
                cancelar(sesionId);
            }
            return;
        }
        cancelar(sesionId);
    }

    private void programar(UUID sesionId, int intento, Instant disparo) {
        JobDetail detail = JobBuilder.newJob(CerrarSalaJob.class)
                .withIdentity(jobKey(sesionId))
                .usingJobData(SesionService.SesionJobKeys.PARAM_SESION_ID, sesionId.toString())
                .usingJobData(CerrarSalaJob.PARAM_INTENTO, intento)
                .storeDurably()
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerCierre(sesionId))
                .forJob(detail)
                .startAt(Date.from(disparo))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withMisfireHandlingInstructionIgnoreMisfires())
                .build();
        try {
            // replace=true: cada reintento pisa al anterior con su nuevo número de intento.
            scheduler.scheduleJob(detail, Set.of(trigger), true);
        } catch (SchedulerException e) {
            // Fail-open (ADR-M3-03): no se revierte el corte por no poder agendar.
            log.error("No se pudo agendar el reintento de cierre de la sala de la sesion {}.",
                    sesionId, e);
        }
    }

    private void cancelar(UUID sesionId) {
        try {
            scheduler.deleteJob(jobKey(sesionId));
        } catch (SchedulerException e) {
            // Benigno: un disparo sobrante vuelve a cerrar una sala inexistente (404 = éxito).
        }
    }

    private static JobKey jobKey(UUID sesionId) {
        return new JobKey("cierre-sala-job-" + sesionId, SesionService.GRUPO_JOB);
    }

    /** Clave del trigger de reintento — tests. */
    public static TriggerKey triggerCierre(UUID sesionId) {
        return new TriggerKey("cierre-sala-trigger-" + sesionId, SesionService.GRUPO_JOB);
    }
}
