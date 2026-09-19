package com.tinku.reservas.service;

/** T-M4-12: {@code duracionMinutos} del picker de horarios debe ser positivo. */
public class DuracionMinutosInvalidaException extends RuntimeException {
    public DuracionMinutosInvalidaException(String mensaje) {
        super(mensaje);
    }
}
