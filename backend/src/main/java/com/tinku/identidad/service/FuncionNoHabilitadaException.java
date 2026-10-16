package com.tinku.identidad.service;

/** Una función apagada por flag (p. ej. ADR-M1-06): para afuera, no existe (404). */
public class FuncionNoHabilitadaException extends RuntimeException {
    public FuncionNoHabilitadaException() {
        super("No encontramos lo que buscás.");
    }
}
