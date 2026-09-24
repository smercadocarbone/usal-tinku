/**
 * Único mapa de etiquetas humanas para los enums que viajan en JSON (B6 UX-02).
 *
 * Regla: nunca renderizar un enum crudo en pantalla — pasar por estos mapas
 * con fallback al valor original (por si el backend agrega un valor nuevo que
 * todavía no tiene etiqueta). Los valores de acá coinciden 1:1 con los
 * `@JsonValue` de los enums del backend; fijate allí antes de tocar uno.
 */

// Reserva — backend `reservas.model.EstadoReserva`.
export const ETIQUETA_ESTADO_RESERVA: Record<string, string> = {
  pendiente_pago: "Pendiente de pago",
  confirmada: "Confirmada",
  en_curso: "En curso",
  finalizada: "Finalizada",
  cancelada: "Cancelada",
  // Nunca culpar (UX-01 §5): "No se presentaron" → "La clase no se realizó".
  no_show_estudiante: "No se realizó · el alumno no se conectó",
  no_show_tutor: "No se realizó · el tutor no se conectó",
  no_show_doble: "La clase no se realizó",
};

// Cancelación de Reserva — backend `reservas.model.MotivoCancelacion`.
export const ETIQUETA_MOTIVO_CANCELACION: Record<string, string> = {
  voluntaria: "Se canceló la clase",
  timeout_pago: "No se completó el pago a tiempo",
  revocacion_autorizacion: "El adulto responsable revocó la autorización",
  sancion: "Cancelada por el sistema",
};

// Solicitud de un menor a su Adulto Responsable — backend `reservas.model.EstadoSolicitud`.
export const ETIQUETA_ESTADO_SOLICITUD: Record<string, string> = {
  pendiente: "Pendiente",
  convertida: "Convertida en reserva",
  expirada: "Expirada",
  rechazada: "Rechazada",
};

// Denuncia contra un Tutor — backend `seguridad.model.EstadoDenuncia`.
export const ETIQUETA_ESTADO_DENUNCIA: Record<string, string> = {
  registrada: "Registrada",
  en_revision: "En revisión",
  resuelta_infundada: "Resuelta: infundada",
  resuelta_fundada: "Resuelta: fundada",
  escalada: "Escalada",
};

// Motivo de Denuncia — backend `seguridad.model.MotivoDenuncia`.
export const ETIQUETA_MOTIVO_DENUNCIA: Record<string, string> = {
  comportamiento_inapropiado: "Comportamiento inapropiado",
  incumplimiento: "No cumplió lo acordado",
  fraude: "Fraude",
  contenido_ilegal: "Contenido ilegal",
  acoso: "Acoso",
};

// Alerta de seguridad del kill-switch — backend `aula.model.AlertaSeguridad`.
export const ETIQUETA_ESTADO_ALERTA: Record<string, string> = {
  pendiente_revision: "Pendiente de revisión",
  resuelta_reactivacion: "Resuelta — sesión reactivada",
  resuelta_baja: "Resuelta — sin reactivación",
};

// Credencial académica — backend `identidad.model.EstadoCredencial`.
export const ETIQUETA_ESTADO_CREDENCIAL: Record<string, string> = {
  PENDIENTE: "Pendiente de revisión",
  APROBADO: "Aprobada",
  RECHAZADO: "Rechazada",
};

// Ticket de soporte — backend `admin.model.EstadoTicket`.
export const ETIQUETA_ESTADO_TICKET: Record<string, string> = {
  abierto: "Abierto",
  en_proceso: "En proceso",
  resuelto: "Resuelto",
  cerrado: "Cerrado",
};

// Transacción de pago (escrow) — backend `pagos.model.EstadoTransaccion`.
export const ETIQUETA_ESTADO_PAGO: Record<string, string> = {
  retenido_escrow: "Retenido hasta después de la clase",
  liberado: "Liberado al tutor",
  reembolsado: "Reembolsado",
  pausado_denuncia: "Pausado por denuncia",
};

/** Helper: etiqueta humana con fallback al valor crudo. */
export function etiqueta(mapa: Record<string, string>, valor: string | null | undefined): string {
  if (valor === null || valor === undefined) return "—";
  return mapa[valor] ?? valor;
}
/**
 * Tono de la pastilla de estado de una Reserva (`EstadoReserva`). Mapa único:
 * ninguna pantalla decide por su cuenta de qué color es un estado.
 */
export const TONO_ESTADO_RESERVA: Record<string, "neutro" | "exito" | "aviso" | "peligro" | "info" | "marca"> = {
  pendiente_pago: "aviso",
  confirmada: "exito",
  en_curso: "info",
  finalizada: "neutro",
  cancelada: "neutro",
  no_show_estudiante: "neutro",
  no_show_tutor: "neutro",
  no_show_doble: "neutro",
};
