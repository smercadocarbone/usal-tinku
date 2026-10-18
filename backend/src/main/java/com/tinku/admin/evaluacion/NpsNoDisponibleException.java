package com.tinku.admin.evaluacion;

/** T-TES-11b: cuenta Menor, o todavía sin una clase terminada → 403. */
public class NpsNoDisponibleException extends RuntimeException {
    public NpsNoDisponibleException() {
        super("Esta pregunta todavía no está disponible para tu cuenta.");
    }
}
