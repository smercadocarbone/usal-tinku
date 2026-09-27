package com.tinku.reservas.evento;

import java.util.UUID;

/**
 * {@code paquete.cancelado} (v2.5, FR-RES-034 / FR-PAG-023, ADR-M5-03): quien pagó canceló el
 * paquete entero antes de la primera clase. M5 devuelve el pago completo de una sola vez. Cada
 * clase también emite su {@code reserva.cancelada} (M3 desagenda su Sesión), pero M5 las ignora
 * cuando el paquete está cancelado. {@code reservaId} es la ancla del pago.
 */
public class PaqueteCanceladoEvent extends ReservaEvento {

    private final UUID paqueteId;

    public PaqueteCanceladoEvent(Object source, UUID paqueteId, UUID reservaAnclaId) {
        super(source, "paquete.cancelado", reservaAnclaId);
        this.paqueteId = paqueteId;
    }

    public UUID getPaqueteId() {
        return paqueteId;
    }
}
