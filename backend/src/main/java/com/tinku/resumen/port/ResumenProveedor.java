package com.tinku.resumen.port;

import java.util.UUID;

/**
 * Puerto del proveedor de LLM para el resumen (T-M6-05). ADR-M6-03 decidido el
 * 2026-09-24: <b>Gemini 3.5 Flash-Lite</b>, pipeline de <b>dos llamadas</b>
 * (transcribir → anonimizar → resumir, PT7) — con UNA sola llamada al modelo la
 * anonimizacion (FR-SUM-005) no podria correr antes de que el audio salga.
 * Whisper queda fuera del stack. Este puerto cubre la segunda llamada: a partir
 * del transcript YA anonimizado produce el resumen.
 *
 * <p>La implementacion activa es condicional: {@code ResumenProveedorGemini}
 * existe solo con la property {@code tinku.resumen.proveedor=gemini}; sin ella
 * sigue {@link ResumenProveedorFailClosed} — nada sale a ningun modelo externo
 * (tests y dev no llaman a Google).
 *
 * <p>Anonimizacion (FR-SUM-005): {@code transcriptAnonimizado} ES lo que se
 * envia — el pipeline (ResumenService) corre {@code AnonimizadorTranscript}
 * antes de construir este request, y el transcript crudo jamas llega aca.
 * {@code audioBase64}/{@code audioMimeType} quedan en el contrato pero
 * deliberadamente SIN uso (ADR-M6-03, pipeline de dos llamadas): el audio va al
 * transcriber ({@code TranscriptSesionProveedorGemini}), no a este paso.
 */
public interface ResumenProveedor {

    /** Input del proveedor: el transcript YA anonimizado + metadata. Audio
     *  (base64 + mime) SIN uso — ADR-M6-03, ver javadoc de la interfaz. */
    record ResumenRequest(UUID idSesion, String transcriptAnonimizado,
                          String audioMimeType, String audioBase64,
                          String materia, String nivelEscolar) {
    }

    record ResumenResultado(String texto) {
    }

    /**
     * Genera el resumen a partir del transcript ya anonimizado (segunda llamada
     * del pipeline de ADR-M6-03). Contrato de fallo: lanza RuntimeException si
     * el proveedor no responde — el servicio de M6 lo traduce en el backoff de
     * reintentos de FR-SUM-007 (mismo patron que M5).
     */
    ResumenResultado generarResumen(ResumenRequest request);
}