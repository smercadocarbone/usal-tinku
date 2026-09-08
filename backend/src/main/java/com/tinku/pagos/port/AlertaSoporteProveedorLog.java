package com.tinku.pagos.port;

import com.tinku.pagos.model.Transaccion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * STUB de {@link AlertaSoporteProveedor} (Chunk M5-C): sin M8 el alerta no tiene
 * cola a la que llegar, pero FR-PAG-007 exige que la liberación no avance en
 * silencio — así que al menos queda auditado en log. M8 reemplaza este bean por
 * la notificación real al Soporte Financiero (Spec M8).
 */
@Component
public class AlertaSoporteProveedorLog implements AlertaSoporteProveedor {

    private static final Logger log = LoggerFactory.getLogger(AlertaSoporteProveedorLog.class);

    @Override
    public void notificarFalloLiberacion(Transaccion transaccion) {
        log.warn("Liberación de escrow fallando ante MercadoPago — transaccion={}, "
                + "reservaId={}, intentos_liberacion={} (FR-PAG-007). M8: cola de "
                + "Soporte Financiero pendiente.",
                transaccion.getId(), transaccion.getReservaId(),
                transaccion.getIntentosLiberacion());
    }
}