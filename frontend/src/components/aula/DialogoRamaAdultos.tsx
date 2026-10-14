"use client";

import { useEffect, useState } from "react";
import { Boton, Modal } from "@/components/ui";

/**
 * US-7: pregunta al otro participante si vio algo incorrecto o ilegal. El foco inicial está en "No",
 * Escape (o cerrar) cuenta como "No", y al llegar a `venceEn` se resuelve sola como "No" (el job del
 * backend ya la venció del otro lado).
 */
export default function DialogoRamaAdultos({
  abierto,
  nombre,
  venceEn,
  onResponder,
  onVencio,
}: {
  abierto: boolean;
  nombre: string;
  /** Instante (ms epoch) en que vence la pregunta. */
  venceEn: number;
  onResponder: (vio: boolean) => void;
  onVencio: () => void;
}) {
  const [restanteMs, setRestanteMs] = useState(() => Math.max(0, venceEn - Date.now()));

  useEffect(() => {
    if (!abierto) return;
    const tick = () => {
      const r = venceEn - Date.now();
      setRestanteMs(Math.max(0, r));
      if (r <= 0) onVencio();
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto, venceEn]);

  // Foco inicial en "No" (la opción segura): showModal() enfocaría el botón de cerrar.
  useEffect(() => {
    if (!abierto) return;
    const id = window.setTimeout(() => document.querySelector<HTMLElement>("[data-foco-no]")?.focus(), 0);
    return () => window.clearTimeout(id);
  }, [abierto]);

  const seg = Math.ceil(restanteMs / 1000);
  const reloj = `${Math.floor(seg / 60)}:${String(seg % 60).padStart(2, "0")}`;

  return (
    <Modal
      abierto={abierto}
      onCerrar={() => onResponder(false)}
      titulo={`¿Viste algo incorrecto o ilegal en el video de ${nombre || "la otra persona"}?`}
      variante="centrado"
      ancho="sm"
      pie={
        <>
          <Boton variante="peligro" onClick={() => onResponder(true)}>
            Sí
          </Boton>
          <Boton variante="secundario" onClick={() => onResponder(false)} data-foco-no>
            No
          </Boton>
        </>
      }
    >
      <p className="text-[15px] text-tinta-suave">
        Su video está oculto mientras respondés. Si no respondés, se toma como «No» en{" "}
        <span aria-live="off" className="font-semibold tabular-nums">
          {reloj}
        </span>
        .
      </p>
    </Modal>
  );
}
