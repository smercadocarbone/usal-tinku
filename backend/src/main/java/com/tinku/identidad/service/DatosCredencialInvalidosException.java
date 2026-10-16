package com.tinku.identidad.service;

/** Datos de una credencial (ADR-M1-06) inválidos o rechazados por el triaje automático → 422, con el motivo para el Tutor. */
public class DatosCredencialInvalidosException extends RuntimeException {
    public DatosCredencialInvalidosException(String mensaje) {
        super(mensaje);
    }
}
