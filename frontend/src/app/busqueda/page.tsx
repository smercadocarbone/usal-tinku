"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  buscarTutores,
  getCatalogos,
  mensajeDeError,
  type NivelCatalogo,
  type ResultadoBusqueda,
} from "@/lib/api";

function rotuloNivel(n: string): string {
  return n.charAt(0).toUpperCase() + n.slice(1);
}

export default function BusquedaPage() {
  const [catalogos, setCatalogos] = useState<NivelCatalogo[] | null>(null);
  const [errorCat, setErrorCat] = useState<string | null>(null);

  const [textoBusqueda, setTextoBusqueda] = useState("");
  const [nombre, setNombre] = useState("");
  const [nivel, setNivel] = useState("");
  const [curso, setCurso] = useState("");
  const [materia, setMateria] = useState("");

  const [resultados, setResultados] = useState<ResultadoBusqueda[] | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aviso, setAviso] = useState<string | null>(null);

  const nivelSel = catalogos?.find((n) => n.nivel === nivel) ?? null;
  const cursoSel = nivelSel?.cursos.find((c) => c.nombre === curso) ?? null;

  function cargarCatalogos() {
    setErrorCat(null);
    getCatalogos()
      .then(setCatalogos)
      .catch((err) =>
        setErrorCat(mensajeDeError(err, "No se pudo cargar el catálogo."))
      );
  }

  useEffect(() => {
    cargarCatalogos();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const texto = textoBusqueda.trim();
    const nom = nombre.trim();
    if (!texto && !nom && !materia) {
      setResultados(null);
      setError(
        "Completá al menos un campo: texto libre, nombre del tema o una materia."
      );
      return;
    }

    setBuscando(true);
    setError(null);
    setResultados(null);
    try {
      setResultados(
        await buscarTutores({
          textoBusqueda: texto || undefined,
          nombre: nom || undefined,
          filtroMateria: materia || undefined,
        })
      );
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo completar la búsqueda."));
    } finally {
      setBuscando(false);
    }
  }

  return (
    <>
      <header className="cabecera">
        <div className="marca" style={{ marginBottom: 0 }}>
          Tinku<span>.</span>
        </div>
        <Link
          href="/cuenta"
          className="boton boton--secundario"
          style={{ textDecoration: "none" }}
        >
          Mi cuenta
        </Link>
      </header>

      <main className="contenido">
        <h1>Buscar tutores</h1>
        <p>
          Buscá por texto libre, por nombre de tema o acotá por materia del
          catálogo.
        </p>

        {errorCat && !catalogos && (
          <div className="alerta alerta--error" role="alert">
            {errorCat}{" "}
            <button type="button" className="boton boton--secundario" onClick={cargarCatalogos}>
              Reintentar
            </button>
          </div>
        )}

        <form className="formulario" onSubmit={onSubmit}>
          <div className="campo">
            <label htmlFor="texto">Texto libre</label>
            <input
              id="texto"
              type="text"
              placeholder="Ej.: cómo dividir"
              value={textoBusqueda}
              onChange={(e) => setTextoBusqueda(e.target.value)}
            />
          </div>

          <div className="campo">
            <label htmlFor="nombre">Nombre del tema</label>
            <input
              id="nombre"
              type="text"
              placeholder="Ej.: División"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
            />
          </div>

          <div className="fila-selectores">
            <div className="campo">
              <label htmlFor="sel-nivel">Nivel</label>
              <select
                id="sel-nivel"
                value={nivel}
                onChange={(e) => {
                  setNivel(e.target.value);
                  setCurso("");
                  setMateria("");
                }}
              >
                <option value="">Todos</option>
                {(catalogos ?? []).map((n) => (
                  <option key={n.nivel} value={n.nivel}>
                    {rotuloNivel(n.nivel)}
                  </option>
                ))}
              </select>
            </div>

            <div className="campo">
              <label htmlFor="sel-curso">Curso / carrera</label>
              <select
                id="sel-curso"
                value={curso}
                disabled={!nivelSel}
                onChange={(e) => {
                  setCurso(e.target.value);
                  setMateria("");
                }}
              >
                <option value="">Todos</option>
                {nivelSel?.cursos.map((c) => (
                  <option key={c.nombre} value={c.nombre}>
                    {c.nombre}
                  </option>
                ))}
              </select>
            </div>

            <div className="campo">
              <label htmlFor="sel-materia">Materia</label>
              <select
                id="sel-materia"
                value={materia}
                disabled={!cursoSel}
                onChange={(e) => setMateria(e.target.value)}
              >
                <option value="">Todas</option>
                {cursoSel?.materias.map((m) => (
                  <option key={m.nombre} value={m.nombre}>
                    {m.nombre}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {error && (
            <div className="alerta alerta--error" role="alert">
              {error}
            </div>
          )}

          <button type="submit" className="boton" disabled={buscando}>
            {buscando ? "Buscando…" : "Buscar"}
          </button>
        </form>

        {resultados !== null && !buscando && (
          resultados.length === 0 ? (
            <p className="pie-enlace">No se encontraron resultados.</p>
          ) : (
            <div className="resultados">
              {resultados.map((r) => (
                <article className="resultado" key={r.tutorId}>
                  <h3>Tutor #{r.tutorId}</h3>
                  <p className="resultado-meta">
                    Coincidencia: {r.score.toFixed(2)}
                  </p>
                  {r.noAutorizado && (
                    <>
                      <button
                        type="button"
                        className="boton boton--secundario"
                        onClick={() =>
                          setAviso((prev) =>
                            prev === r.tutorId ? null : r.tutorId
                          )
                        }
                      >
                        {aviso === r.tutorId
                          ? "Ocultar aviso"
                          : "Solicitar autorización"}
                      </button>
                      {/* ponytail: TODO M3 — el aviso al Adulto Responsable se
                          implementa cuando exista el endpoint de solicitud;
                          este chunk no llama a la API. */}
                      {aviso === r.tutorId && (
                        <p className="alerta alerta--informativa" role="status">
                          Tu adulto a cargo debe autorizar a este tutor para
                          poder contactarte.
                        </p>
                      )}
                    </>
                  )}
                </article>
              ))}
            </div>
          )
        )}
      </main>
    </>
  );
}