package com.tinku.reservas.service;

/** R-D D-1: la franja no existe, ya se quitó o es de otro Tutor (no se distingue: 404). */
public class FranjaNoEncontradaException extends RuntimeException {
    public FranjaNoEncontradaException() {
        super("No encontramos esa franja.");
    }
}
