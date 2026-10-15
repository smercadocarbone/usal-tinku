package com.tinku.pagos.service;

/** La Reserva ya no está {@code pendiente_pago} (ya confirmada/cancelada), así
 * que no se puede generar una preferencia nueva (422). */
public class PreferenciaNoDisponibleException extends RuntimeException {
    public PreferenciaNoDisponibleException() {
        super("Esta clase ya no está pendiente de pago.");
    }
}