package com.tinku.aula.web;

import com.tinku.seguridad.model.AlertaSeguridad;

import java.time.Instant;
import java.util.UUID;

public record EvidenciaResponse(UUID id, UUID sesionId, String rama, UUID detectadoId,
                                boolean tieneClip, String estado) {

    public static EvidenciaResponse from(AlertaSeguridad a) {
        return new EvidenciaResponse(a.getId(), a.getSesionId(), a.getRama(),
                a.getDetectadoId(), a.getClipUrl() != null, a.getEstado());
    }
}