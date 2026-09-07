package com.tinku.identidad.dto;

import com.tinku.identidad.model.EstadoCap;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Respuesta de un CAP. No expone datos internos del revisor.
 */
public record CapResponse(
        UUID id,
        EstadoCap estado,
        boolean tieneAntecedentes,
        LocalDate venceAt,
        int numeroIntento
) {
}
