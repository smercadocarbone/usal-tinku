package com.tinku.pagos.service;

/**
 * No hay credenciales de MercadoPago configuradas (503 al intentar usar el servicio).
 * Configuración: {@code MP_ACCESS_TOKEN} ({@code tinku.mercadopago.access-token}). El mensaje
 * llega tal cual a la pantalla del usuario, por eso no nombra variables de entorno.
 */
public class MercadoPagoNoConfiguradoException extends RuntimeException {
    public MercadoPagoNoConfiguradoException() {
        super("Los pagos no están disponibles en este momento. Probá de nuevo en unos minutos.");
    }
}