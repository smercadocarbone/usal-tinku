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

    public PagoInconsistenteException(UUID reservaId, String mpPaymentId) {
        super("Pago " + mpPaymentId + " no coincide con el precio congelado de la Reserva "
                + reservaId + " — no se confirma (fail-closed).");
    }
}