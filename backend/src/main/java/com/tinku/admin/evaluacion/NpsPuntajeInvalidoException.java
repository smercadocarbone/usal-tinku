package com.tinku.admin.evaluacion;

/** T-TES-11b: puntaje fuera de 0..10 → 422. */
public class NpsPuntajeInvalidoException extends RuntimeException {
    public NpsPuntajeInvalidoException() {
        super("Elegí un número del 0 al 10.");
    }
}
