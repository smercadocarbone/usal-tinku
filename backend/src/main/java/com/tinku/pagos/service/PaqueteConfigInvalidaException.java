package com.tinku.pagos.service;

/** ADR-M5-03: configuración del paquete fuera de rango o sin tarifa. Se traduce a 422. */
public class PaqueteConfigInvalidaException extends RuntimeException {
    public PaqueteConfigInvalidaException(String mensaje) {
        super(mensaje);
    }
}
