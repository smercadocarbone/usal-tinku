package com.tinku.aula.jobs;

import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-24h — recordatorio de la clase a los participantes (Tabla_Tiempos). Idempotente: si la Reserva ya no está confirmada, no avisa.
 * Job puntual de Quartz programado por {@link SesionService#programarSesion}.
 */
@Component
@DisallowConcurrentExecution
public class RecordatorioClaseJob implements Job {

    private final SesionService sesionService;

    public RecordatorioClaseJob(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(SesionService.SesionJobKeys.PARAM_SESION_ID));
        sesionService.enviarRecordatorio(sesionId);
    }
}
