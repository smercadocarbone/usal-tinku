/**
 * Plazos de negocio que la UI le cuenta al usuario. Copia de
 * `docs/Tabla_Tiempos_Tinku.md` (única fuente de verdad): si cambia la tabla,
 * cambia acá y en ningún otro lado del frontend.
 */
export const TIEMPOS = {
  /** Timeout de reserva sin pagar. */
  pagoMinutos: 15,
  /** Liberación de escrow, desde `sesion.finalizada`. */
  liberacionHoras: 24,
  /** Cancelación sin penalidad. */
  cancelacionSinPenalidadHoras: 24,
  /** Creación de sala + botón de unirse. */
  salaAbreMinutosAntes: 5,
  /** Ventana mínima para reservar. */
  ventanaMinimaReservaMinutos: 15,
  /** Plazo de descargo de una Denuncia estándar. */
  descargoHoras: 48,
  /** Ventana de edición de calificación pública. */
  edicionCalificacionHoras: 48,
  /** Revisión del Admin de una Alerta de kill-switch. */
  revisionAlertaHoras: 12,
  /** Edad mínima del menor. */
  edadMinimaMenor: 6,
  /** Umbral mínimo de calificaciones públicas ("Tutor nuevo" antes). */
  minimoCalificaciones: 5,
  /** Espera tras agotar intentos de OCR. */
  esperaOcrHoras: 24,
} as const;
