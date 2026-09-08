package com.tinku.reservas.service;

import com.tinku.reservas.model.EstadoReserva;

/**
 * US-6/US-7: solo se cancela una Reserva {@code pendiente_pago} o {@code
 * confirmada}. En curso/finalizada/no-show se resuelve por otros flujos (M3); una
 * ya cancelada no se vuelve a cancelar.
 */
public class ReservaNoCancelableException extends RuntimeException {
    public ReservaNoCancelableException() {
        super("La Reserva no está en un estado cancelable (pendiente_pago o confirmada).");
    }
}