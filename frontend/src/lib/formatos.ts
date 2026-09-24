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