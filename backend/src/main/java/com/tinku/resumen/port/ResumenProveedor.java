package com.tinku.resumen.port;

import java.util.UUID;

/**
 * Puerto del proveedor de LLM para el resumen (T-M6-05). Proveedor decidido:
 * GPT-4o (ADR-M6-03), activo con {@code LLM_PROVEEDOR=gpt-4o}; sin eso el bean
 * es {@link ResumenProveedorFailClosed} y nada sale a ningun modelo externo.
 *
 * <p>Anonimizacion (FR-SUM-005): {@code transcriptAnonimizado} ES lo que se
 * envia — el pipeline (ResumenService) corre {@code AnonimizadorTranscript}
 * antes de construir este request, y el transcript crudo jamas llega aca.
 * {@code audioBase64}/{@code audioMimeType} quedan en el contrato pero no se
 * usan: el audio crudo no es anonimizable antes de salir, por eso ADR-M6-03
 * descarta el "audio directo" de la Constitucion y el LLM solo recibe texto.
 */
public interface ResumenProveedor {

    /** Input del proveedor: el transcript YA anonimizado + metadata. El audio
     *  (base64 + mime) es opcional y hoy no se usa — ver javadoc de la interfaz. */
    record ResumenRequest(UUID idSesion, String transcriptAnonimizado,
                          String audioMimeType, String audioBase64,
                          String materia, String nivelEscolar) {
    }

    record ResumenResultado(String texto) {
    }

    /**
     * Genera el resumen en una sola llamada. Contrato de fallo: lanza
     * RuntimeException si el proveedor no responde — el servicio de M6 lo
     * traduce en el backoff de reintentos de FR-SUM-007 (mismo patron que M5).
     */
    ResumenResultado generarResumen(ResumenRequest request);
}