package com.tinku.pagos.jobs;

import com.tinku.pagos.service.ConciliacionPagosService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Barrido de conciliación de pagos (R2): cada 5 min, persistido en el job store de Quartz
 * (Constitución Art. IV). Lo registra {@link ConciliacionPagosJobRegistro}.
 */
@Component
@DisallowConcurrentExecution
public class ConciliacionPagosJob implements Job {

    private final ConciliacionPagosService conciliacion;

    public ConciliacionPagosJob(ConciliacionPagosService conciliacion) {
        this.conciliacion = conciliacion;
    }

    @Override
    public void execute(JobExecutionContext context) {
        conciliacion.barrer();
    }
}
