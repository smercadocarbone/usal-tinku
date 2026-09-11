package com.tinku.seguridad.web;

import com.tinku.seguridad.model.MotivoDenuncia;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body de POST /api/denuncias (US-1). {@code sesionId} nullable (denuncia de perfil). */
public record PresentarDenunciaRequest(
        @NotNull UUID denunciadoId,
        UUID sesionId,
        @NotNull MotivoDenuncia motivo,
        String evidenciaUrl) {

    public PresentarDenunciaRequest {
        if (evidenciaUrl != null && evidenciaUrl.length() > 500) {
            throw new IllegalArgumentException("evidencia_url no puede superar los 500 caracteres");
        }
    }
}