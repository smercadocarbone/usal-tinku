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
      <header className="flex items-center justify-between border-b border-borde bg-superficie px-5 py-[0.9rem]">
        <div className="text-[1.05rem] font-bold text-texto">
          Tinku<span className="text-accent">.</span>
        </div>
        <Link
          href="/cuenta"
          className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
        >
          Mi cuenta
        </Link>
      </header>

      <main className="mx-auto max-w-[44rem] px-5 py-8">
        <h1 className="mb-1 text-[1.4rem] tracking-[-0.01em]">Buscar tutores</h1>
        <p className="mb-6 text-texto-suave">
          Buscá por texto libre, por nombre de tema o acotá por materia del
          catálogo.
        </p>

        {errorCat && !catalogos && (
          <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
            {errorCat}{" "}
            <button
              type="button"
              className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
              onClick={cargarCatalogos}
            >
              Reintentar
            </button>
          </div>
        )}

        <form className="flex flex-col gap-4" onSubmit={onSubmit}>
          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="texto" className="text-[0.85rem] font-semibold">Texto libre</label>
            <input
              id="texto"
              type="text"
              placeholder="Ej.: cómo dividir"
              value={textoBusqueda}
              onChange={(e) => setTextoBusqueda(e.target.value)}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          <div className="flex flex-col gap-[0.35rem]">
            <label htmlFor="nombre" className="text-[0.85rem] font-semibold">Nombre del tema</label>
            <input
              id="nombre"
              type="text"
              placeholder="Ej.: División"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
            />
          </div>

          <div className="grid grid-cols-[repeat(auto-fit,minmax(9rem,1fr))] gap-3">
            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="sel-nivel" className="text-[0.85rem] font-semibold">Nivel</label>
              <select
                id="sel-nivel"
                value={nivel}
                onChange={(e) => {
                  setNivel(e.target.value);
                  setCurso("");
                  setMateria("");
                }}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <option value="">Todos</option>
                {(catalogos ?? []).map((n) => (
                  <option key={n.nivel} value={n.nivel}>
                    {rotuloNivel(n.nivel)}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="sel-curso" className="text-[0.85rem] font-semibold">Curso / carrera</label>
              <select
                id="sel-curso"
                value={curso}
                disabled={!nivelSel}
                onChange={(e) => {
                  setCurso(e.target.value);
                  setMateria("");
                }}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <option value="">Todos</option>
                {nivelSel?.cursos.map((c) => (
                  <option key={c.nombre} value={c.nombre}>
                    {c.nombre}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex flex-col gap-[0.35rem]">
              <label htmlFor="sel-materia" className="text-[0.85rem] font-semibold">Materia</label>
              <select
                id="sel-materia"
                value={materia}
                disabled={!cursoSel}
                onChange={(e) => setMateria(e.target.value)}
                className="w-full rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
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
            <div className="rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
              {error}
            </div>
          )}

          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={buscando}
          >
            {buscando ? "Buscando…" : "Buscar"}
          </button>
        </form>

        {resultados !== null && !buscando && (
          resultados.length === 0 ? (
            <p className="mt-5 text-center text-[0.9rem] text-texto-suave">No se encontraron resultados.</p>
          ) : (
            <div className="mt-6 flex flex-col gap-3">
              {resultados.map((r) => (
                <article
                  className="rounded-tarjeta border border-borde bg-superficie px-5 py-4 shadow-tarjeta"
                  key={r.tutorId}
                >
                  <h3 className="mb-1 text-[1.05rem]">Tutor #{r.tutorId}</h3>
                  <p className="mb-3 text-[0.9rem] text-texto-suave">
                    Coincidencia: {r.score.toFixed(2)}
                  </p>
                  {r.noAutorizado && (
                    <>
                      <button
                        type="button"
                        className="cursor-pointer rounded-lg border border-borde bg-transparent px-4 py-[0.65rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
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
                        <p className="mt-2 rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
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