package com.tinku.identidad.jobs;

import com.tinku.identidad.service.CertificadoService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Job de Quartz (persistido en JDBC, Constitución Art. IV/X) que corre
 * diariamente: vence los Certificados de Antecedentes Penales cuyo
 * {@code vence_at} ya pasó y suspende {@code activoParaMatching} del Tutor
 * (FR-ID-025, T-M1-17). Delega en {@link CertificadoService#marcarVencidos()},
 * idempotente. La definición del JobDetail/Trigger vive en {@code QuartzConfig}.
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
