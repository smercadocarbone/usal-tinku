/**
 * R-C C-1 (H-AULA-1): qué pantalla mostrar cuando la sala se desconecta, según el estado de la
 * sesión en el backend (`GET /api/sesiones/{id}/killswitch`). El aviso `corte-seguridad` entre
 * pares sigue siendo el camino rápido; esto cubre cuando ese aviso no llegó.
 */
export type CierreAula = "seguridad" | "finalizada" | "esperando";

export function resolverCierre(estado: { cortePorSeguridad?: boolean; sesionCerrada?: boolean } | null): CierreAula {
  if (estado?.cortePorSeguridad) return "seguridad";
  if (estado?.sesionCerrada) return "finalizada";
  return "esperando";
}
