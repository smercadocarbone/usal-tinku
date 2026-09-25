package com.tinku.reservas.web;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Body de {@code POST /api/reservas} (reserva directa, T-M4-05; US-3/US-4,
 * FR-RES-001/003). La crea quien paga: Estudiante adulto para sí mismo
 * ({@code beneficiarioId} ausente) o Adulto Responsable por su menor a cargo
 * ({@code beneficiarioId} = id del menor). El menor nunca puede reservar
 * directo por sí mismo (Artículo II) — se resuelve en el servicio por los datos
 * propios de M1, no por un flag del request.
 */
public record NuevaReservaDirectaRequest(
        @NotNull UUID tutorId,
        UUID beneficiarioId,
        @NotNull Instant horario,
        /** D6: 30 a 180 minutos, en bloques de 30 (lo valida el servicio → 422). */
        @NotNull Integer duracionMinutos,
        /** T09: adicional pago de resumen (opcional; null = no). Nunca con un menor → 422. */
        Boolean resumenContratado) {

    public NuevaReservaDirectaRequest(UUID tutorId, UUID beneficiarioId, Instant horario, Integer duracionMinutos) {
        this(tutorId, beneficiarioId, horario, duracionMinutos, null);
    }

    public boolean conResumen() {
        return Boolean.TRUE.equals(resumenContratado);
    }
}