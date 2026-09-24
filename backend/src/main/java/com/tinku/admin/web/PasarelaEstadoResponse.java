package com.tinku.admin.web;

/**
 * Estado global de la pasarela de pagos (V22) tal como lo ve Soporte
 * Financiero: {@code habilitada=true} → cobro real; {@code false} → modo Bypass
 * (las Reservas se confirman sin procesar cobros reales).
 * {@code bypassPermitido} (FASE2-07): false en {@code prod}, para que el panel
 * deshabilite el toggle con una explicación en vez de fallar después del click.
 */
public record PasarelaEstadoResponse(boolean habilitada, boolean bypassPermitido) {

    public static PasarelaEstadoResponse from(boolean habilitada, boolean bypassPermitido) {
        return new PasarelaEstadoResponse(habilitada, bypassPermitido);
    }
}
