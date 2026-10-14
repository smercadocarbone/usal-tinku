package com.tinku.aula;

/**
 * FR-AULA-010 / US-6bis (409): con un Menor en la clase no se entrega token si el clasificador
 * del navegador no cargó (ADR-M3-07 §6, fail-closed).
 */
public class ClasificadorNoListoException extends RuntimeException {
    public ClasificadorNoListoException() {
        super("Para entrar a una clase con un menor, la protección del aula tiene que estar activa. "
                + "Probá con otro navegador o dispositivo.");
    }
}
