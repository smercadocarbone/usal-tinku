package com.tinku.pagos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BR-PAG-01 (FR-PAG-003): comisión de plataforma = 15% del monto bruto, a cargo
 * del Tutor y nunca visible como línea aparte (US-7). Un único cálculo compartido
 * por todo el módulo para que la preferencia de M5-A ({@code marketplace_fee}) y
 * la {@code comision_plataforma} de la {@code Transaccion} que crea el webhook de
 * M5-B produzcan SIEMPRE el mismo número — no dos implementaciones que puedan
 * divergir en redondeo.
 */
@Component
public class ComisionPlataforma {

    private final int percent;

    public ComisionPlataforma(@Value("${tinku.mercadopago.marketplace-fee-percent:15}") int percent) {
        this.percent = percent;
    }

    public BigDecimal calcular(BigDecimal montoBruto) {
        return montoBruto.multiply(BigDecimal.valueOf(percent).movePointLeft(2))
                .setScale(2, RoundingMode.HALF_UP);
    }
}