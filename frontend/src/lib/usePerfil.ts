"use client";

import { useEffect, useState } from "react";
import { api, getPerfilPropio, type PerfilPropio, type RolAdmin } from "./api";
import { useSesion } from "./useSesion";

/*
 * Datos que la cabecera necesita en todas las pantallas (nombre para el avatar, si
 * es admin). Se piden UNA vez por carga de página y se comparten entre componentes:
 * sin esto, cada pantalla que monta la cabecera repetiría las dos requests.
 */
let perfilEnVuelo: Promise<PerfilPropio | null> | null = null;
let rolEnVuelo: Promise<RolAdmin | null> | null = null;
let tokenCache: string | null = null;

function reiniciarSiCambioElToken(token: string) {
  if (tokenCache !== token) {
    tokenCache = token;
    perfilEnVuelo = null;
    rolEnVuelo = null;
  }
}

export function usePerfilPropio(): PerfilPropio | null {
  const sesion = useSesion();
  const [perfil, setPerfil] = useState<PerfilPropio | null>(null);
  useEffect(() => {
    if (!sesion) return;
    reiniciarSiCambioElToken(sesion.token);
    perfilEnVuelo ??= getPerfilPropio().catch(() => null);
    let vivo = true;
    void perfilEnVuelo.then((p) => vivo && setPerfil(p));
    return () => {
      vivo = false;
    };
  }, [sesion]);
  return perfil;
}

/**
 * Rol de admin del usuario, o `null` si no es admin (403/404) o no hay sesión.
 * `fresco`: dentro de /admin se pregunta siempre al backend (el rol puede haber
 * cambiado); la cabecera usa el valor cacheado.
 */
export function useRolAdmin(fresco = false): RolAdmin | null {
  return useRolAdminResuelto(fresco).rol;
}

/**
 * Igual que {@link useRolAdmin}, pero dice además si ya se sabe la respuesta. Sin esto, una pantalla
 * de admin no distingue "todavía no sé" de "no es admin" y queda en "Cargando…" para siempre
 * (hallazgos T3-03 y T5-1 de las pruebas en producción).
 */
export function useRolAdminResuelto(fresco = false): { rol: RolAdmin | null; resuelto: boolean } {
  const sesion = useSesion();
  const [rol, setRol] = useState<RolAdmin | null>(null);
  const [resuelto, setResuelto] = useState(false);
  useEffect(() => {
    // Un Menor nunca es admin: no hace falta preguntar.
    // useSesion devuelve null en el primer render: sin sesión todavía no se sabe nada.
    if (!sesion) return;
    if (sesion.payload.tipo === "MENOR") {
      setRol(null);
      setResuelto(true);
      return;
    }
    reiniciarSiCambioElToken(sesion.token);
    // R-B B-10 / R-G G-9 (T1-7, T2-H12): fuera del panel el rol sale de /api/usuarios/me
    // (`rolAdmin`), que la cabecera ya pide; antes cada pantalla de quien no es admin dejaba un
    // 403 de /api/admin/yo en la consola. Dentro de /admin (`fresco`) se sigue preguntando al panel.
    if (fresco) rolEnVuelo = null;
    rolEnVuelo ??= fresco
      ? api
          .get<{ rol: RolAdmin }>("/api/admin/yo")
          .then((r) => r.rol)
          .catch(() => null)
      : (perfilEnVuelo ??= getPerfilPropio().catch(() => null)).then((p) => p?.rolAdmin ?? null);
    let vivo = true;
    void rolEnVuelo.then((r) => {
      if (!vivo) return;
      setRol(r);
      setResuelto(true);
    });
    return () => {
      vivo = false;
    };
  }, [sesion, fresco]);
  return { rol, resuelto };
}
