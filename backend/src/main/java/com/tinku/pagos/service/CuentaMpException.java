package com.tinku.pagos.service;

import org.springframework.http.HttpStatus;

/** Errores de la conexión de MercadoPago del Tutor (ADR-M5-02), con su código HTTP. */
public class CuentaMpException extends RuntimeException {

    private final HttpStatus status;

    public CuentaMpException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static CuentaMpException tutorSinCuenta() {
        return new CuentaMpException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Este tutor todavía no puede recibir pagos. Elegí otro tutor o probá más tarde.");
    }
}
