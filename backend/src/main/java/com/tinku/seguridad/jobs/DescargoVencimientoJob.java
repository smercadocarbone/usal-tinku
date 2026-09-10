package com.tinku.seguridad.jobs;

import com.tinku.seguridad.service.DenunciaService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-M9-03 — vencimiento del descargo (48hs, Tabla_Tiempos): al dispararse se
 * calcula el SLA de resolución (5 días hábiles, FR-SEC-010) y se agenda el
 * {@code SlaResolucionJob}. Desencadenado por {@code DenunciaService} al entrar
 * en {@code en_revision}; idempotente (si ya vencido/resuelto, no hace nada).
 */
@Component
@DisallowConcurrentExecution
public class DescargoVencimientoJob implements Job {

    public static final String PARAM_DENUNCIA_ID = "denunciaId";

    private final DenunciaService denunciaService;

    public DescargoVencimientoJob(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID denunciaId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_DENUNCIA_ID));
        denunciaService.alVencerDescargo(denunciaId);
    }
}