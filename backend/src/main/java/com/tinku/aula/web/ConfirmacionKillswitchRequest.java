package com.tinku.aula.web;

import jakarta.validation.constraints.NotNull;

/**
 * Body de {@code POST /api/sesiones/{id}/killswitch/confirmacion} (T-M3-09,
 * rama adultos): la respuesta del OTRO participante a si vio contenido
 * inapropiado. {@code true} → corte + bloqueo del detectado; {@code false} →
 * la sesión continúa (solo log interno).
 */
public record ConfirmacionKillswitchRequest(
        @NotNull Boolean vio) {
}