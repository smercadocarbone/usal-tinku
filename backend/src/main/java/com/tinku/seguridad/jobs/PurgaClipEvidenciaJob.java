package com.tinku.seguridad.jobs;

import com.tinku.seguridad.repository.AlertaSeguridadRepository;
import com.tinku.identidad.port.Almacenamiento;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BR-KS-02 (AUD-021): al vencer la retención del clip (30 días desde la resolución
 * de la Alerta) se borra el archivo y la referencia. Job one-shot de Quartz persistido,
 * agendado al resolver (Artículo IV/X). Idempotente: sin clip, no hace nada.
 */
@Component
@DisallowConcurrentExecution
public class PurgaClipEvidenciaJob implements Job {

    public static final String PARAM_ALERTA_ID = "alertaId";

    private final AlertaSeguridadRepository alertaRepo;
    private final Almacenamiento almacenamiento;

    public PurgaClipEvidenciaJob(AlertaSeguridadRepository alertaRepo, Almacenamiento almacenamiento) {
        this.alertaRepo = alertaRepo;
        this.almacenamiento = almacenamiento;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        UUID alertaId = UUID.fromString(context.getMergedJobDataMap().getString(PARAM_ALERTA_ID));
        alertaRepo.findById(alertaId).ifPresent(alerta -> {
            if (alerta.getClipUrl() == null) {
                return;
            }
            almacenamiento.borrar(alerta.getClipUrl());
            alerta.setClipUrl(null);
            alertaRepo.save(alerta);
        });
    }
}
