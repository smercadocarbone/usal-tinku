package com.tinku.pagos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * FR-PAG-019 (ADR-M5-04): tasa de procesamiento de MercadoPago, con IVA, que se le muestra al Tutor
 * para estimar cuánto le queda. Con el modelo A (ADR-M5-02) MercadoPago la descuenta de los fondos
 * del Tutor antes del {@code marketplace_fee}. Es solo informativa: la tasa real depende del plazo de
 * acreditación de cada cuenta, y Tinku nunca la usa para calcular montos (la única cuenta de dinero
 * de Tinku es {@link ComisionPlataforma}).
 */
@Component
public class ComisionMercadoPagoEstimada {

    private final BigDecimal porcentaje;

    public ComisionMercadoPagoEstimada(
            @Value("${tinku.mercadopago.comision-procesamiento-percent:6.04}") BigDecimal porcentaje) {
        this.porcentaje = porcentaje;
    }

    public BigDecimal porcentaje() {
        return porcentaje;
    }
}
