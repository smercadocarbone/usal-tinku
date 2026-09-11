package com.tinku.admin.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/**
 * Roles del Admin de Tinku (Spec M8, FR-ADM-008; Plan M8 §1). Un Admin tiene
 * exactamente un rol en el MVP — no combinable (a diferencia de las capacidades
 * de Usuario de M1). Valores BD en minúsculas (V16), columna {@code admins.rol}.
 */
public enum RolAdmin {

    MODERACION_SEGURIDAD("moderacion_seguridad"),
    SOPORTE_FINANCIERO("soporte_financiero");

    private final String valor;

    RolAdmin(String valor) {
        this.valor = valor;
    }

    @JsonValue
    public String getValor() {
        return valor;
    }

    @JsonCreator
    public static RolAdmin parse(String valor) {
        return Arrays.stream(values())
                .filter(r -> r.valor.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rol de Admin inválido: " + valor));
    }
}