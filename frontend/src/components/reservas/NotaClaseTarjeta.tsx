"use client";

import { useEffect, useState } from "react";
import { NotebookPen } from "lucide-react";
import { mensajeDeError } from "@/lib/api";
import { escribirNota, getNota, type NotaClase } from "@/lib/reservas";
import { TIEMPOS } from "@/lib/tiempos";
import { AreaTexto, Boton, Tarjeta, useToast } from "@/components/ui";

const MAX_TEXTO = 1000;

export interface NotaClaseTarjetaProps {
  reservaId: string;
  /** El Tutor la escribe; el Adulto Responsable (quien pagó) la lee. */
  soyTutor: boolean;
  /** La clase terminó: recién ahí el Tutor puede escribir. */
  finalizada: boolean;
  nombreAlumno?: string | null;
}

/**
 * Nota del Tutor al Adulto Responsable en una clase con un Menor (FR-RES-026). No es un resumen
 * automático ni una grabación: es un texto corto que escribe el Tutor, filtrado.
 */
export default function NotaClaseTarjeta({ reservaId, soyTutor, finalizada, nombreAlumno }: NotaClaseTarjetaProps) {
  const toast = useToast();
  const [nota, setNota] = useState<NotaClase | null | undefined>(undefined);
  const [texto, setTexto] = useState("");
  const [editando, setEditando] = useState(false);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getNota(reservaId)
      .then((n) => setNota(n ?? null))
      .catch(() => setNota(null));
  }, [reservaId]);

  if (nota === undefined) return null;
  if (!soyTutor && !nota) return null;
  if (soyTutor && !finalizada) return null;

  async function guardar() {
    setGuardando(true);
    setError(null);
    try {
      setNota(await escribirNota(reservaId, texto));
      setEditando(false);
      toast.mostrar(nota ? "Actualizamos la nota" : "Le mandamos la nota a su adulto responsable");
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos guardar la nota."));
    } finally {
      setGuardando(false);
    }
  }

  const alumno = nombreAlumno ?? "el alumno";

  return (
    <Tarjeta className="mt-10" aria-labelledby="titulo-nota">
      <h2 id="titulo-nota" className="flex items-center gap-2 text-lg font-bold">
        <NotebookPen className="size-5 text-marca-700" aria-hidden />
        {soyTutor ? "Nota para su adulto responsable" : "Nota del tutor sobre la clase"}
      </h2>
      {editando || (soyTutor && !nota) ? (
        <div className="mt-4 flex flex-col gap-3">
          <AreaTexto
            id="nota-texto"
            etiqueta={`Cómo le fue a ${alumno}`}
            etiquetaOculta={!!nota}
            ayuda={`Qué vieron, qué le cuesta y qué conviene practicar. La lee su adulto responsable; la podés corregir durante ${TIEMPOS.edicionNotaHoras} hs.`}
            value={texto}
            maxLength={MAX_TEXTO}
            contador
            onChange={(e) => setTexto(e.target.value)}
          />
          {error && <p className="text-sm font-semibold text-peligro" role="alert">{error}</p>}
          <div className="flex flex-col gap-2 sm:flex-row">
            <Boton onClick={() => void guardar()} cargando={guardando} textoCargando="Guardando…" disabled={!texto.trim()}>
              {nota ? "Guardar cambios" : "Mandar la nota"}
            </Boton>
            {nota && (
              <Boton variante="secundario" onClick={() => setEditando(false)}>
                Cancelar
              </Boton>
            )}
          </div>
        </div>
      ) : nota ? (
        <div className="mt-3 flex flex-col gap-3">
          <p className="whitespace-pre-line text-[15px] leading-relaxed text-tinta-suave">{nota.texto}</p>
          {soyTutor && nota.editable && (
            <Boton
              variante="secundario"
              className="w-fit"
              onClick={() => {
                setTexto(nota.texto);
                setEditando(true);
              }}
            >
              Corregir la nota
            </Boton>
          )}
        </div>
      ) : null}
    </Tarjeta>
  );
}
