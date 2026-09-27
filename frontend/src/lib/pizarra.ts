/**
 * Protocolo de la pizarra compartida (FR-AULA-011, ADR-M3-06). Viaja por el canal de datos de
 * LiveKit (`topic: "pizarra"`, confiable). Coordenadas normalizadas 0..1 sobre un tablero 4:3, así
 * se ve igual en pantallas distintas. Nada se persiste (FR-AULA-013): el estado vive en memoria de
 * cada participante mientras dura la clase.
 *
 * Todo mensaje que llega se valida acá: un paquete mal formado (o que no es de la pizarra) se
 * descarta. Módulo puro, sin React ni LiveKit, para poder testearlo aislado.
 */

export const COLORES_PIZARRA = ["#111827", "#dc2626", "#2563eb", "#16a34a"] as const;
export const GROSORES_PIZARRA = [3, 6, 12] as const;
export const GROSOR_GOMA = 28;
/** Relación del tablero: 4:3 en todas las pantallas. */
export const ASPECTO_PIZARRA = 4 / 3;

const MAX_PUNTOS_POR_MENSAJE = 400; // 200 puntos (x, y): un mensaje confiable de LiveKit entra holgado
const MAX_TRAZOS = 2000;

export interface Trazo {
  id: string;
  /** Solo se deshacen los trazos propios. */
  propio: boolean;
  color: string;
  grosor: number;
  goma: boolean;
  /** [x0, y0, x1, y1, ...] normalizados 0..1. */
  puntos: number[];
}

export type MensajePizarra =
  /** Puntos nuevos de un trazo que se está dibujando (se agregan al final). */
  | { op: "segmento"; id: string; color: string; grosor: number; goma: boolean; puntos: number[] }
  /** Un trazo completo (para ponerse al día al entrar): reemplaza al de ese id. */
  | { op: "trazo"; id: string; color: string; grosor: number; goma: boolean; puntos: number[] }
  | { op: "deshacer"; id: string }
  | { op: "borrar" }
  /** Quien entra pide el estado; el otro responde con un "trazo" por cada uno. */
  | { op: "sync-pedido" }
  /** Quien abre la pizarra la abre también del otro lado. */
  | { op: "abrir" };

const ID_VALIDO = /^[A-Za-z0-9_-]{1,64}$/;

function puntosValidos(p: unknown): p is number[] {
  return (
    Array.isArray(p) &&
    p.length % 2 === 0 &&
    p.length <= MAX_PUNTOS_POR_MENSAJE &&
    p.every((n) => typeof n === "number" && Number.isFinite(n) && n >= 0 && n <= 1)
  );
}

function estiloValido(m: Record<string, unknown>): boolean {
  return (
    typeof m.goma === "boolean" &&
    typeof m.grosor === "number" &&
    (m.goma ? m.grosor === GROSOR_GOMA : (GROSORES_PIZARRA as readonly number[]).includes(m.grosor)) &&
    typeof m.color === "string" &&
    (COLORES_PIZARRA as readonly string[]).includes(m.color)
  );
}

/** Mensaje de la red → mensaje válido, o `null` si no es de la pizarra o está mal formado. */
export function decodificar(datos: Uint8Array): MensajePizarra | null {
  let m: Record<string, unknown>;
  try {
    m = JSON.parse(new TextDecoder().decode(datos)) as Record<string, unknown>;
  } catch {
    return null;
  }
  if (!m || typeof m !== "object" || m.tipo !== "pizarra") return null;
  switch (m.op) {
    case "segmento":
    case "trazo":
      if (typeof m.id !== "string" || !ID_VALIDO.test(m.id) || !puntosValidos(m.puntos) || !estiloValido(m)) return null;
      return {
        op: m.op,
        id: m.id,
        color: m.color as string,
        grosor: m.grosor as number,
        goma: m.goma as boolean,
        puntos: m.puntos,
      };
    case "deshacer":
      return typeof m.id === "string" && ID_VALIDO.test(m.id) ? { op: "deshacer", id: m.id } : null;
    case "borrar":
    case "sync-pedido":
    case "abrir":
      return { op: m.op };
    default:
      return null;
  }
}

export function codificar(m: MensajePizarra): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(JSON.stringify({ tipo: "pizarra", ...m }));
}

/** Aplica un mensaje (propio o del otro) al estado. No muta: devuelve una lista nueva. */
export function aplicar(trazos: Trazo[], m: MensajePizarra, propio: boolean): Trazo[] {
  switch (m.op) {
    case "segmento": {
      const i = trazos.findIndex((t) => t.id === m.id);
      if (i === -1) {
        if (trazos.length >= MAX_TRAZOS) return trazos;
        return [...trazos, { id: m.id, propio, color: m.color, grosor: m.grosor, goma: m.goma, puntos: [...m.puntos] }];
      }
      const copia = trazos.slice();
      copia[i] = { ...copia[i], puntos: [...copia[i].puntos, ...m.puntos] };
      return copia;
    }
    case "trazo": {
      const nuevo = { id: m.id, propio, color: m.color, grosor: m.grosor, goma: m.goma, puntos: [...m.puntos] };
      const i = trazos.findIndex((t) => t.id === m.id);
      if (i === -1) return trazos.length >= MAX_TRAZOS ? trazos : [...trazos, nuevo];
      const copia = trazos.slice();
      copia[i] = { ...nuevo, propio: copia[i].propio };
      return copia;
    }
    case "deshacer":
      return trazos.filter((t) => t.id !== m.id);
    case "borrar":
      return [];
    default:
      return trazos;
  }
}

/** Para ponerse al día: cada trazo como un mensaje "trazo", partido si es largo. */
export function mensajesDeSync(trazos: Trazo[]): MensajePizarra[] {
  const salida: MensajePizarra[] = [];
  for (const t of trazos) {
    for (let i = 0; i < Math.max(t.puntos.length, 1); i += MAX_PUNTOS_POR_MENSAJE) {
      const puntos = t.puntos.slice(i, i + MAX_PUNTOS_POR_MENSAJE);
      const base = { id: t.id, color: t.color, grosor: t.grosor, goma: t.goma, puntos };
      salida.push(i === 0 ? { op: "trazo", ...base } : { op: "segmento", ...base });
    }
  }
  return salida;
}

/** Parte los puntos de un trazo en segmentos que entran en un mensaje. */
export function partir(puntos: number[]): number[][] {
  const partes: number[][] = [];
  for (let i = 0; i < puntos.length; i += MAX_PUNTOS_POR_MENSAJE) partes.push(puntos.slice(i, i + MAX_PUNTOS_POR_MENSAJE));
  return partes;
}
