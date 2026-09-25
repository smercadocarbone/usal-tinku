package com.tinku.resumen.jobs;

import com.tinku.resumen.service.ResumenService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz persistido (AGENTS §1.3) — PT6/ADR-M3-04: tope de 24 hs del audio del resumen
 * (Tabla de Tiempos). Borra el audio si sigue ahí y, si el resumen nunca se generó, lo marca
 * fallido (→ reembolso del adicional, BR-PAG-11). Idempotente.
 */
@Component
@DisallowConcurrentExecution
public class PurgaAudioResumenJob implements Job {

    public static final String PARAM_SESION_ID = "sesionId";

    private final ResumenService resumenService;

    public PurgaAudioResumenJob(ResumenService resumenService) {
        this.resumenService = resumenService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(context.getMergedJobDataMap().getString(PARAM_SESION_ID));
        resumenService.purgarAudio(sesionId);
    }
}
