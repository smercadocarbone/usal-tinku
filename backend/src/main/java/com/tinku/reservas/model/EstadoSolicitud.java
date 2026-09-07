package com.tinku.reservas.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Estados de una Solicitud de Sesión (FR-RES-021/022, US-2/US-3).
 * Los valores de base de datos son minúsculas (V9__m4_reservas.sql) por
 * convención del schema `reservas`; {@link EstadoSolicitudConverter} mapea.
 * Jackson los serializa con {@code @JsonValue} (ej. `pendiente`, no `PENDIENTE`).
 */
public enum EstadoSolicitud {
    PENDIENTE("pendiente"),
    CONVERTIDA("convertida"),
    EXPIRADA("expirada"),
    RECHAZADA("rechazada");

    private final String valor;

    EstadoSolicitud(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static EstadoSolicitud parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de solicitud inválido: " + valor));
    }
}