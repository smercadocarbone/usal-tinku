package com.tinku.pagos.service;

/** La tarifa por hora supera el tope que entra en los montos guardados (→ 422). */
public class TarifaSobreTopeException extends RuntimeException {

    public TarifaSobreTopeException() {
        super("La tarifa por hora no puede superar $8.333.333,33.");
    }
}
