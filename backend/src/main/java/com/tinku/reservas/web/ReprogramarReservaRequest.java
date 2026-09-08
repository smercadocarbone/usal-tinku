package com.tinku.reservas.web;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Body de {@code POST /api/reservas/{id}/reprogramar} (T-M4-07, US-5). Quien pagó
 * cambia el horario de una Reserva confirmada: se conserva el mismo precio original
 * y no se genera transacción nueva (FR-RES-015). Si faltan menos de 24hs, se trata
 * como cancelación tardía (FR-RES-016).
 */
public record ReprogramarReservaRequest(
        @NotNull Instant nuevoHorario) {
}