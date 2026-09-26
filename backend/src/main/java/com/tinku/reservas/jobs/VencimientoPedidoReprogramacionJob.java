package com.tinku.reservas.jobs;

import com.tinku.reservas.service.PedidoReprogramacionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Job de Quartz persistido a T-60 de la clase original (FR-RES-031, Tabla_Tiempos): si el pedido
 * de reprogramación del Tutor sigue pendiente, vence y la clase se cancela con devolución.
 * Idempotente: un pedido ya resuelto no hace nada.
 */
@Component
@DisallowConcurrentExecution
public class VencimientoPedidoReprogramacionJob implements Job {

    public static final String PARAM_PEDIDO_ID = "pedidoId";

    private final PedidoReprogramacionService pedidos;

    public VencimientoPedidoReprogramacionJob(PedidoReprogramacionService pedidos) {
        this.pedidos = pedidos;
    }

    @Override
    public void execute(JobExecutionContext context) {
        pedidos.vencer(UUID.fromString(context.getMergedJobDataMap().getString(PARAM_PEDIDO_ID)));
    }
}
