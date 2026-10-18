"use client";

import { useEffect, useState } from "react";
import { Boton, Modal, useToast } from "@/components/ui";
import { mensajeDeError } from "@/lib/api";
import { npsPendiente, posponerNps, responderNps } from "@/lib/evaluacion";
import { cn } from "@/lib/cn";

const PUNTAJES = Array.from({ length: 11 }, (_, i) => i);

/**
 * T-TES-11b §4.2 (I-04): NPS in-app, una sola vez por usuario, después de su primera clase
 * terminada o calificada. Decide el backend (`/api/evaluacion/nps/pendiente`): con la evaluación
 * apagada o para una cuenta Menor nunca se ofrece. `esMenor` corta antes de preguntar (Art. II).
 */
export default function EncuestaNps({ esMenor }: { esMenor: boolean }) {
  const toast = useToast();
  const [abierto, setAbierto] = useState(false);
  const [puntaje, setPuntaje] = useState<number | null>(null);
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (esMenor) return;
    let vivo = true;
    void npsPendiente().then((ofrecer) => {
      if (vivo && ofrecer) setAbierto(true);
    });
    return () => {
      vivo = false;
    };
  }, [esMenor]);

  function ahoraNo() {
    posponerNps();
    setAbierto(false);
  }

  async function enviar() {
    if (puntaje === null) return;
    setEnviando(true);
    try {
      await responderNps(puntaje);
      setAbierto(false);
      toast.mostrar("¡Gracias por tu respuesta!");
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar tu respuesta."), { tono: "error" });
      setAbierto(false);
    } finally {
      setEnviando(false);
    }
  }

  if (esMenor) return null;

  return (
    <Modal
      abierto={abierto}
      onCerrar={ahoraNo}
      titulo="¿Recomendarías Tinku?"
      descripcion="¿Qué tan probable es que recomiendes Tinku a un amigo o familiar? 0 es nada probable y 10, muy probable."
      ancho="md"
      pie={
        <div className="flex w-full justify-end gap-2">
          <Boton variante="secundario" onClick={ahoraNo}>
            Ahora no
          </Boton>
          <Boton onClick={() => void enviar()} disabled={puntaje === null || enviando}>
            Enviar
          </Boton>
        </div>
      }
    >
      <div role="radiogroup" aria-label="Puntaje del 0 al 10" className="grid grid-cols-6 gap-2 sm:grid-cols-11">
        {PUNTAJES.map((n) => (
          <button
            key={n}
            type="button"
            role="radio"
            aria-checked={puntaje === n}
            aria-label={String(n)}
            onClick={() => setPuntaje(n)}
            className={cn(
              "h-11 rounded-control border text-base font-semibold",
              puntaje === n ? "border-marca-700 bg-marca-700 text-white" : "border-borde bg-superficie hover:border-marca-700"
            )}
          >
            {n}
          </button>
        ))}
      </div>
      <div className="mt-2 flex justify-between text-sm text-tinta-suave">
        <span>Nada probable</span>
        <span>Muy probable</span>
      </div>
    </Modal>
  );
}
