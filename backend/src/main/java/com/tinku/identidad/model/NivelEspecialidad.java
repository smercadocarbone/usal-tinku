package com.tinku.identidad.model;

/**
 * Nivel máximo que un Tutor declara poder enseñar en una especialidad (ADR-M1-06, SPIKE §4.1).
 * El orden importa: un nivel cubre a los anteriores.
 */
public enum NivelEspecialidad {
    PRIMARIO,
    SECUNDARIO,
    UNIVERSITARIO;

    public boolean hasta(NivelEspecialidad tope) {
        return compareTo(tope) <= 0;
    }
}
