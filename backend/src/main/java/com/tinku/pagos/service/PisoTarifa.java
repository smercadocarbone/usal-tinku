package com.tinku.pagos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Piso de la tarifa por hora del Tutor (T06, DT2; tesis Cap. 5: USD 4/h). Único
 * lugar del valor: {@code tinku.tarifa.piso-hora-ars}. PT3: fijo en ARS, lo revisa
 * un Admin una vez por mes (sin API de tipo de cambio). PT4: se exige al editar la
 * tarifa, no se aplica retroactivamente a las ya guardadas. No confundir con
 * {@code precios_referencia_regional}, que es una sugerencia (FR-PAG-005).
 */
@Component
public class PisoTarifa {

    private final BigDecimal pisoHora;

    public PisoTarifa(@Value("${tinku.tarifa.piso-hora-ars}") BigDecimal pisoHora) {
        this.pisoHora = pisoHora;
    }

    public BigDecimal pisoHora() {
        return pisoHora;
    }

    /** @throws TarifaBajoPisoException si {@code precioHora} está por debajo del piso (el piso mismo vale). */
    public void exigir(BigDecimal precioHora) {
        if (precioHora.compareTo(pisoHora) < 0) {
            throw new TarifaBajoPisoException(pisoHora);
        }
    }
}
