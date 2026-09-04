package com.tinku.identidad.service;

/**
 * FR-ID-013: máximo 5 perfiles de menor a cargo de un Adulto Responsable.
 */
public class LimiteMenoresAlcanzadoException extends RuntimeException {
    public LimiteMenoresAlcanzadoException() {
        super("Alcanzaste el máximo de 5 perfiles de menor a cargo.");
    }
}
