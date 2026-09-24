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

/** Rol de admin del usuario, o `null` si no es admin (403/404) o no hay sesión. */
export function useRolAdmin(): RolAdmin | null {
  const sesion = useSesion();
  const [rol, setRol] = useState<RolAdmin | null>(null);
  useEffect(() => {
    // Un Menor nunca es admin: no hace falta preguntar.
    if (!sesion || sesion.payload.tipo === "MENOR") return;
    reiniciarSiCambioElToken(sesion.token);
    // Cacheado por token en sessionStorage: sin esto, cada navegación de un usuario
    // que no es admin dejaría un 403 en la consola.
    const clave = `tinku_rol_admin:${sesion.token.slice(-24)}`;
    let guardado: string | null = null;
    try {
      guardado = window.sessionStorage.getItem(clave);
    } catch {
      /* almacenamiento bloqueado: se pregunta al backend */
    }
    rolEnVuelo ??=
      guardado !== null
        ? Promise.resolve(guardado === "" ? null : (guardado as RolAdmin))
        : api
            .get<{ rol: RolAdmin }>("/api/admin/yo")
            .then((r) => r.rol)
            .catch(() => null)
            .then((r) => {
              try {
                window.sessionStorage.setItem(clave, r ?? "");
              } catch {
                /* sin cache, no pasa nada */
              }
              return r;
            });
    let vivo = true;
    void rolEnVuelo.then((r) => vivo && setRol(r));
    return () => {
      vivo = false;
    };
  }, [sesion]);
  return rol;
}
