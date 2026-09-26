"use client";

import { useEffect, useRef, useState } from "react";
import {
  getCatalogos,
  getMisTemas,
  setMisTemas,
  mensajeDeError,
  type NivelCatalogo,
} from "@/lib/api";
import { Alerta, Chip, IndicadorGuardado } from "@/components/ui";
import AsistenteMaterias from "@/components/tutor/AsistenteMaterias";

const GUARDADO_OK = "Cambios guardados.";

const NOMBRE_NIVEL: Record<string, string> = {
  primario: "Primario",
  secundario: "Secundario",
  universitario: "Universitario",
};

export default function TemasTutor() {
  const [catalogos, setCatalogos] = useState<NivelCatalogo[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [errorPrecarga, setErrorPrecarga] = useState<string | null>(null);
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set());
  const [abiertos, setAbiertos] = useState<Set<string>>(new Set());
  const [estado, setEstado] = useState<string | null>(null);

  const interaccion = useRef(0);

  useEffect(() => {
    let activo = true;
    getCatalogos()
      .then((c) => {
        if (!activo) return;
        setCatalogos(c);
        if (c[0]) setAbiertos(new Set([c[0].nivel]));
      })
      .catch((err) => {
        if (activo)
          setErrorPrecarga(
            mensajeDeError(err, "No se pudo cargar el catálogo de temas.")
          );
      })
      .finally(() => {
        if (activo) setCargando(false);
      });

    getMisTemas()
      .then((m) => {
        if (activo && interaccion.current === 0)
          setSeleccion(new Set(m.temaIds));
      })
      .catch((err) => {
        if (activo)
          setErrorPrecarga(
            mensajeDeError(err, "No se pudieron cargar tus temas guardados.")
          );
      });

    return () => {
      activo = false;
    };
  }, []);

  useEffect(() => {
    if (interaccion.current === 0) return;
    const id = setTimeout(() => {
      setEstado("Guardando…");
      setMisTemas(Array.from(seleccion))
        .then(() => setEstado(GUARDADO_OK))
        .catch((err) =>
          setEstado(
            mensajeDeError(err, "No se pudieron guardar los cambios. Intentá de nuevo.")
          )
        );
    }, 300);
    return () => clearTimeout(id);
  }, [seleccion]);

  function alternarTema(temaId: string) {
    interaccion.current += 1;
    setSeleccion((prev) => {
      const next = new Set(prev);
      if (next.has(temaId)) next.delete(temaId);
      else next.add(temaId);
      return next;
    });
  }

  function alternarBloque(clave: string) {
    setAbiertos((prev) => {
      const next = new Set(prev);
      if (next.has(clave)) next.delete(clave);
      else next.add(clave);
      return next;
    });
  }

  if (cargando) {
    return (
      <section aria-label="Mis temas">
        <h2 className="text-lg font-semibold text-slate-800">Mis temas</h2>
        <p className="mt-1 text-sm text-slate-500">Cargando el catálogo…</p>
      </section>
    );
  }

  if (!catalogos) {
    return (
      <section aria-label="Mis temas">
        <h2 className="text-lg font-semibold text-slate-800">Mis temas</h2>
        {errorPrecarga && <Alerta tono="error">{errorPrecarga}</Alerta>}
      </section>
    );
  }

  // Nombre de cada tema elegido, para el resumen de arriba.
  const nombres = new Map<string, string>();
  for (const n of catalogos) for (const c of n.cursos) for (const m of c.materias) for (const t of m.temas)
    nombres.set(t.id, `${t.nombre} (${m.nombre}, ${c.nombre})`);

  return (
    <section aria-label="Mis temas" className="flex flex-col gap-6">
      {errorPrecarga && <Alerta tono="error">{errorPrecarga}</Alerta>}

      <AsistenteMaterias seleccion={seleccion} onAlternar={alternarTema} />

      <div>
        <h3 className="text-lg font-bold">Tus temas ({seleccion.size})</h3>
        {seleccion.size === 0 ? (
          <p className="mt-1 text-sm text-tinta-suave">Todavía no elegiste ninguno. Sin temas no aparecés en las búsquedas.</p>
        ) : (
          <div className="mt-2 flex flex-wrap gap-2">
            {Array.from(seleccion).map((id) => (
              <Chip key={id} activo removible onClick={() => alternarTema(id)} aria-label={`Quitar ${nombres.get(id) ?? "tema"}`}>
                {nombres.get(id) ?? "Tema"}
              </Chip>
            ))}
          </div>
        )}
      </div>

      <div>
      <h3 className="text-lg font-bold">Todo el catálogo</h3>
      <p className="mt-1 text-sm text-tinta-suave">
        Si preferís, buscalos a mano. Se guardan solos y se usan para que tu perfil aparezca en las búsquedas.
      </p>
      <div className="mt-2">
        {catalogos.map((nivel) => {
          const claveNivel = nivel.nivel;
          const abiertoNivel = abiertos.has(claveNivel);
          return (
            <div key={claveNivel}>
              <button
                type="button"
                className="flex w-full cursor-pointer items-center gap-1.5 border-0 bg-transparent py-2 pl-0 pr-0 text-left text-base font-semibold text-slate-800 hover:text-teal-700"
                aria-expanded={abiertoNivel}
                onClick={() => alternarBloque(claveNivel)}
              >
                <span aria-hidden>{abiertoNivel ? "▾" : "▸"}</span>
                {NOMBRE_NIVEL[nivel.nivel] ?? nivel.nivel} ({nivel.cursos.length})
              </button>

              {abiertoNivel && (
                <div className="ml-4 border-l border-slate-200 pl-[0.6rem]">
                  {nivel.cursos.map((curso) => {
                    const claveCurso = `${claveNivel}|${curso.nombre}`;
                    const abiertoCurso = abiertos.has(claveCurso);
                    return (
                      <div key={claveCurso}>
                        <button
                          type="button"
                          className="flex w-full cursor-pointer items-center gap-1.5 border-0 bg-transparent py-2 pl-0 pr-0 text-left text-base font-semibold text-slate-800 hover:text-teal-700"
                          aria-expanded={abiertoCurso}
                          onClick={() => alternarBloque(claveCurso)}
                        >
                          <span aria-hidden>{abiertoCurso ? "▾" : "▸"}</span>
                          {curso.nombre}
                        </button>

                        {abiertoCurso && (
                          <div className="ml-4 border-l border-slate-200 pl-[0.6rem]">
                            {curso.materias.map((materia) => {
                              const claveMateria = `${claveCurso}|${materia.nombre}`;
                              const abiertaMateria = abiertos.has(claveMateria);
                              return (
                                <div key={claveMateria}>
                                  <button
                                    type="button"
                                    className="flex w-full cursor-pointer items-center gap-1.5 border-0 bg-transparent py-2 pl-0 pr-0 text-left text-base font-semibold text-slate-800 hover:text-teal-700"
                                    aria-expanded={abiertaMateria}
                                    onClick={() => alternarBloque(claveMateria)}
                                  >
                                    <span aria-hidden>
                                      {abiertaMateria ? "▾" : "▸"}
                                    </span>
                                    {materia.nombre}
                                  </button>

                                  {abiertaMateria && (
                                    <div className="ml-4 border-l border-slate-200 pl-[0.6rem]">
                                      {materia.temas.map((tema) => (
                                        <label
                                          key={tema.id}
                                          aria-label={tema.nombre}
                                          className="flex cursor-pointer items-start gap-2 text-sm"
                                        >
                                          <input
                                            type="checkbox"
                                            className="mt-1 accent-teal-600"
                                            checked={seleccion.has(tema.id)}
                                            onChange={() => alternarTema(tema.id)}
                                          />
                                          <span className="flex flex-col">
                                            <strong>{tema.nombre}</strong>
                                            <span className="text-xs text-slate-500">
                                              {tema.descripcion}
                                            </span>
                                          </span>
                                        </label>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </div>

      </div>

      {estado && (
        <IndicadorGuardado
          className="mt-2"
          estado={
            estado === GUARDADO_OK
              ? "ok"
              : estado.startsWith("Guardando")
                ? "guardando"
                : "error"
          }
          mensajeError={estado}
        />
      )}
    </section>
  );
}