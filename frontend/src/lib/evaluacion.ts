/**
 * T-TES-11b (docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md §4): instrumentación de las
 * sesiones moderadas que reemplazan al piloto. Todo es inocuo con el flag del backend apagado: no
 * llega `X-Busqueda-Id`, no se registra nada y el NPS responde 404 (no se ofrece).
 */

import { api } from "./api";

const CLAVE_BUSQUEDA = "tinku.evaluacion.busqueda";
const CLAVE_NPS_POSPUESTO = "tinku.evaluacion.nps-pospuesto";

interface BusquedaRecordada {
  id: string;
  tutorIds: string[];
}

/** Recuerda la última búsqueda registrada (sessionStorage: solo esta pestaña, se borra al cerrarla). */
export function recordarBusqueda(busquedaId: string | null, tutorIds: string[]): void {
  try {
    if (!busquedaId) {
      window.sessionStorage.removeItem(CLAVE_BUSQUEDA);
      return;
    }
    const valor: BusquedaRecordada = { id: busquedaId, tutorIds };
    window.sessionStorage.setItem(CLAVE_BUSQUEDA, JSON.stringify(valor));
  } catch {
    // Sin storage (modo privado, bloqueado): la evaluación pierde ese dato, la app sigue igual.
  }
}

function busquedaRecordada(): BusquedaRecordada | null {
  try {
    const crudo = window.sessionStorage.getItem(CLAVE_BUSQUEDA);
    return crudo ? (JSON.parse(crudo) as BusquedaRecordada) : null;
  } catch {
    return null;
  }
}

/**
 * I-01: el usuario abrió el perfil (`PERFIL`) o reservó (`RESERVA`) un Tutor sugerido por su última
 * búsqueda. Solo si ese Tutor estaba en la lista; nunca bloquea ni muestra errores.
 */
export function registrarAceptacion(tutorId: string, tipo: "PERFIL" | "RESERVA"): void {
  const b = busquedaRecordada();
  if (!b || !b.tutorIds.includes(tutorId)) return;
  api.post(`/api/busquedas/${b.id}/aceptaciones`, { tutorId, tipo }).catch(() => undefined);
}

/* ---- NPS in-app (I-04) ---- */

export async function npsPendiente(): Promise<boolean> {
  try {
    if (window.sessionStorage.getItem(CLAVE_NPS_POSPUESTO) === "1") return false;
  } catch {
    // sigue
  }
  try {
    const r = await api.get<{ ofrecer: boolean }>("/api/evaluacion/nps/pendiente");
    return r.ofrecer === true;
  } catch {
    return false; // flag apagado (404) o error: no se ofrece
  }
}

export async function responderNps(puntaje: number): Promise<void> {
  await api.post<{ puntaje: number }>("/api/evaluacion/nps", { puntaje });
}

/** "Ahora no": no se vuelve a ofrecer en esta pestaña. */
export function posponerNps(): void {
  try {
    window.sessionStorage.setItem(CLAVE_NPS_POSPUESTO, "1");
  } catch {
    // sin storage: puede volver a aparecer al recargar, no es grave
  }
}

/* ---- Panel de Admin (§4.3) ---- */

export interface IndicadoresEvaluacion {
  habilitada: boolean;
  desde: string | null;
  hasta: string | null;
  topK: number;
  busquedas: { total: number; conResultado: number; sinResultado: number };
  i01: {
    busquedasConAceptacion: number;
    tasa: number | null;
    aceptacionesPerfil: number;
    aceptacionesReserva: number;
    porPosicion: { posicion: number; cantidad: number }[];
  };
  nps: { respuestas: number; promotores: number; pasivos: number; detractores: number; valor: number | null };
  i02: { calificaciones: number; promedio: number | null };
}

export function getIndicadoresEvaluacion(desde?: string, hasta?: string): Promise<IndicadoresEvaluacion> {
  const params = new URLSearchParams();
  if (desde) params.set("desde", desde);
  if (hasta) params.set("hasta", hasta);
  const qs = params.toString();
  return api.get<IndicadoresEvaluacion>(`/api/admin/evaluacion/indicadores${qs ? `?${qs}` : ""}`);
}
