package com.tinku.admin.notificacion;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/** Barrido periódico del outbox para email (FASE2-03). Persistido en Quartz (A4). */
@Component
@DisallowConcurrentExecution
public class EnvioEmailsJob implements Job {

    private final EnvioEmailNotificacionesService envio;

    public EnvioEmailsJob(EnvioEmailNotificacionesService envio) {
        this.envio = envio;
    }

    @Override
    public void execute(JobExecutionContext context) {
        envio.procesarPendientes();
    }
}
