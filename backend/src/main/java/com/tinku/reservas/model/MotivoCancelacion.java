package com.tinku.reservas.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Por qué se canceló una Reserva (Plan_M4, sección 1 — auditoría de cancelaciones,
 * resuelve E-05 de la ronda de QA). Solo tiene sentido en estado `cancelada`.
 * `voluntaria` cubre la cancelación libre (≥24hs) y la tardía asimétrica (US-6/US-7);
 * los demás distinguen origen del sistema.
 */
public enum MotivoCancelacion {
    VOLUNTARIA("voluntaria"),
    TIMEOUT_PAGO("timeout_pago"),
    REVOCACION_AUTORIZACION("revocacion_autorizacion"),
    SANCION("sancion"),
    /** PT10 (T02): el Tutor perdió la habilitación para menores (CAP vencido). */
    CAP_VENCIDO("cap_vencido");

    private final String valor;

    MotivoCancelacion(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static MotivoCancelacion parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Motivo de cancelación inválido: " + valor));
    }
}