package com.tinku.admin.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Actualización de la tabla de precios regional (US-8, FR-ADM-007): agrega una
 * versión NUEVA por provincia — nunca sobreescribe la vigente (FR-PAG-006, el
 * valor solo aplica hacia adelante). {@code vigenteDesde} lo fija el servidor.
 */
public record ActualizarPrecioRegionalRequest(
        @NotBlank @Size(max = 50) String provincia,
        @NotNull @DecimalMin("0.0") BigDecimal valorSugerido) {
}