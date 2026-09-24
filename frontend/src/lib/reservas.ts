import { ETIQUETA_ESTADO_RESERVA } from "./etiquetas";

/** Etiqueta humana del estado (B6: los enums no se renderizan crudos). */
export const ESTADO_ETIQUETA = ETIQUETA_ESTADO_RESERVA;

export interface Reserva {
  id: string;
  pagadorId: string;
  beneficiarioId: string | null;
  tutorId: string;
  horario: string;
  precio: number | null;
  estado: string;
  motivoCancelacion: string | null;
}