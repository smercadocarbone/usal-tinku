package com.tinku.pagos.service;

/** No hay credenciales de MercadoPago configuradas (503 al intentar usar el servicio). */
public class MercadoPagoNoConfiguradoException extends RuntimeException {
    public MercadoPagoNoConfiguradoException() {
        super("MercadoPago no está configurado. Definí MP_ACCESS_TOKEN "
                + "(tinku.mercadopago.access-token) para generar preferencias de pago.");
    }
}