"use client";

import { useState } from "react";
import Link from "next/link";
import { api, ApiError } from "@/lib/api";
import Cabecera from "@/components/Cabecera";

interface ResultadoBusqueda {
  tutorId: string;
  score: number;
  noAutorizado: boolean;
}

interface TutorBasico {
  id: string;
  nombre: string;
  apellido: string;
}

interface BusquedaGuardada {
  id: string;
  textoBusqueda: string;
  createdAt: string;
}

export default function BuscarPage() {
  const [texto, setTexto] = useState("");
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resultados, setResultados] = useState<ResultadoBusqueda[]>([]);
  const [tutores, setTutores] = useState<Map<string, TutorBasico>>(new Map());
  const [buscado, setBuscado] = useState(false);

  const [guardadas, setGuardadas] = useState<BusquedaGuardada[]>([]);
  const [cargandoGuardadas, setCargandoGuardadas] = useState(false);
  const [mostrarGuardadas, setMostrarGuardadas] = useState(false);

  async function buscar(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!texto.trim()) return;

    setBuscando(true);
    setError(null);
    setResultados([]);
    setTutores(new Map());
    setBuscado(false);

    try {
      const lista = await api.post<ResultadoBusqueda[]>(
        "/api/busquedas",
        { textoBusqueda: texto.trim() }
      );
      setResultados(lista);
      setBuscado(true);

      const tutorIds = [...new Set(lista.map((r) => r.tutorId))];
      const nuevos = new Map<string, TutorBasico>();
      await Promise.allSettled(
        tutorIds.map(async (id) => {
          try {
            const t = await api.get<TutorBasico>(`/api/tutores/${id}`);
            nuevos.set(id, t);
          } catch {
            nuevos.set(id, { id, nombre: `Tutor #${id}`, apellido: "" });
          }
        })
      );
      setTutores(nuevos);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError("No se pudo completar la busqueda.");
      }
    } finally {
      setBuscando(false);
    }
  }

  async function guardarBusqueda() {
    if (!texto.trim()) return;
    try {
      const guardada = await api.post<BusquedaGuardada>(
        "/api/busquedas/guardadas",
        { textoBusqueda: texto.trim() }
      );
      setGuardadas((prev) => [guardada, ...prev]);
    } catch {
      // silencioso — no es crítico
    }
  }

  async function cargarGuardadas() {
    setCargandoGuardadas(true);
    try {
      const lista = await api.get<BusquedaGuardada[]>(
        "/api/busquedas/guardadas"
      );
      setGuardadas(lista);
      setMostrarGuardadas(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      }
    } finally {
      setCargandoGuardadas(false);
    }
  }

  async function ejecutarGuardada(guardada: BusquedaGuardada) {
    setTexto(guardada.textoBusqueda);
    setMostrarGuardadas(false);
    setBuscando(true);
    setError(null);
    setResultados([]);
    setTutores(new Map());
    setBuscado(false);

    try {
      const lista = await api.post<ResultadoBusqueda[]>(
        `/api/busquedas/guardadas/${guardada.id}/ejecutar`
      );
      setResultados(lista);
      setBuscado(true);

      const tutorIds = [...new Set(lista.map((r) => r.tutorId))];
      const nuevos = new Map<string, TutorBasico>();
      await Promise.allSettled(
        tutorIds.map(async (id) => {
          try {
            const t = await api.get<TutorBasico>(`/api/tutores/${id}`);
            nuevos.set(id, t);
          } catch {
            nuevos.set(id, { id, nombre: `Tutor #${id}`, apellido: "" });
          }
        })
      );
      setTutores(nuevos);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.message);
      }
    } finally {
      setBuscando(false);
    }
  }

  return (
    <>
      <Cabecera enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]} />

      <main className="mx-auto max-w-[44rem] px-5 py-8">
        <h1 className="text-[1.3rem] tracking-[-0.01em]">
          Buscar tutores
        </h1>
        <p className="text-texto-suave">
          Describe lo que necesitas y encontramos al Tutor mas relevante.
        </p>

        <form
          onSubmit={buscar}
          className="mb-6 flex gap-2"
        >
          <input
            type="text"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Ej: clases de matematica para secundario"
            required
            maxLength={500}
            className="flex-1 rounded-lg border border-borde bg-superficie px-3 py-[0.65rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1 disabled:cursor-not-allowed disabled:opacity-60"
          />
          <button
            type="submit"
            className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
            disabled={buscando || !texto.trim()}
          >
            {buscando ? "Buscando..." : "Buscar"}
          </button>
        </form>

        <div className="mb-6 flex gap-3">
          <button
            type="button"
            className="cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-2 text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-60"
            onClick={guardarBusqueda}
            disabled={!texto.trim() || buscando}
          >
            Guardar busqueda
          </button>
          <button
            type="button"
            className="cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-2 text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50 disabled:cursor-not-allowed disabled:opacity-60"
            onClick={mostrarGuardadas ? () => setMostrarGuardadas(false) : cargarGuardadas}
            disabled={cargandoGuardadas}
          >
            {cargandoGuardadas
              ? "Cargando..."
              : mostrarGuardadas
                ? "Ocultar guardadas"
                : "Ver guardadas"}
          </button>
        </div>

        {mostrarGuardadas && (
          <div className="mb-6">
            <h2 className="mb-2 text-base">Guardadas</h2>
            {guardadas.length === 0 ? (
              <p className="m-0 text-[0.9rem] text-texto-suave">
                No tenes busquedas guardadas.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {guardadas.map((g) => (
                  <button
                    key={g.id}
                    type="button"
                    onClick={() => ejecutarGuardada(g)}
                    aria-label={`Ejecutar busqueda guardada: ${g.textoBusqueda}`}
                    title={g.textoBusqueda}
                    className="max-w-full cursor-pointer overflow-hidden text-ellipsis whitespace-nowrap rounded-full border border-borde bg-superficie px-2.5 py-1.5 text-[0.8rem] font-semibold text-accent"
                  >
                    {g.textoBusqueda}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {error && (
          <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro" role="alert">
            {error}
          </div>
        )}

        {buscado && resultados.length === 0 && (
          <div
            role="status"
            className="px-4 py-8 text-center text-texto-suave"
          >
            No se encontraron tutores para esa busqueda.
          </div>
        )}

        {resultados.length > 0 && (
          <ul className="m-0 list-none p-0">
            {resultados.map((r) => {
              const tutor = tutores.get(r.tutorId);
              return (
                <li
                  key={r.tutorId}
                  className="mb-2 flex items-center justify-between rounded-tarjeta border border-borde bg-superficie p-4 shadow-tarjeta"
                >
                  <div>
                    <div className="font-semibold">
                      {tutor
                        ? tutor.apellido
                          ? `${tutor.nombre} ${tutor.apellido}`
                          : tutor.nombre
                        : "Cargando..."}
                    </div>
                    <div className="mt-[0.15rem] text-[0.8rem] text-texto-suave">
                      Relevancia: {Math.round(r.score * 100)}%
                    </div>
                  </div>
                  <div className="flex items-center gap-2">
                    {r.noAutorizado && (
                      <span className="text-[0.75rem] font-semibold text-aviso">
                        No autorizado
                      </span>
                    )}
                    <Link
                      href={`/tutores/${r.tutorId}`}
                      className="cursor-pointer rounded-lg border border-borde bg-transparent px-3 py-[0.4rem] text-[0.85rem] font-semibold text-accent enabled:hover:border-accent enabled:hover:bg-teal-50"
                    >
                      Ver perfil
                    </Link>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </main>
    </>
  );
}