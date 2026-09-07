package com.tinku.reservas.web;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Body de {@code POST /api/tutores/franjas} (T-M4-02, FR-RES-012).
 * ADR-M4-01: franja semanal recurrente ({@code diaSemana}) o puntual
 * ({@code fechaEspecifica}) — exactamente una (chk_franja_modo en V9).
 */
public record PublicarFranjaRequest(
        Short diaSemana,
        LocalDate fechaEspecifica,
        @NotNull LocalTime horaInicio,
        @NotNull LocalTime horaFin) {

    @AssertTrue(message = "Debe indicarse diaSemana (0-6) o fechaEspecifica, no ambos ni ninguno.")
    public boolean isModoValido() {
        return (diaSemana == null) != (fechaEspecifica == null);
    }
}