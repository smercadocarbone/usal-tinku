package com.tinku.resumen.jobs;

import com.tinku.resumen.service.ResumenService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz (JobStore persistido, Artículo IV/X) — T-M6-06: intenta la
 * generacion del resumen, tanto en el disparo inicial (a los ~30s de
 * {@code sesion.finalizada}) como en cada reintento del backoff 5/15/1h
 * (FR-SUM-007, mismo patron que {@code LiberacionEscrowJob} de M5). Lo agenda
 * {@link ResumenService#programarReintento}. Idempotente: los guards de estado
 * de {@link ResumenService#ejecutarGenerar} hacen que un disparo duplicado no
 * genere dos veces.
 */
@Component
@DisallowConcurrentExecution
public class ReintentoResumenJob implements Job {

    public static final String PARAM_SESION_ID = "sesionId";

    private final ResumenService resumenService;

    public ReintentoResumenJob(ResumenService resumenService) {
        this.resumenService = resumenService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_SESION_ID));
        resumenService.ejecutarGenerar(sesionId);
    }
}