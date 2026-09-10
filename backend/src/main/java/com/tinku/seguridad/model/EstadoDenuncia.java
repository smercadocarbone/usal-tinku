package com.tinku.seguridad.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Estados de una Denuncia estándar — FR-SEC-002 (los valores de BD están en
 * mayúsculas por la convención {@code @Enumerated(EnumType.STRING)} del repo).
 * <pre>
 * REGISTRADA → EN_REVISION (descargo 48hs) → RESUELTA_INFUNDADA / RESUELTA_FUNDADA / ESCALADA
 * </pre>
 */
public enum EstadoDenuncia {
    REGISTRADA("registrada"),
    EN_REVISION("en_revision"),
    RESUELTA_INFUNDADA("resuelta_infundada"),
    RESUELTA_FUNDADA("resuelta_fundada"),
    ESCALADA("escalada");

    private final String valor;

    EstadoDenuncia(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static EstadoDenuncia parse(String valor) {
        return Arrays.stream(values())
                .filter(e -> e.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estado de denuncia inválido: " + valor));
    }
}