package com.tinku.seguridad.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body de POST /api/denuncias/{id}/descargo y /api/alertas-seguridad/{id}/descargo
 * (FR-SEC-006: máx. 300 caracteres). */
public record DescargoRequest(
        @NotBlank @Size(max = 300) String descargo) {
}