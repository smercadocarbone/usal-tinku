package com.tinku.seguridad.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Motivos de la lista cerrada de Denuncia (US-1, FR-SEC-001). Los elige quien
 * denuncia; el Admin de Moderación y Seguridad es quién clasifica en la
 * resolución. {@code CONTENIDO_ILEGAL} dispara el escalado de US-5/FR-SEC-009.
 */
public enum MotivoDenuncia {
    COMPORTAMIENTO_INAPROPIADO("comportamiento_inapropiado"),
    INCUMPLIMIENTO("incumplimiento"),
    FRAUDE("fraude"),
    CONTENIDO_ILEGAL("contenido_ilegal"),
    ACOSO("acoso");

    private final String valor;

    MotivoDenuncia(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static MotivoDenuncia parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Motivo de denuncia inválido: " + valor));
    }
}