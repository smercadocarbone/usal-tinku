package com.tinku.pagos.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Cuerpo de PUT /api/pagos/tarifa (Spec M5 US-6, FR-PAG-006, Chunk M5-H): el
 * Tutor fija su precio POR HORA de clase (D6). Cada Reserva congela
 * {@code precioHora × duracionMinutos / 60} al crearse (FR-PAG-013), nunca se re-congela.
 */
public record ActualizarTarifaTutorRequest(
        @NotNull(message = "precioHora es obligatorio.")
        @DecimalMin(value = "0.01", message = "El precio por hora debe ser mayor a cero.")
        BigDecimal precioHora) {
}