package com.tinku.identidad.service;

/** El Tutor indicado en {@code GET /api/tutores/{id}} no existe (404). */
public class TutorNoEncontradoException extends RuntimeException {
    public TutorNoEncontradoException() {
        super("No encontramos a ese tutor.");
    }
}