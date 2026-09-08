package com.tinku.reservas.service;

/** No existe la Reserva indicada (404). */
public class ReservaNoEncontradaException extends RuntimeException {
    public ReservaNoEncontradaException() {
        super("Reserva no encontrada.");
    }
}