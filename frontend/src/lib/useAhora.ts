"use client";

import { useEffect, useState } from "react";

/**
 * "Ahora" para cuentas regresivas y comparaciones con la hora actual. Arranca en 0
 * (mismo valor en servidor y cliente: sin error de hidratación, B3) y se actualiza
 * tras el montaje cada `cadaMs`.
 */
export function useAhora(cadaMs = 30_000): number {
  const [ahora, setAhora] = useState(0);
  useEffect(() => {
    setAhora(Date.now());
    const id = window.setInterval(() => setAhora(Date.now()), cadaMs);
    return () => window.clearInterval(id);
  }, [cadaMs]);
  return ahora;
}
