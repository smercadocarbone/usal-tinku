package com.tinku.reservas.jobs;

import com.tinku.reservas.service.PedidoPrevioService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz persistido que borra el adjunto del pedido previo 24 hs después del fin agendado
 * de la clase (ADR-M4-01, Tabla_Tiempos). Idempotente: sin archivo no hace nada; si la clase se
 * movió más adelante, se vuelve a agendar.
 */
@Component
@DisallowConcurrentExecution
public class BorradoAdjuntoPedidoJob implements Job {

    public static final String PARAM_RESERVA_ID = "reservaId";

    private final PedidoPrevioService pedidos;

    public BorradoAdjuntoPedidoJob(PedidoPrevioService pedidos) {
        this.pedidos = pedidos;
    }

    @Override
    public void execute(JobExecutionContext context) {
        pedidos.vencerArchivo(UUID.fromString(context.getMergedJobDataMap().getString(PARAM_RESERVA_ID)));
    }
}
