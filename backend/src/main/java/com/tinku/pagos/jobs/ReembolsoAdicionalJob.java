package com.tinku.pagos.jobs;

import com.tinku.pagos.service.ReembolsoAdicionalOutbox;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reembolso del adicional de resumen (R4, BR-PAG-11), persistido en Quartz. Lo agenda y reagenda
 * con backoff {@link ReembolsoAdicionalOutbox}; idempotente por el estado del outbox.
 */
@Component
@DisallowConcurrentExecution
public class ReembolsoAdicionalJob implements Job {

    public static final String PARAM_TRANSACCION_ID = "transaccionId";

    private final ReembolsoAdicionalOutbox reembolso;

    public ReembolsoAdicionalJob(ReembolsoAdicionalOutbox reembolso) {
        this.reembolso = reembolso;
    }

    @Override
    public void execute(JobExecutionContext context) {
        reembolso.ejecutar(UUID.fromString(context.getMergedJobDataMap().getString(PARAM_TRANSACCION_ID)));
    }
}
