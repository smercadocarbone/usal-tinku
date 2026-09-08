package com.tinku.aula.jobs;

import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-M3-04 — no-show a {@code horario_reserva + 10min} (US-7, Plan M3 §3.2,
 * Tabla_Tiempos). Decide quién no se presentó leyendo los joins grabados por el
 * webhook (V10) y emite {@code sesion.no_show_*} hacia M5. Se CANCELA
 * explícitamente si ambos llegan antes de T+10 (ver LiveKitWebhookService);
 * si aun así dispara, es idempotente: la Reserva ya no estará confirmada.
 */
@Component
@DisallowConcurrentExecution
public class NoShowJob implements Job {

    private final SesionService sesionService;

    public NoShowJob(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(SesionService.SesionJobKeys.PARAM_SESION_ID));
        sesionService.ejecutarNoShow(sesionId);
    }
}