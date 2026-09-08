package com.tinku.reservas.service;

/** US-6/US-7: solo quien pagó la Reserva o el Tutor pueden cancelarla. */
public class NoPuedeCancelarReservaException extends RuntimeException {
    public NoPuedeCancelarReservaException() {
        super("Solo quien pagó la Reserva o el Tutor pueden cancelarla.");
    }
}