package com.tinku.pagos.jobs;

import com.tinku.pagos.service.LiberacionEscrowService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz (JobStore persistido, Artículo IV/X) que libera el escrow al
 * vencerse {@code liberar_at} (T-M5-05, FR-PAG-002/007; fila "Liberación de
 * escrow — 24hs post-sesión" de Tabla_Tiempos). Lo agenda
 * {@link LiberacionEscrowService} al recibir {@code sesion.finalizada}; los
 * reintentos de FR-PAG-007 re-programan este mismo job con backoff. Idempotente:
 * los guards de estado de {@link LiberacionEscrowService#ejecutarLiberacion}
 * hacen que un disparo sobre una transacción ya resuelta/pausada no haga nada.
 */
@Component
@DisallowConcurrentExecution
public class LiberacionEscrowJob implements Job {

    public static final String PARAM_TRANSACCION_ID = "transaccionId";

    private final LiberacionEscrowService liberacionEscrowService;

    public LiberacionEscrowJob(LiberacionEscrowService liberacionEscrowService) {
        this.liberacionEscrowService = liberacionEscrowService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID transaccionId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_TRANSACCION_ID));
        liberacionEscrowService.ejecutarLiberacion(transaccionId);
    }
}