package com.tinku.pagos.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Cuerpo de PUT /api/pagos/tarifa (Spec M5 US-6, FR-PAG-006, Chunk M5-H): el
 * Tutor fija el precio por sesión que va a cobrar. Un valor por sesión — este
 * precio se congela en cada Reserva al crearse (FR-PAG-013), nunca se re-congela.
 */
public record ActualizarTarifaTutorRequest(
        @NotNull(message = "precioSesion es obligatorio.")
        @DecimalMin(value = "0.01", message = "El precio por sesión debe ser mayor a cero.")
        BigDecimal precioSesion) {
}