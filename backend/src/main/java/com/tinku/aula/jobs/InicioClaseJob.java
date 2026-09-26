package com.tinku.aula.jobs;

import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Horario de inicio — avisa a quien todavía no entró a la clase. Idempotente: si la Reserva ya no está confirmada, no avisa.
 * Job puntual de Quartz programado por {@link SesionService#programarSesion}.
 */
@Component
@DisallowConcurrentExecution
public class InicioClaseJob implements Job {

    private final SesionService sesionService;

    public InicioClaseJob(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(SesionService.SesionJobKeys.PARAM_SESION_ID));
        sesionService.avisarInicio(sesionId);
    }
}
