package com.tinku.identidad.jobs;

import com.tinku.identidad.service.CertificadoService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Job de Quartz (persistido en JDBC, Constitución Art. IV/X) que corre
 * diariamente: vence los Certificados de Antecedentes Penales cuyo
 * {@code vence_at} ya pasó; el Tutor pierde la habilitación para menores y se cancelan
 * sus clases con menores (FR-ID-025, PT10). NO toca {@code activoParaMatching} (T02 TR2).
 * Delega en {@link CertificadoService#marcarVencidos()}, idempotente. Lo registra
 * {@code CapVencimientoJobRegistro}.
 */
@Component
@DisallowConcurrentExecution
public class CapVencimientoJob implements Job {

    private final CertificadoService certificadoService;

    public CapVencimientoJob(CertificadoService certificadoService) {
        this.certificadoService = certificadoService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        certificadoService.marcarVencidos();
    }
}
