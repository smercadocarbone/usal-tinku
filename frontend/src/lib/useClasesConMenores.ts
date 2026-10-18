"use client";

import { useEffect, useState } from "react";
import { getClasesConMenoresHabilitadas } from "@/lib/api";

/**
 * T-TES-10: si las clases con menores están habilitadas (flag del backend). `null` mientras carga.
 * Solo consulta con `activo` (p. ej. sesión de Menor). Fail-closed: si la consulta falla, `false`.
 */
export function useClasesConMenores(activo: boolean): boolean | null {
  const [habilitadas, setHabilitadas] = useState<boolean | null>(null);
  useEffect(() => {
    if (!activo) return;
    let vigente = true;
    getClasesConMenoresHabilitadas().then((h) => {
      if (vigente) setHabilitadas(h);
    });
    return () => {
      vigente = false;
    };
  }, [activo]);
  return habilitadas;
}
