package com.tinku.identidad.service;

/** Decisión del Admin sobre una credencial que no se puede aplicar (ADR-M1-06) → 422. */
public class RevisionCredencialInvalidaException extends RuntimeException {
    public RevisionCredencialInvalidaException(String mensaje) {
        super(mensaje);
    }
}
