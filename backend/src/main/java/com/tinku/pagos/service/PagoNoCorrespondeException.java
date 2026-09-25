package com.tinku.pagos.service;

/** El pago que informa el retorno de MercadoPago no es de esta reserva (422). */
public class PagoNoCorrespondeException extends RuntimeException {
    public PagoNoCorrespondeException() {
        super("Ese pago no corresponde a esta reserva.");
    }
}
