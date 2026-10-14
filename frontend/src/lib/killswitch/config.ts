/**
 * Constantes del kill-switch del cliente. Cada tiempo sale de `docs/Tabla_Tiempos_Tinku.md` (filas
 * ADR-M3-07); no se cambian acá sin cambiar la tabla (AGENTS §0).
 */

/** Tabla de Tiempos: "Intervalo de clasificación del kill-switch". */
export const INTERVALO_CLASIFICACION_MS = 250;
/** Tabla de Tiempos: "Confirmación de una detección". */
export const MUESTRAS_PARA_CONFIRMAR = 3;
/** Tabla de Tiempos: "Tiempo máximo de carga del clasificador". */
export const CARGA_MAXIMA_MS = 20_000;
/** Tabla de Tiempos: "Tiempo sin respuesta del clasificador". */
export const SIN_RESPUESTA_MS = 10_000;
/** Tabla de Tiempos: "Demora que oculta el video con un menor". */
export const DEMORA_OCULTAR_MS = 3_000;
/** Tabla de Tiempos: "Reintentos para reactivar el clasificador" (cantidad). */
export const REINTENTOS_REACTIVAR = 5;
/** Tabla de Tiempos: "Reintentos para reactivar el clasificador" (espera entre intentos). */
export const ESPERA_ENTRE_REINTENTOS_MS = 5_000;
/** ADR-M3-07 §4. */
export const UMBRAL_DETECCION = 0.85;
export const TAMANO_ENTRADA = 224;
export const URL_MODELO = "/modelos/nsfw/model.json";
export const URL_WASM = "/modelos/tfjs-wasm/";

/** Tabla de Tiempos: "Buffer rotativo de evidencia (kill-switch)". */
export const BUFFER_SEGUNDOS = 30;
/** ADR-M3-07 §5. */
export const CLIP_BITS_POR_SEGUNDO = 1_200_000;
export const CLIP_MAX_BYTES = 5 * 1024 * 1024;
/** Tabla de Tiempos: "Reintentos de subida del clip de evidencia" (intentos en total). */
export const REINTENTOS_SUBIDA_CLIP = 3;
/** Tabla de Tiempos: "Reintentos de subida del clip de evidencia" (espera entre intentos). */
export const ESPERA_SUBIDA_CLIP_MS = 2_000;

/** Tabla de Tiempos: "Reintentos del disparo del kill-switch" (intentos en total). */
export const REINTENTOS_DISPARO = 3;
/** Tabla de Tiempos: "Reintentos del disparo del kill-switch" (espera entre intentos). */
export const ESPERA_ENTRE_DISPAROS_MS = 1_000;
/** Tabla de Tiempos: "Plazo de respuesta de la rama adultos del kill-switch". */
export const PLAZO_RAMA_ADULTOS_MS = 300_000;
/** Canal de datos de LiveKit, separado de "pizarra" y del modo texto. */
export const TOPIC_KILLSWITCH = "killswitch";
