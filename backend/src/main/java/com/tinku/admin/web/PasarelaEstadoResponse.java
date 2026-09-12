package com.tinku.admin.web;

/**
 * Estado global de la pasarela de pagos (V22) tal como lo ve Soporte
 * Financiero: {@code habilitada=true} → cobro real; {@code false} → modo Bypass
 * (las Reservas se confirman sin procesar cobros reales).
 */
public record PasarelaEstadoResponse(boolean habilitada) {

    public static PasarelaEstadoResponse from(boolean habilitada) {
        return new PasarelaEstadoResponse(habilitada);
    }
}