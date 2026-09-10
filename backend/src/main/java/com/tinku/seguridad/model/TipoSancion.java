package com.tinku.seguridad.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Escala de sanciones del Admin de Moderación y Seguridad (FR-SEC-005). */
public enum TipoSancion {
    ADVERTENCIA("advertencia"),
    SUSPENSION_TEMPORAL("suspension_temporal"),
    SUSPENSION_DEFINITIVA("suspension_definitiva"),
    BANEO_AUTORIDADES("baneo_autoridades");

    private final String valor;

    TipoSancion(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static TipoSancion parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de sanción inválido: " + valor));
    }
}