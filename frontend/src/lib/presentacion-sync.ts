/**
 * Relevo en memoria para el "mando" de la presentación (/presentacion).
 *
 * Cada pantalla (la del proyector y el celular) se conecta por SSE a una sala identificada por
 * una clave corta y publica su "presencia" (el estado de la presentación). Es efímero a propósito:
 * no hay base de datos y no toca el backend. Funciona porque el frontend corre en UN solo proceso
 * (bun run start); con varias réplicas habría que moverlo a Redis.
 */
export type Presencia = Record<string, unknown>;
type Cliente = {
  id: string;
  sala: string;
  presencia: Presencia;
  enviar: (texto: string) => void;
};

const g = globalThis as unknown as { __presentacionClientes?: Map<string, Cliente> };
export const clientes: Map<string, Cliente> = (g.__presentacionClientes ??= new Map());

export const MAX_CLIENTES = 40;
export const MAX_BYTES_PRESENCIA = 4096;
export const RE_ID = /^[a-z0-9]{4,40}$/;
export const RE_SALA = /^[A-Za-z0-9]{1,16}$/;

export function difundir(sala: string) {
  const pares = [...clientes.values()]
    .filter((c) => c.sala === sala)
    .map((c) => ({ id: c.id, presence: c.presencia }));
  const msg = `event: peers\ndata: ${JSON.stringify(pares)}\n\n`;
  for (const c of clientes.values()) {
    if (c.sala !== sala) continue;
    try {
      c.enviar(msg);
    } catch {
      /* conexión caída: se limpia en su evento de cierre */
    }
  }
}
