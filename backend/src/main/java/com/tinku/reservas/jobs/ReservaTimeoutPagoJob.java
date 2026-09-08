package com.tinku.reservas.jobs;

import com.tinku.reservas.service.ReservaService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz (JobStore persistido, Artículo IV/X) que expira una Reserva en
 * {@code pendiente_pago} a los 15 minutos (FR-RES-020, T-M4-06; fila de
 * Tabla_Tiempos_Tinku.md). Se agenda por cada Reserva en su
 * {@code created_at + 15min} y se cancela explícitamente al confirmarse el pago
 * (mismo patrón que la cancelación del no-show del Plan M3 §3.2). Idempotente:
 * si la Reserva ya dejó de estar {@code pendiente_pago} (confirmada o cancelada),
 * no hace nada — la cancelación de una Reserva libera el horario porque la
 * EXCLUDE de V9 ignora el estado {@code cancelada}.
 */
@Component
@DisallowConcurrentExecution
public class ReservaTimeoutPagoJob implements Job {

    public static final String PARAM_RESERVA_ID = "reservaId";

    private final ReservaService reservaService;

    public ReservaTimeoutPagoJob(ReservaService reservaService) {
        this.reservaService = reservaService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        UUID reservaId = UUID.fromString(
                context.getMergedJobDataMap().getString(PARAM_RESERVA_ID));
        reservaService.expirarPorTimeoutPago(reservaId);
    }
}