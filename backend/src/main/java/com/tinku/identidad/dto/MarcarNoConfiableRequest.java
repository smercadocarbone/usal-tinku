package com.tinku.identidad.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Marcar un Tutor como "no confiable" (o sacar ese marcado) para la cuenta del
 * Adulto Responsable (FR-ID-009): deja de aparecer en los resultados de
 * matching de esa cuenta, sin alertar a Admin ni tocar la reputación pública.
 */
public record MarcarNoConfiableRequest(
        @NotNull UUID tutorId,
        @NotNull Boolean noConfiable
) {
}
