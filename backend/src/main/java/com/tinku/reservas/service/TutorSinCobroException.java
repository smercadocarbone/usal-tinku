package com.tinku.reservas.service;

/** ADR-M5-02: el Tutor todavía no conectó su MercadoPago y no puede cobrar (422). */
public class TutorSinCobroException extends RuntimeException {
    public TutorSinCobroException() {
        super("Este tutor todavía no puede recibir pagos. Elegí otro tutor o probá más tarde.");
    }
}
