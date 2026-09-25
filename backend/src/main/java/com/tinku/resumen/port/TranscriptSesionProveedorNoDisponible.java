package com.tinku.resumen.port;

import java.util.UUID;

/**
 * {@link TranscriptSesionProveedor} sin proveedor ({@code LLM_PROVEEDOR} distinto de
 * {@code gpt-4o}): responde {@code null} → el pipeline marca el caso borde #2 y no genera
 * resumen; el sistema nunca inventa contenido (Spec M6 §5, caso 2).
 */
public class TranscriptSesionProveedorNoDisponible implements TranscriptSesionProveedor {

    @Override
    public String transcript(UUID sesionId) {
        return null;
    }
}