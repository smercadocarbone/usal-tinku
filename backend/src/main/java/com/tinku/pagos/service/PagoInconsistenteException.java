package com.tinku.pagos.service;

import java.util.UUID;

/**
 * El webhook firmó y el pago está aprobado, pero algo de la reconciliación contra
 * la Reserva no cierra (el monto pagado no coincide con el precio congelado, o el
 * monto no viene en la respuesta del provider). Fail-closed: no se confirma la
 * Reserva ni se crea el escrow. 500 con retry del provider (MP reintenta) para que
 * nunca se pierda en silencio.
 */
public class PagoInconsistenteException extends RuntimeException {

    private final String detalle;

    public PagoInconsistenteException(UUID reservaId, String mpPaymentId) {
        // El mensaje puede llegar a la pantalla (confirmación al volver de MercadoPago):
        // sin ids ni jerga. Los ids van en getDetalle() para el log.
        super("El monto pagado no coincide con el precio de la clase, así que no la confirmamos. "
                + "Escribinos a soporte y lo revisamos.");
        this.detalle = "Pago " + mpPaymentId + " no coincide con el precio congelado de la Reserva "
                + reservaId + " — no se confirma (fail-closed).";
    }

    public String getDetalle() {
        return detalle;
    }
}