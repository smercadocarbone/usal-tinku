import { ETIQUETA_ESTADO_RESERVA } from "./etiquetas";

/** Etiqueta humana del estado (B6: los enums no se renderizan crudos). */
export const ESTADO_ETIQUETA = ETIQUETA_ESTADO_RESERVA;

/**
 * Reserva como la ve quien pide (`GET /api/reservas`, UX-05 §4): nombres, duración
 * y acciones calculadas en el servidor. Los campos nuevos son opcionales para
 * tolerar mocks/respuestas viejas.
 */
export interface Reserva {
  id: string;
  pagadorId: string;
  beneficiarioId: string | null;
  tutorId: string;
  horario: string;
  precio: number | null;
  estado: string;
  motivoCancelacion: string | null;
  tutorNombre?: string | null;
  tutorApellido?: string | null;
  beneficiarioNombre?: string | null;
  beneficiarioApellido?: string | null;
  duracionMinutos?: number | null;
  /** Fin de la clase (D6: `horario + duracionMinutos`). */
  horarioFin?: string | null;
  pagoVenceAt?: string | null;
  puedePagar?: boolean;
  puedeCancelar?: boolean;
  /** Si cancelar AHORA devuelve todo al pagador (`PoliticaCancelacion`). */
  cancelarReembolsaTotal?: boolean | null;
  /** T09: adicional de resumen automático contratado. */
  resumenContratado?: boolean;
  precioAdicionalResumen?: number | null;
  /** Lo que se paga: clase + adicional. */
  montoTotal?: number | null;
}

/** Solicitud de clase de un menor (`/api/solicitudes`). */
export interface Solicitud {
  id: string;
  tutorId: string;
  horarioPropuesto: string;
  estado: string;
  expiraAt: string;
  menorId?: string;
  menorNombre?: string | null;
  tutorNombre?: string | null;
  tutorApellido?: string | null;
  /** D6: la Reserva hereda esta duración al aprobarse. */
  duracionMinutos?: number | null;
}

/** Fin agendado de la clase, si se conoce la duración. */
export function finDe(r: Reserva): string | null {
  if (r.horarioFin) return r.horarioFin;
  if (!r.duracionMinutos) return null;
  return new Date(new Date(r.horario).getTime() + r.duracionMinutos * 60000).toISOString();
}

export const ESTADOS_PROXIMOS = new Set(["pendiente_pago", "confirmada", "en_curso"]);
