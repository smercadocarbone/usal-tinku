/**
 * Disponibilidad del tutor en días concretos a partir de sus franjas publicadas
 * (M4: semanales por `diaSemana` 0=domingo, o puntuales por `fechaEspecifica`).
 * Todas las fechas se calculan en hora argentina.
 */
import { claveDia } from "./formatos";

export interface Franja {
  id: string;
  tutorId: string;
  diaSemana: number | null;
  fechaEspecifica: string | null;
  horaInicio: string;
  horaFin: string;
  activa: boolean;
}

export interface DiaAgenda {
  /** `YYYY-MM-DD` en hora argentina. */
  fecha: string;
  /** Mediodía de ese día (para formatear sin corrimientos de zona). */
  referencia: Date;
  franjas: Franja[];
}


/** Día de la semana (0=domingo) de una fecha `YYYY-MM-DD`. */
function diaSemana(fecha: string): number {
  return new Date(`${fecha}T12:00:00-03:00`).getUTCDay();
}

export function hhmm(hora: string): string {
  return hora.slice(0, 5);
}

export function minutos(hora: string): number {
  const [h, m] = hora.split(":").map(Number);
  return (h ?? 0) * 60 + (m ?? 0);
}

/** Duración de una franja en minutos. */
export function duracionFranja(f: Franja): number {
  return minutos(f.horaFin) - minutos(f.horaInicio);
}

/** Instante ISO del inicio de una franja en una fecha (Argentina es UTC-3 todo el año). */
export function inicioISO(fecha: string, hora: string): string {
  return new Date(`${fecha}T${hhmm(hora)}:00-03:00`).toISOString();
}

/** Los próximos `cantidad` días (desde hoy) con las franjas que aplican a cada uno. */
export function proximosDias(franjas: Franja[], cantidad = 14, ahora: Date = new Date()): DiaAgenda[] {
  const activas = franjas.filter((f) => f.activa);
  const hoy = claveDia(ahora);
  const dias: DiaAgenda[] = [];
  for (let i = 0; i < cantidad; i++) {
    const ref = new Date(`${hoy}T12:00:00-03:00`);
    ref.setUTCDate(ref.getUTCDate() + i);
    const fecha = claveDia(ref);
    const dow = diaSemana(fecha);
    const delDia = activas
      .filter((f) => (f.fechaEspecifica ? f.fechaEspecifica.slice(0, 10) === fecha : f.diaSemana === dow))
      .sort((a, b) => minutos(a.horaInicio) - minutos(b.horaInicio));
    dias.push({ fecha, referencia: ref, franjas: delDia });
  }
  return dias;
}
