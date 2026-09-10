package com.tinku.seguridad.web;

import com.tinku.aula.model.AlertaSeguridad;

import java.time.Instant;
import java.util.UUID;

/** Vista de una Alerta de Seguridad para la cola de M8. */
public record AlertaSeguridadResponse(
        UUID id,
        UUID sesionId,
        String rama,
        UUID detectadoId,
        String estado,
        String descargoTexto,
        Instant descargoRecibidoAt,
        Instant clipRetencionHasta,
        Instant createdAt) {

    public static AlertaSeguridadResponse from(AlertaSeguridad a) {
        return new AlertaSeguridadResponse(a.getId(), a.getSesionId(), a.getRama(),
                a.getDetectadoId(), a.getEstado(), a.getDescargoTexto(), a.getDescargoRecibidoAt(),
                a.getClipRetencionHasta(), a.getCreatedAt());
    }
}