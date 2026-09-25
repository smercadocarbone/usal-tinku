package com.tinku.pagos.service;

import com.tinku.resumen.port.ReembolsoAdicionalResumen;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * BR-PAG-11 (T09): puerto que M6 usa cuando el resumen falla. Delega en
 * {@link ReembolsoAdicionalOutbox}, que persiste el pedido y lo ejecuta con reintentos (R4).
 */
@Component
public class ReembolsoAdicionalResumenMercadoPago implements ReembolsoAdicionalResumen {

    private final ReembolsoAdicionalOutbox outbox;

    public ReembolsoAdicionalResumenMercadoPago(ReembolsoAdicionalOutbox outbox) {
        this.outbox = outbox;
    }

    @Override
    public void reembolsarAdicional(UUID reservaId) {
        outbox.reembolsarAdicional(reservaId);
    }
}
