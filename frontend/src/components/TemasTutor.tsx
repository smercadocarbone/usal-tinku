"use client";

import { useEffect, useRef, useState } from "react";
import {
  getCatalogos,
  getMisTemas,
  setMisTemas,
  mensajeDeError,
  type NivelCatalogo,
} from "@/lib/api";

const NOMBRE_NIVEL: Record<string, string> = {
  primario: "Primario",
  secundario: "Secundario",
  universitario: "Universitario",
};

/**
 * Sección del perfil Tutor en /cuenta: árbol colapsable nivel → curso →
 * materia → temas con descripción inline y guardado automático (debounced)
 * contra el contrato 2b.
 */
export default function TemasTutor() {
  const [catalogos, setCatalogos] = useState<NivelCatalogo[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [errorPrecarga, setErrorPrecarga] = useState<string | null>(null);
  const [seleccion, setSeleccion] = useState<Set<string>>(new Set());
  const [abiertos, setAbiertos] = useState<Set<string>>(new Set());
  const [estado, setEstado] = useState<string | null>(null);

  // Contador de cambios hechos por el usuario: la precarga de getMisTemas()
  // setea `seleccion` sin incrementarlo, así el efecto de guardado no dispara
  // un PUT redundante apenas monta la sección.
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

  // Guardado automático con pequeño debounce: varios clicks rápidos coalescen
  // en un solo PUT con el estado final, sin requests fuera de orden.
  useEffect(() => {
    if (interaccion.current === 0) return;
    const id = setTimeout(() => {
      setEstado("Guardando…");
      setMisTemas(Array.from(seleccion))
        .then(() => setEstado("Cambios guardados."))
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
        <h2>Mis temas</h2>
        <p>Cargando el catálogo…</p>
      </section>
    );
  }

  if (!catalogos) {
    return (
      <section aria-label="Mis temas">
        <h2>Mis temas</h2>
        {errorPrecarga && (
          <div className="alerta alerta--error" role="alert">
            {errorPrecarga}
          </div>
        )}
      </section>
    );
  }

  return (
    <section aria-label="Mis temas">
      <h2>Mis temas</h2>
      <p>
        Elegí los temas que cubrís en tus tutorías. Se guardan solos y se usan
        para que tu perfil aparezca en las búsquedas.
      </p>

      {errorPrecarga && (
        <div className="alerta alerta--error" role="alert">
          {errorPrecarga}
        </div>
      )}

      <div className="arbol">
        {catalogos.map((nivel) => {
          const claveNivel = nivel.nivel;
          const abiertoNivel = abiertos.has(claveNivel);
          return (
            <div key={claveNivel}>
              <button
                type="button"
                className="arbol-fila"
                aria-expanded={abiertoNivel}
                onClick={() => alternarBloque(claveNivel)}
              >
                <span aria-hidden>{abiertoNivel ? "▾" : "▸"}</span>
                {NOMBRE_NIVEL[nivel.nivel] ?? nivel.nivel} ({nivel.cursos.length})
              </button>

              {abiertoNivel && (
                <div className="arbol-grupo">
                  {nivel.cursos.map((curso) => {
                    const claveCurso = `${claveNivel}|${curso.nombre}`;
                    const abiertoCurso = abiertos.has(claveCurso);
                    return (
                      <div key={claveCurso}>
                        <button
                          type="button"
                          className="arbol-fila"
                          aria-expanded={abiertoCurso}
                          onClick={() => alternarBloque(claveCurso)}
                        >
                          <span aria-hidden>{abiertoCurso ? "▾" : "▸"}</span>
                          {curso.nombre}
                        </button>

                        {abiertoCurso && (
                          <div className="arbol-grupo">
                            {curso.materias.map((materia) => {
                              const claveMateria = `${claveCurso}|${materia.nombre}`;
                              const abiertaMateria = abiertos.has(claveMateria);
                              return (
                                <div key={claveMateria}>
                                  <button
                                    type="button"
                                    className="arbol-fila"
                                    aria-expanded={abiertaMateria}
                                    onClick={() => alternarBloque(claveMateria)}
                                  >
                                    <span aria-hidden>
                                      {abiertaMateria ? "▾" : "▸"}
                                    </span>
                                    {materia.nombre}
                                  </button>

                                  {abiertaMateria && (
                                    <div className="arbol-grupo">
                                      {materia.temas.map((tema) => (
                                        <label
                                          key={tema.id}
                                          aria-label={tema.nombre}
                                          className="opcion arbol-tema-linea"
                                        >
                                          <input
                                            type="checkbox"
                                            checked={seleccion.has(tema.id)}
                                            onChange={() => alternarTema(tema.id)}
                                          />
                                          <span className="arbol-tema">
                                            <strong>{tema.nombre}</strong>
                                            <span className="arbol-descripcion">
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

      {estado && (
        <p
          className={
            estado === "Cambios guardados."
              ? "alerta alerta--exito"
              : estado.startsWith("Guardando")
                ? "arbol-estado"
                : "alerta alerta--error"
          }
          role={estado.startsWith("No se pudieron") ? "alert" : "status"}
        >
          {estado}
        </p>
      )}
    </section>
  );
}