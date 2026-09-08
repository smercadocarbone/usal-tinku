package com.tinku.aula.jobs;

import com.tinku.aula.SesionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * T-M3-03 — creación diferida de la sala a {@code horario_reserva - 5min} (US-1,
 * Plan M3 §3.1, Tabla_Tiempos). Job puntual de Quartz programado por
 * {@link SesionService#programarSesion}; idempotente (si la sala ya existe o la
 * Reserva ya no está confirmada, no hace nada).
 */
@Component
@DisallowConcurrentExecution
public class CrearSalaJob implements Job {

    private final SesionService sesionService;

    public CrearSalaJob(SesionService sesionService) {
        this.sesionService = sesionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID sesionId = UUID.fromString(
                context.getMergedJobDataMap().getString(SesionService.SesionJobKeys.PARAM_SESION_ID));
        sesionService.crearSalaDiferida(sesionId);
    }
}