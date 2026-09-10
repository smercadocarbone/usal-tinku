package com.tinku.resumen.jobs;

import com.tinku.resumen.service.ResumenService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz (JobStore persistido, Artículo IV/X) — T-M6-07: recordatorio
 * unico a las 24hs de que el resumen esta disponible (Tabla_Tiempos, mismo
 * criterio que {@code RecordatorioCalificacionJob} de M7). One-shot: se agenda
 * una sola vez al generar, no se reprograma. Idempotente por la bandera
 * {@code recordatorio_pendiente} de la fila — {@link
 * ResumenService#enviarRecordatorio} solo envia la primera vez y las
 * infraestructuras de notificacion del piloto son log estructurado.
 */
@Component
@DisallowConcurrentExecution
public class RecordatorioResumenJob implements Job {

    public static final String PARAM_SESION_ID = "sesionId";

    private final ResumenService resumenService;

    public RecordatorioResumenJob(ResumenService resumenService) {
        this.resumenService = resumenService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_SESION_ID));
        resumenService.enviarRecordatorio(sesionId);
    }
}