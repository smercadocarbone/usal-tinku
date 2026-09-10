package com.tinku.resumen.port;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default de {@link TranscriptSesionProveedor} mientras M3 no persiste el
 * transcript (LiveKit Egress pendiente): responda {@code null} → el pipeline
 * marca el caso borde #2 y no genera resumen. Sin storage no hay transcript que
 * anonimizar, y el sistema nunca inventa contenido (Spec M6 §5, caso 2).
 */
@Component
public class TranscriptSesionProveedorNoDisponible implements TranscriptSesionProveedor {

    @Override
    public String transcript(UUID sesionId) {
        return null;
    }
}