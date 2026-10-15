package com.tinku.pagos.service;

/**
 * R-B B-5 (T1-10): MercadoPago respondió 404 para ese pago. No es una caída de MP (503): el
 * paymentId no existe o no es de esta cuenta. Extiende {@link MercadoPagoNoDisponibleException}
 * para que quien ya reintentaba ante un error de MP (webhook, conciliación) siga igual.
 */
public class PagoMpNoEncontradoException extends MercadoPagoNoDisponibleException {
    public PagoMpNoEncontradoException() {
        super("No encontramos ese pago en MercadoPago.");
    }
}
