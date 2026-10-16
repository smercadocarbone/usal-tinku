package com.tinku.identidad.service;

/** Datos de especialidades inválidos (ADR-M1-06) → 422. */
public class EspecialidadInvalidaException extends RuntimeException {
    public EspecialidadInvalidaException(String mensaje) {
        super(mensaje);
    }
}
