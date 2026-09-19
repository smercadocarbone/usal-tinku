export function formatearFecha(iso: string): string {
  return new Date(iso).toLocaleDateString("es-AR", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export function formatearHora(iso: string): string {
  return new Date(iso).toLocaleTimeString("es-AR", {
    hour: "2-digit",
    minute: "2-digit",
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
  });
}