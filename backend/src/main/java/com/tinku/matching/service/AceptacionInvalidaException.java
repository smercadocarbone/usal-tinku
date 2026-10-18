package com.tinku.matching.service;

/** T-TES-11b: aceptación con un tutor que no se mostró o un tipo desconocido → 422. */
public class AceptacionInvalidaException extends RuntimeException {
    public AceptacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
