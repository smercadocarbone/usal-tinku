package com.tinku.identidad.jobs;

import com.tinku.identidad.service.VerificacionFirmaCapService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Paso de la cola de verificación de firmas del CAP (ADR-M1-10), persistido en el job store
 * JDBC de Quartz (Art. IV/X). Sin ejecuciones concurrentes: nunca hay dos pasos a la vez, así
 * que Tinku nunca tiene más de un pedido abierto en el validador. Lo registra
 * {@link CapFirmaColaJobRegistro}.
 */
@Component
@DisallowConcurrentExecution
public class CapFirmaColaJob implements Job {

    private final VerificacionFirmaCapService verificacion;

    public CapFirmaColaJob(VerificacionFirmaCapService verificacion) {
        this.verificacion = verificacion;
    }

    @Override
    public void execute(JobExecutionContext context) {
        verificacion.procesarSiguiente();
    }
}
