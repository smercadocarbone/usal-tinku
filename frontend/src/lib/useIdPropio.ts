"use client";

import { usePerfilPropio } from "./usePerfil";

/**
 * Id (UUID) del usuario logueado. B4: el `sub` del JWT es hoy el DNI, no el id —
 * hasta FASE3-03 el id sale de GET /api/usuarios/me.
 */
export function useIdPropio(): string | null {
  return usePerfilPropio()?.id ?? null;
}
