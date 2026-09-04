package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Un Adulto Responsable autoriza a un Tutor para dictar clases a uno de SUS
 * menores (tabla {@code autorizaciones_tutor}, FR-ID-009).
 */
public record AutorizarTutorRequest(
        @NotNull UUID menorId,
        @NotNull UUID tutorId
) {
}
