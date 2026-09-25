"use client";

import { useEffect, useState } from "react";
import { Mic } from "lucide-react";
import { aceptarClausula, CLAUSULA_GRABACION, getClausula, mensajeDeError } from "@/lib/api";
import { Alerta, Boton, Cargando } from "@/components/ui";

/**
 * T08/T09 (ADR-M3-04): el Tutor habilita el resumen automático aceptando la grabación de solo
 * audio. Sin esto, sus alumnos no ven la opción al reservar. Nunca aplica a clases con menores.
 */
export default function SeccionResumen() {
  const [aceptada, setAceptada] = useState<boolean | undefined>(undefined);
  const [acepto, setAcepto] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getClausula(CLAUSULA_GRABACION)
      .then((c) => setAceptada(c.aceptada))
      .catch(() => setAceptada(false));
  }, []);

  async function habilitar() {
    setEnviando(true);
    setError(null);
    try {
      await aceptarClausula(CLAUSULA_GRABACION);
      setAceptada(true);
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos habilitar el resumen."));
    } finally {
      setEnviando(false);
    }
  }

  if (aceptada === undefined) return <Cargando>Cargando…</Cargando>;

  if (aceptada) {
    return (
      <p className="flex gap-2.5 text-[15px] text-tinta-suave">
        <Mic className="size-5 shrink-0 text-marca-700" aria-hidden />
        Habilitado. Tus alumnos adultos pueden agregar el resumen automático al reservar. En esas clases, tu navegador graba solo el audio y lo sube al terminar.
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-[15px] text-tinta-suave">
        Tus alumnos adultos pueden pagar un resumen automático de la clase. Para eso, en esas clases se graba <strong>solo el audio</strong> (nunca video), y se borra apenas se transcribe (24 hs como máximo). Nunca se ofrece en clases con menores.
      </p>
      <label className="flex cursor-pointer items-start gap-3 text-sm text-tinta-suave">
        <input type="checkbox" className="mt-1 size-4 accent-marca-600" checked={acepto} onChange={(e) => setAcepto(e.target.checked)} />
        <span>
          Acepto la grabación de solo audio en las clases donde el alumno contrate el resumen.
          <span className="mt-1 block text-xs text-tinta-tenue">Cláusula de los Términos: texto pendiente de revisión legal.</span>
        </span>
      </label>
      {error && <Alerta tono="peligro">{error}</Alerta>}
      <Boton className="self-start" disabled={!acepto} cargando={enviando} textoCargando="Habilitando…" onClick={habilitar}>
        Habilitar el resumen
      </Boton>
    </div>
  );
}
