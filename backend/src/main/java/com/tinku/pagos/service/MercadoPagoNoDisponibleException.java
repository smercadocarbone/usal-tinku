package com.tinku.pagos.service;

/** MercadoPago rechazo o no respondio (503) — el backend no fabrica un link de
 * pago como si MercadoPago existiese (misma filosofia que MatchingNoDisponibleException). */
public class MercadoPagoNoDisponibleException extends RuntimeException {
    public MercadoPagoNoDisponibleException() {
        super("MercadoPago no está disponible en este momento; reintentá más tarde.");
    }
}