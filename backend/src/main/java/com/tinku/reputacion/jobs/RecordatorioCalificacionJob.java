package com.tinku.reputacion.jobs;

import com.tinku.reputacion.model.Calificacion;
import com.tinku.reputacion.repository.CalificacionRepository;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz (JobStore persistido, Artículo IV/X) — T-M7-06: recordatorio
 * unico a las 24hs del fin de la Sesion para que el Estudiante califique al
 * Tutor (FR-REP-005). Idempotente: si ya existe la calificacion publica
 * (estudiante_a_tutor), no hace nada. Sin infraestructura de notificaciones en
 * el piloto, el recordatorio es un log estructurado (mismo criterio que
 * {@code AlertaSoporteProveedorLog}).
 */
@Component
@DisallowConcurrentExecution
public class RecordatorioCalificacionJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioCalificacionJob.class);

    public static final String PARAM_SESION_ID = "sesionId";

    private final CalificacionRepository calificacionRepo;

    public RecordatorioCalificacionJob(CalificacionRepository calificacionRepo) {
        this.calificacionRepo = calificacionRepo;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_SESION_ID));
        if (calificacionRepo.findBySesionIdAndDireccion(sesionId,
                Calificacion.DIR_ESTUDIANTE_A_TUTOR).isEmpty()) {
            log.info("RECORDATORIO_CALIFICACION sesionId={} — el Estudiante aun no califico "
                    + "al Tutor de la Sesion finalizada (FR-REP-005).", sesionId);
        }
    }
}