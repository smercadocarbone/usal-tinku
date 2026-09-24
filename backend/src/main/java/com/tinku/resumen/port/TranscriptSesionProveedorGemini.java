package com.tinku.resumen.port;

import com.tinku.resumen.GeminiCliente;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Obtiene el transcript de la sesion vía Gemini (T07, ADR-M6-03). Activo solo
 * con la property {@code tinku.resumen.proveedor=gemini}; sin ella sigue
 * {@link TranscriptSesionProveedorNoDisponible}.
 *
 * <p>La transcripcion en si (audio → texto) ya vive en
 * {@link GeminiCliente#transcribir(byte[], String)}; lo que T08 agrega es el
 * origen del audio: {@code AlmacenamientoAudio.leer(sesion.getAudioObjeto())} y
 * la Files API de Gemini si el archivo supera el limite de {@code inlineData}
 * (TG4). Mientras T08 no exista, este bean devuelve {@code null} — mismo caso
 * borde #2 que el default (sin contenido util no se genera resumen ni se
 * inventa contenido).
 */
@Component
@ConditionalOnProperty(name = "tinku.resumen.proveedor", havingValue = "gemini")
public class TranscriptSesionProveedorGemini implements TranscriptSesionProveedor {

    private final GeminiCliente geminiCliente;

    public TranscriptSesionProveedorGemini(GeminiCliente geminiCliente) {
        this.geminiCliente = geminiCliente;
    }

    @Override
    public String transcript(UUID sesionId) {
        // T07: el audio aun no existe (lo provee T08). Ver javadoc de la clase.
        return null;
    }
}