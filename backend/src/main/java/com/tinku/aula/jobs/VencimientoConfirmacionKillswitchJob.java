package com.tinku.aula.jobs;

import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * ADR-M3-07 §7: vence la pregunta de la rama adultos del kill-switch. Sin respuesta en el plazo
 * (Tabla de Tiempos) se resuelve como "No". Persistido en Quartz (AGENTS §1.3); idempotente.
 */
@Component
@DisallowConcurrentExecution
public class VencimientoConfirmacionKillswitchJob implements Job {

    private final SesionService sesionService;

    public VencimientoConfirmacionKillswitchJob(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(SesionService.SesionJobKeys.PARAM_SESION_ID));
        sesionService.vencerConfirmacionRamaAdultos(sesionId);
    }
}
