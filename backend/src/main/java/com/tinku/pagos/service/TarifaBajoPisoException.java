package com.tinku.pagos.service;

import java.math.BigDecimal;

/** T06: la tarifa por hora quedó por debajo del piso (→ 422 con el piso en el cuerpo). */
public class TarifaBajoPisoException extends RuntimeException {

    private final BigDecimal pisoHora;

    public TarifaBajoPisoException(BigDecimal pisoHora) {
        super("La tarifa por hora no puede ser menor a $" + pisoHora.stripTrailingZeros().toPlainString() + ".");
        this.pisoHora = pisoHora;
    }

    public BigDecimal getPisoHora() {
        return pisoHora;
    }
}
