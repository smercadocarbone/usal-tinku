"use client";

import { useState } from "react";
import { Star } from "lucide-react";
import { calificarSesion, mensajeDeError } from "@/lib/api";
import { Alerta, Boton } from "@/components/ui";
import { cn } from "@/lib/cn";

export interface FormularioCalificacionProps {
  sesionId: string;
}

/**
 * Calificación de la sesión (M7, US-2). La dirección (quién califica a quién)
 * la deriva el backend del rol del autor — este formulario es el mismo tanto
 * si lo completa el Estudiante/AR como el Tutor, nunca manda esa decisión.
 */
export default function FormularioCalificacion({ sesionId }: FormularioCalificacionProps) {
  const [estrellas, setEstrellas] = useState(0);
  const [estrellasHover, setEstrellasHover] = useState(0);
  const [comentario, setComentario] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [enviada, setEnviada] = useState(false);

  async function enviar() {
    if (estrellas < 1) {
      setError("Elegí una cantidad de estrellas antes de enviar.");
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      await calificarSesion(sesionId, { estrellas, comentario: comentario.trim() || undefined });
      setEnviada(true);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo enviar la calificación."));
    } finally {
      setEnviando(false);
    }
  }

  if (enviada) {
    return <Alerta tono="exito">¡Gracias! Tu calificación quedó registrada.</Alerta>;
  }

  const mostradas = estrellasHover || estrellas;

  return (
    <div className="flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <p className="text-sm font-semibold text-slate-800">¿Cómo estuvo la clase?</p>

      <div role="group" aria-label="Calificación en estrellas" className="flex gap-1">
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n}
            type="button"
            aria-pressed={estrellas === n}
            aria-label={`${n} estrella${n > 1 ? "s" : ""}`}
            onMouseEnter={() => setEstrellasHover(n)}
            onMouseLeave={() => setEstrellasHover(0)}
            onClick={() => setEstrellas(n)}
            className="cursor-pointer p-0.5"
          >
            <Star
              size={28}
              className={cn(
                n <= mostradas ? "fill-amber-400 text-amber-400" : "fill-none text-slate-300"
              )}
            />
          </button>
        ))}
      </div>

      <label htmlFor="comentarioCalificacion" className="text-sm font-semibold text-slate-800">
        Comentario (opcional)
      </label>
      <textarea
        id="comentarioCalificacion"
        rows={3}
        maxLength={500}
        value={comentario}
        onChange={(e) => setComentario(e.target.value)}
        className="w-full rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1"
      />

      {error && <Alerta tono="error">{error}</Alerta>}

      <Boton tamano="sm" className="w-fit" cargando={enviando} textoCargando="Enviando…" onClick={enviar}>
        Enviar calificación
      </Boton>
    </div>
  );
}
