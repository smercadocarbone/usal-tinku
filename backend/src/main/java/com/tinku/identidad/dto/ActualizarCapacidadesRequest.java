package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activar/desactivar capacidades del usuario autenticado (FR-ID-015/016).
 * Al menos una capacidad debe seguir activa (FR-ID-001) y no se puede
 * desactivar "Adulto Responsable" mientras haya menores a cargo (FR-ID-016).
 */
public record ActualizarCapacidadesRequest(
        @NotNull Boolean capacidadEstudiante,
        @NotNull Boolean capacidadAdultoResponsable
) {
}
