package com.tinku.aula.jobs;

import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-M3-05 — corte automático a {@code fin_agendado + 5min} (US-8, Plan M3 §3.5,
 * Tabla_Tiempos): si nadie presionó «Finalizar», cierra la Sesión igual. El
 * fin agendado se calcula al programarla (horario + duración de la franja que
 * cubre la Reserva). Idempotente: si la sesión ya se cerró, no cambia nada.
 */
@Component
@DisallowConcurrentExecution
public class CorteAutomaticoJob implements Job {

    private final SesionService sesionService;

    public CorteAutomaticoJob(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(SesionService.SesionJobKeys.PARAM_SESION_ID));
        sesionService.ejecutarCorteAutomatico(sesionId);
    }
}