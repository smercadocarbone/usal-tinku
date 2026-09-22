package com.tinku.aula.jobs;

import com.tinku.aula.CierreSalaService;
import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reintento persistido del cierre de la sala de LiveKit tras un corte (AUD-001,
 * ADR-M3-03). Lo agenda {@link CierreSalaService} cuando LiveKit no contesta;
 * idempotente (cerrar una sala que ya no existe es éxito).
 */
@Component
@DisallowConcurrentExecution
public class CerrarSalaJob implements Job {

    public static final String PARAM_INTENTO = "intento";

    private final CierreSalaService cierreSalaService;

    public CerrarSalaJob(CierreSalaService cierreSalaService) {
        this.cierreSalaService = cierreSalaService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        var datos = context.getMergedJobDataMap();
        cierreSalaService.ejecutarCierre(
                UUID.fromString(datos.getString(SesionService.SesionJobKeys.PARAM_SESION_ID)),
                datos.getInt(PARAM_INTENTO));
    }
}
