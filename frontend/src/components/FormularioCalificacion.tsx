"use client";

import { useEffect, useState } from "react";
import { Star } from "lucide-react";
import {
  calificarSesion,
  editarCalificacion,
  eliminarCalificacion,
  getMiCalificacion,
  mensajeDeError,
  type CalificacionCreada,
} from "@/lib/api";
import { Alerta, AreaTexto, Boton, Cargando } from "@/components/ui";
import { cn } from "@/lib/cn";

export interface FormularioCalificacionProps {
  sesionId: string;
}

function Estrellas({
  valor,
  onCambiar,
}: {
  valor: number;
  onCambiar?: (n: number) => void;
}) {
  const [hover, setHover] = useState(0);
  const soloLectura = !onCambiar;
  const mostradas = hover || valor;
  return (
    <div role="group" aria-label="Calificación en estrellas" className="flex gap-1">
      {[1, 2, 3, 4, 5].map((n) => (
        <button
          key={n}
          type="button"
          aria-pressed={valor === n}
          aria-label={`${n} estrella${n > 1 ? "s" : ""}`}
          disabled={soloLectura}
          onMouseEnter={() => !soloLectura && setHover(n)}
          onMouseLeave={() => !soloLectura && setHover(0)}
          onClick={() => onCambiar?.(n)}
          className={cn("rounded-full p-1", soloLectura ? "cursor-default" : "cursor-pointer motion-safe:hover:scale-110 transition-transform")}
        >
          <Star
            size={34}
            className={cn(n <= mostradas ? "fill-acento-500 text-acento-500" : "fill-none text-borde-control")}
          />
        </button>
      ))}
    </div>
  );
}

/**
 * Calificación de la sesión (M7, US-2). La dirección (quién califica a quién)
 * la deriva el backend del rol del autor — este formulario es el mismo tanto
 * si lo completa el Estudiante/AR como el Tutor, nunca manda esa decisión.
 *
 * Auditoría 2026-09-20: antes esto era solo un formulario de alta — sin
 * recuperar la propia calificación (GET /api/sesiones/{id}/calificacion), un
 * usuario que volvía a esta pantalla no veía lo que ya calificó, no podía
 * editarlo ni borrarlo, y reintentar enviarlo fallaba con 422 recién al
 * completar el formulario vacío de nuevo.
 */
export default function FormularioCalificacion({ sesionId }: FormularioCalificacionProps) {
  const [cargando, setCargando] = useState(true);
  const [calificacion, setCalificacion] = useState<CalificacionCreada | null>(null);
  const [editando, setEditando] = useState(false);

  const [estrellas, setEstrellas] = useState(0);
  const [comentario, setComentario] = useState("");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [puedeEditar, setPuedeEditar] = useState(false);

  useEffect(() => {
    getMiCalificacion(sesionId)
      .then(setCalificacion)
      .catch(() => setCalificacion(null))
      .finally(() => setCargando(false));
  }, [sesionId]);

  // `Date.now()` es impuro — se calcula en un efecto (al cargar/guardar la
  // calificación), nunca durante el render.
  useEffect(() => {
    setPuedeEditar(
      calificacion !== null && Date.now() < new Date(calificacion.editableHasta).getTime()
    );
  }, [calificacion]);

  function abrirEdicion() {
    if (!calificacion) return;
    setEstrellas(calificacion.estrellas);
    setComentario(calificacion.comentario ?? "");
    setError(null);
    setEditando(true);
  }

  async function enviar() {
    if (estrellas < 1) {
      setError("Elegí una cantidad de estrellas antes de enviar.");
      return;
    }
    setEnviando(true);
    setError(null);
    try {
      const cuerpo = { estrellas, comentario: comentario.trim() || undefined };
      const resultado = calificacion
        ? await editarCalificacion(calificacion.id, cuerpo)
        : await calificarSesion(sesionId, cuerpo);
      setCalificacion(resultado);
      setEditando(false);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo guardar la calificación."));
    } finally {
      setEnviando(false);
    }
  }

  async function eliminar() {
    if (!calificacion) return;
    if (!window.confirm("¿Borrar tu calificación?")) return;
    setEnviando(true);
    setError(null);
    try {
      await eliminarCalificacion(calificacion.id);
      setCalificacion(null);
      setEstrellas(0);
      setComentario("");
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo borrar la calificación."));
    } finally {
      setEnviando(false);
    }
  }

  if (cargando) {
    return <Cargando>Cargando calificación…</Cargando>;
  }

  if (calificacion && !editando) {
    return (
      <div className="flex flex-col gap-4 rounded-tarjeta border border-borde bg-superficie p-5 shadow-elevado">
        <h2 className="text-lg font-bold">Tu calificación</h2>
        <Estrellas valor={calificacion.estrellas} />
        {calificacion.comentario && <p className="text-[15px] text-tinta-suave">{calificacion.comentario}</p>}
        {error && <Alerta tono="peligro">{error}</Alerta>}
        {puedeEditar && (
          <div className="flex gap-2">
            <Boton variante="secundario" tamano="sm" onClick={abrirEdicion}>
              Editar
            </Boton>
            <Boton
              variante="peligro"
              tamano="sm"
              cargando={enviando}
              textoCargando="Borrando…"
              onClick={eliminar}
            >
              Borrar
            </Boton>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-4 rounded-tarjeta border border-borde bg-superficie p-5 shadow-elevado">
      <h2 className="text-lg font-bold">¿Cómo estuvo la clase?</h2>
      <Estrellas valor={estrellas} onCambiar={setEstrellas} />

      <AreaTexto
        id="comentarioCalificacion"
        etiqueta="Comentario (opcional)"
        rows={3}
        maxLength={500}
        contador
        value={comentario}
        onChange={(e) => setComentario(e.target.value)}
      />

      {error && <Alerta tono="peligro">{error}</Alerta>}

      <div className="flex gap-2">
        <Boton tamano="sm" className="w-fit" cargando={enviando} textoCargando="Enviando…" onClick={enviar}>
          {editando ? "Guardar cambios" : "Enviar calificación"}
        </Boton>
        {editando && (
          <Boton variante="secundario" tamano="sm" onClick={() => setEditando(false)}>
            Cancelar
          </Boton>
        )}
      </div>
    </div>
  );
}
