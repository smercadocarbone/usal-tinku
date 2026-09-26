/**
 * Zona horaria explícita para todos los formatos de fecha/hora (B3).
 *
 * El servidor de Next corre en UTC pero el usuario está en Buenos Aires
 * (`Intl` en el cliente usa el TZ del navegador). Sin fixar la zona, el
 * render del servidor y el del cliente difieren alrededor de la medianoche →
 * mismatch de hidratación. Fixar `timeZone` en el formatter hace que ambos
 * lados produzcan el mismo string siempre.
 */
const ZONA_ARGENTINA = "America/Argentina/Buenos_Aires";

export function formatearFecha(iso: string): string {
  return new Date(iso).toLocaleDateString("es-AR", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: ZONA_ARGENTINA,
  });
}

export function formatearHora(iso: string): string {
  return new Date(iso).toLocaleTimeString("es-AR", {
    hour: "2-digit",
    minute: "2-digit",
    timeZone: ZONA_ARGENTINA,
  });
}

export function formatearPrecio(valor: number): string {
  return `$${valor.toLocaleString("es-AR")}`;
}

/**
 * Monto con la moneda explícita. Se usa donde la persona está por autorizar un
 * cobro real (pantalla de pago): ahí "$" a secas es ambiguo y el usuario tiene
 * derecho a saber en qué moneda se le va a debitar. En listados y perfiles
 * alcanza con `formatearPrecio` — "$" es el peso en contexto argentino.
 */
export function formatearPrecioConMoneda(valor: number): string {
  return `${formatearPrecio(valor)} ARS`;
}

export function formatearFechaCorta(iso: string): string {
  return new Date(iso).toLocaleDateString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: ZONA_ARGENTINA,
  });
}
/* ---- UX-01: formatos del sistema visual (`Precio`, `FechaHora`) ---- */

const PESOS = new Intl.NumberFormat("es-AR", {
  style: "currency",
  currency: "ARS",
  maximumFractionDigits: 0,
});

/** `$ 15.000` — pesos argentinos sin centavos. */
export function formatearPesos(valor: number): string {
  return PESOS.format(valor);
}

function partes(iso: string | Date, opciones: Intl.DateTimeFormatOptions): Record<string, string> {
  const fecha = typeof iso === "string" ? new Date(iso) : iso;
  const res: Record<string, string> = {};
  for (const p of new Intl.DateTimeFormat("es-AR", { ...opciones, timeZone: ZONA_ARGENTINA }).formatToParts(fecha)) {
    res[p.type] = p.value;
  }
  return res;
}

/** `mar 24 sep · 18:00` */
export function fechaHoraCorta(iso: string | Date): string {
  const p = partes(iso, { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
  return `${(p.weekday ?? "").replace(".", "")} ${p.day} ${(p.month ?? "").replace(".", "")} · ${p.hour}:${p.minute}`;
}

/** `martes 24 de septiembre, 18:00` (+ ` a 19:00` si hay fin). */
export function fechaHoraLarga(inicio: string | Date, fin?: string | Date | null): string {
  const p = partes(inicio, { weekday: "long", day: "numeric", month: "long", hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
  let texto = `${p.weekday} ${p.day} de ${p.month}, ${p.hour}:${p.minute}`;
  if (fin) {
    const f = partes(fin, { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
    texto += ` a ${f.hour}:${f.minute}`;
  }
  return texto;
}

/** `jue 25 sep` */
export function diaCorto(iso: string | Date): string {
  const p = partes(iso, { weekday: "short", day: "numeric", month: "short" });
  return `${(p.weekday ?? "").replace(".", "")} ${p.day} ${(p.month ?? "").replace(".", "")}`;
}

/** `18:00` en hora argentina, 24 h. */
export function horaCorta(iso: string | Date): string {
  const p = partes(iso, { hour: "2-digit", minute: "2-digit", hourCycle: "h23" });
  return `${p.hour}:${p.minute}`;
}

/** Fecha `YYYY-MM-DD` en hora argentina (para comparar días sin líos de UTC). */
export function claveDia(iso: string | Date): string {
  const p = partes(iso, { year: "numeric", month: "2-digit", day: "2-digit" });
  return `${p.year}-${p.month}-${p.day}`;
}

/** "1 h 30 min", "45 min", "2 h". */
export function duracionLegible(minutos: number): string {
  const h = Math.floor(minutos / 60);
  const m = Math.round(minutos % 60);
  if (h && m) return `${h} h ${m} min`;
  if (h) return `${h} h`;
  return `${m} min`;
}

/** "en 3 h", "en 12 min", "hace 2 días" — para plazos y cuentas regresivas. */
export function tiempoRelativo(iso: string | Date, ahora: Date = new Date()): string {
  const ms = (typeof iso === "string" ? new Date(iso) : iso).getTime() - ahora.getTime();
  const rtf = new Intl.RelativeTimeFormat("es-AR", { numeric: "auto" });
  const abs = Math.abs(ms);
  const min = Math.round(ms / 60000);
  if (abs < 3600000) return rtf.format(min, "minute");
  const h = Math.round(ms / 3600000);
  if (abs < 86400000 * 2) return rtf.format(h, "hour");
  return rtf.format(Math.round(ms / 86400000), "day");
}

/** Hoy en hora local como `YYYY-MM-DD` (`toISOString` da la fecha UTC: de noche en Argentina ya es mañana). */
export function hoyIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}
