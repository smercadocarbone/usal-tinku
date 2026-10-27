package com.tinku.aula;

/** Una acción de la Sesión que solo puede hacer el Tutor de la Reserva (403). */
public class SoloTutorException extends RuntimeException {

    /** {@code accion}: lo que sigue a "puede", p. ej. "dejar pasar al alumno". */
    public SoloTutorException(String accion) {
        super("Solo el tutor de la clase puede " + accion + ".");
    }
}
