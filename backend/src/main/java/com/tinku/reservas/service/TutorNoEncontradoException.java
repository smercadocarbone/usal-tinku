package com.tinku.reservas.service;

/** El tutor indicado en una Reserva directa no existe. */
public class TutorNoEncontradoException extends RuntimeException {
    public TutorNoEncontradoException() {
        super("El Tutor indicado no existe.");
    }
}