"use client";

import { useEffect, useState } from "react";
import { getSession, type Sesion } from "./auth";

/**
 * Lee la sesión de forma segura para render (B3).
 *
 * Con `getSession()` directo en el render el servidor pinta sin token
 * (localStorage no existe ahí) y el primer render del cliente pinta con
 * token → mismatch de hidratación. Este hook devuelve `null` también en el
 * primer render del cliente (snapshot estable) y recién en `useEffect` lee
 * la sesión real. Sin error de hidratación; los datos dependientes del
 * usuario se pintan tras el montaje.
 */
export function useSesion(): Sesion | null {
  const [sesion, setSesion] = useState<Sesion | null>(null);

  useEffect(() => {
    setSesion(getSession());
  }, []);

  return sesion;
}