package com.tinku.seguridad.jobs;

import com.tinku.seguridad.service.DenunciaService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-M9-03 — vencimiento del SLA de resolución (5 días hábiles tras el descargo,
 * FR-SEC-010): si la Denuncia sigue en {@code en_revision}, marca
 * {@code prioridad_alta} para la cola de M8. NO se auto-resuelve en favor de
 * ninguna parte (Plan_M9 §2.3) — solo fuerza visibilidad. El escrow pausado
 * nunca queda indefinido (FR-SEC-010): la escalada es la señal de que el Admin
 * excedió el plazo.
 */
@Component
@DisallowConcurrentExecution
public class SlaResolucionJob implements Job {

    public static final String PARAM_DENUNCIA_ID = "denunciaId";

    private final DenunciaService denunciaService;

    public SlaResolucionJob(DenunciaService denunciaService) {
        this.denunciaService = denunciaService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID denunciaId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_DENUNCIA_ID));
        denunciaService.alVencerSla(denunciaId);
    }
}