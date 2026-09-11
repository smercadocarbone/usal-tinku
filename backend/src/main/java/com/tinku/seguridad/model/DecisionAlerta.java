package com.tinku.seguridad.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Decisión del Admin de Moderación y Seguridad sobre una Alerta de kill-switch
 * (US-2): reactivar (acusación falsa) o sancionar (confirma/agrava la sanción). */
public enum DecisionAlerta {
    REACTIVAR("reactivar"),
    SANCIONAR("sancionar");

    private final String valor;

    DecisionAlerta(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static DecisionAlerta parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Decisión de alerta inválida: " + valor));
    }
}