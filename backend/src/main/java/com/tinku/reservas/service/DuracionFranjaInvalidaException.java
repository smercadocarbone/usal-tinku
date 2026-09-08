package com.tinku.reservas.service;

/** La franja debe durar de 30 a 180 minutos (FR-RES-024, Tabla_Tiempos). */
public class DuracionFranjaInvalidaException extends RuntimeException {
    public DuracionFranjaInvalidaException(String mensaje) {
        super(mensaje);
    }
}