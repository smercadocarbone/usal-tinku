package com.tinku.resumen.port;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default de {@link TranscriptSesionProveedor} — sin la property
 * {@code tinku.resumen.proveedor=gemini} responde {@code null} → el pipeline
 * marca el caso borde #2 y no genera resumen. Con el proveedor activo (ADR-M6-03)
 * su adapter {@link TranscriptSesionProveedorGemini} reemplaza este bean solo
 * cuando la property dice {@code gemini}; hasta T08 no hay audio, asi que tampoco
 * genera — el sistema nunca inventa contenido (Spec M6 §5, caso 2).
 */
@Component
@ConditionalOnProperty(name = "tinku.resumen.proveedor", havingValue = "none",
        matchIfMissing = true)
public class TranscriptSesionProveedorNoDisponible implements TranscriptSesionProveedor {

    @Override
    public String transcript(UUID sesionId) {
        return null;
    }
}