package com.tinku.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Clasificación de la resolución de una Denuncia (US-4). Contrato compartido
 * que viaja en {@code denuncia.resuelta} hacia M5 (escrow) y M4 (cancelar
 * reservas futuras — nunca en {@code infundada}). {@code escalada} es la rama
 * de contenido ilegal (US-5, FR-SEC-009) y fuerza suspensión definitiva.
 */
public enum ResolucionDenuncia {
    INFUNDADA("infundada"),
    FUNDADA("fundada"),
    ESCALADA("escalada");

    private final String valor;

    ResolucionDenuncia(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static ResolucionDenuncia parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resolución de denuncia inválida: " + valor));
    }
}