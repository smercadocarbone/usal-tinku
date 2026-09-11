package com.tinku.aula.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Body de {@code POST /api/sesiones/{id}/evidencia} (T-M3-08): solo la
 * REFERENCIA al clip de 30s del buffer (Artículo V — nunca se persiste el
 * video, y el buffer se descarta salvo disparo del kill-switch, BR-KS-01).
 * {@code duracionSegundos} es opcional y se valida contra el máximo del buffer
 * (30s).
 */
public record EvidenciaRequest(
        @NotBlank String clipUrl,
        Integer duracionSegundos) {
}