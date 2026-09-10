package com.tinku.aula.web;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Body de {@code POST /api/sesiones/{id}/killswitch} (T-M3-07). NO acepta la
 * rama como input — la decide el backend con datos de M1 (si el beneficiario de
 * la Reserva es un menor → rama menor, Artículo II). Un body manipulado con
 * {@code rama: "adultos"} simplemente se ignora (deserialización parcial).
 */
public record KillswitchRequest(
        @NotNull UUID detectadoId) {
}