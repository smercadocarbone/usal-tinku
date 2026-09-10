package com.tinku.resumen.port;

import java.util.UUID;

/**
 * Fuente del transcript de la Sesion (M3 no lo persiste aun — LiveKit Egress
 * no esta implementado). {@code null} significa "sin contenido util" → el caso
 * borde #2 del Spec M6 (no se genera resumen, nunca se inventa contenido).
 * M3-E reemplaza {@link TranscriptSesionProveedorNoDisponible} por la
 * implementacion real cuando exista el storage del transcript.
 */
public interface TranscriptSesionProveedor {

    /** Transcript crudo de la sesion, o {@code null} si no hay contenido util. */
    String transcript(UUID sesionId);
}