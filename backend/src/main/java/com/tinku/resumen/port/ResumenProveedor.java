package com.tinku.resumen.port;

import java.util.UUID;

/**
 * Puerto del proveedor de LLM para el resumen (T-M6-05). La Constitucion
 * (Registro de Decisiones Tecnicas) manda: transcripcion + resumen en UNA sola
 * llamada al LLM elegido — GPT-4o o Gemini 2.0 Flash — que aceptan audio directo;
 * Whisper queda fuera del stack. El ADR del proveedor esta PENDIENTE (T-FIN-03):
 * por eso este puerto solo tiene el contrato y la implementacion activa por
 * default es {@link ResumenProveedorFailClosed} — nada sale a ningun modelo
 * externo mientras el ADR no se resuelva.
 *
 * <p>Anonimizacion (FR-SUM-005): {@code transcriptAnonimizado} ES lo que se
 * envia — el pipeline (ResumenService) corre {@code AnonimizadorTranscript}
 * antes de construir este request, y el transcript crudo jamas llega aca.
 * {@code audioBase64}/{@code audioMimeType} quedan en el contrato por la
 * decision const. de audio directo, pero hoy no se usan: el audio crudo no es
 * anonimizable antes de salir, asi que mientras el ADR no resuelva como
 * combinar audio-directo con anonimizacion, el flujo manda solo el transcript
 * ya limpio. (Flag para T-FIN-03.)
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