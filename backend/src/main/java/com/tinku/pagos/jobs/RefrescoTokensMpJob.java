package com.tinku.pagos.jobs;

import com.tinku.pagos.service.CuentasMpService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Renueva a diario los tokens de MercadoPago de los Tutores que vencen en menos de 30 días
 * (ADR-M5-02, Tabla de Tiempos). Persistido en Quartz; lo registra {@link RefrescoTokensMpJobRegistro}.
 */
@Component
@DisallowConcurrentExecution
public class RefrescoTokensMpJob implements Job {

    private final CuentasMpService cuentasMp;

    public RefrescoTokensMpJob(CuentasMpService cuentasMp) {
        this.cuentasMp = cuentasMp;
    }

    @Override
    public void execute(JobExecutionContext context) {
        cuentasMp.refrescarPorVencer();
    }
}
