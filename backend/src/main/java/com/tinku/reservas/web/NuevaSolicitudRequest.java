package com.tinku.reservas.web;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Body de {@code POST /api/solicitudes} (T-M4-03, FR-RES-021) — lo genera el menor. */
public record NuevaSolicitudRequest(
        @NotNull UUID tutorId,
        @NotNull Instant horarioPropuesto,
        /** D6: 30 a 180 minutos, en bloques de 30; la Reserva la hereda al aprobarse. */
        @NotNull Integer duracionMinutos) {
}