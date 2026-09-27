import { api } from "./api";
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
  /** v2.5: el beneficiario es un Menor (habilita la nota del Tutor al AR, FR-RES-026). */
  beneficiarioMenor?: boolean;
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

/* ---- Enmienda v2.5: pedido previo (FR-RES-027, solo texto) y nota del Tutor (FR-RES-026) ---- */

export interface PedidoPrevio {
  reservaId: string;
  texto: string;
  updatedAt: string;
  /** Todavía se puede cambiar (reserva activa y la clase no empezó). */
  editable: boolean;
}

export interface NotaClase {
  reservaId: string;
  texto: string;
  createdAt: string;
  updatedAt: string;
  /** Solo el Tutor, dentro de las 48 hs. */
  editable: boolean;
}

/** `undefined` (204): todavía no hay pedido. */
export function getPedido(reservaId: string): Promise<PedidoPrevio | undefined> {
  return api.get<PedidoPrevio | undefined>(`/api/reservas/${reservaId}/pedido`);
}

export function guardarPedido(reservaId: string, texto: string): Promise<PedidoPrevio> {
  return api.put<PedidoPrevio>(`/api/reservas/${reservaId}/pedido`, { texto });
}

export function getNota(reservaId: string): Promise<NotaClase | undefined> {
  return api.get<NotaClase | undefined>(`/api/reservas/${reservaId}/nota`);
}

export function escribirNota(reservaId: string, texto: string): Promise<NotaClase> {
  return api.put<NotaClase>(`/api/reservas/${reservaId}/nota`, { texto });
}

/* ---- Enmienda v2.5: pedido de reprogramación del Tutor (FR-RES-029..031) ---- */

export interface PedidoReprogramacion {
  id: string;
  reservaId: string;
  horarioOriginal: string;
  horarioPropuesto: string;
  motivo: string | null;
  estado: string;
  createdAt: string;
  /** Hasta cuándo se puede responder (T-60 de la clase original); después se cancela y se devuelve. */
  venceAt: string;
  /** Quien pagó (con un menor, su adulto responsable). */
  puedoResponder: boolean;
  /** El Tutor. */
  puedoRetirar: boolean;
}

export function getPedidoReprogramacion(reservaId: string): Promise<PedidoReprogramacion | undefined> {
  return api.get<PedidoReprogramacion | undefined>(`/api/reservas/${reservaId}/pedido-reprogramacion`);
}

export function pedirReprogramacion(reservaId: string, nuevoHorario: string, motivo: string): Promise<PedidoReprogramacion> {
  return api.post<PedidoReprogramacion>(`/api/reservas/${reservaId}/pedido-reprogramacion`, {
    nuevoHorario,
    ...(motivo.trim() ? { motivo: motivo.trim() } : {}),
  });
}

export function responderReprogramacion(reservaId: string, acepta: boolean): Promise<void> {
  return api.post<void>(`/api/reservas/${reservaId}/pedido-reprogramacion/${acepta ? "aceptar" : "rechazar"}`);
}

export function retirarReprogramacion(reservaId: string): Promise<void> {
  return api.delete<void>(`/api/reservas/${reservaId}/pedido-reprogramacion`);
}
