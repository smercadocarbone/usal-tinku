package com.tinku.admin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Estados de un ticket de soporte (US-7, T-M8-05). Valores BD en minúsculas
 * (V16): {@code abierto} → {@code en_proceso} → {@code resuelto} → {@code cerrado}.
 */
public enum EstadoTicket {

    ABIERTO("abierto"),
    EN_PROCESO("en_proceso"),
    RESUELTO("resuelto"),
    CERRADO("cerrado");

    private final String valor;

    EstadoTicket(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static EstadoTicket parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de ticket inválido: " + valor));
    }
}