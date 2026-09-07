package com.tinku.reservas.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Estados de una Reserva (Plan_M4, sección 1; FR-RES-003/004/005/008/009/017/020).
 * `cancelada` exige además un {@link MotivoCancelacion} (E-05 de la ronda de QA —
 * la constraint chk_motivo_solo_si_cancelada de V9 lo garantiza a nivel de BD).
 * Los valores de base de datos son minúsculas (V9__m4_reservas.sql) por convención
 * del schema `reservas`; Jackson los serializa con {@code @JsonValue} para que la
 * API use la misma forma (ej. `pendiente_pago`, no `PENDIENTE_PAGO`).
 */
public enum EstadoReserva {
    PENDIENTE_PAGO("pendiente_pago"),
    CONFIRMADA("confirmada"),
    EN_CURSO("en_curso"),
    FINALIZADA("finalizada"),
    CANCELADA("cancelada"),
    NO_SHOW_ESTUDIANTE("no_show_estudiante"),
    NO_SHOW_TUTOR("no_show_tutor"),
    NO_SHOW_DOBLE("no_show_doble");

    private final String valor;

    EstadoReserva(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static EstadoReserva parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de reserva inválido: " + valor));
    }
}