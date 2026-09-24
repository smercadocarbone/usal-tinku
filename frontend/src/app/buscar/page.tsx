"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import Cabecera from "@/components/Cabecera";
import {
  api,
  buscarTutores,
  ejecutarBusquedaGuardada,
  getBusquedasGuardadas,
  getCatalogos,
  guardarBusqueda,
  mensajeDeError,
  type BusquedaGuardada,
  type NivelCatalogo,
  type ResultadoBusqueda,
} from "@/lib/api";
import { formatearPrecio } from "@/lib/formatos";
import { Alerta, Boton, Chip, EstadoVacio, Insignia, Skeleton, Tarjeta } from "@/components/ui";

/* ---- Contratos ---- */

interface TutorPerfil {
  id: string;
  nombre: string;
  apellido: string;
  materias: string[];
  calificacionPromedio: number | null;
  cantidadCalificaciones: number;
  precioHora?: number | null;
}

type TrustLevel = "bronce" | "plata" | "oro";

interface TutorResult {
  id: string;
  nombre: string;
  materias: string[];
  precioProrateado: number | null;
  trustLevel: TrustLevel | null;
  avatarUrl: string;
}

interface BusquedaFiltros {
  nivel?: string;
  materia?: string;
}

/* ---- Confianza derivada ---- */

/**
 * Deriva el nivel de confianza de las calificaciones públicas del perfil.
 * ponytail: regla provisional hasta que el backend exponga una señal de
 * confianza real. Usa la misma puerta que el perfil (>= 5 calificaciones
 * antes de mostrar promedio público, Spec_M7 o M1).
 */
function nivelConfianza(
  promedio: number | null,
  cantidad: number
): TrustLevel | null {
  if (promedio === null || cantidad < 5) return null;
  if (promedio >= 4.5) return "oro";
  if (promedio >= 4) return "plata";
  return "bronce";
}

const ETIQUETA_TRUST: Record<TrustLevel, string> = {
  bronce: "Bronce",
  plata: "Plata",
  oro: "Oro",
};

const COLOR_TRUST: Record<TrustLevel, string> = {
  bronce: "bg-orange-50 text-orange-700",
  plata: "bg-slate-100 text-slate-600",
  oro: "bg-amber-50 text-amber-700",
};

function TrustLevelBadge({ nivel }: { nivel: TrustLevel }) {
  return (
    <Insignia
      tono="neutro"
      className={COLOR_TRUST[nivel]}
      title={`Nivel de confianza: ${ETIQUETA_TRUST[nivel]}`}
    >
      <svg
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        className="h-3 w-3"
        aria-hidden="true"
      >
        <path
          d="M12 3l7 3v5c0 4.5-2.9 7.7-7 9-4.1-1.3-7-4.5-7-9V6l7-3z"
          strokeLinejoin="round"
        />
        <path d="m9 11.5 2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {ETIQUETA_TRUST[nivel]}
    </Insignia>
  );
}

/* ---- Tarjeta de Tutor ---- */

function TutorCard({
  tutor,
  noAutorizado,
  avisoActivo,
  onSolicitarAutorizacion,
}: {
  tutor: TutorResult;
  noAutorizado: boolean;
  avisoActivo: boolean;
  onSolicitarAutorizacion: (id: string) => void;
}) {
  const iniciales = tutor.nombre
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();

  return (
    <Tarjeta as="article" interactiva className="flex flex-col overflow-hidden p-0">
      <Link href={`/tutores/${tutor.id}`} className="block flex-1 p-5">
        <div className="flex items-center gap-3">
          {tutor.avatarUrl ? (
            <img
              src={tutor.avatarUrl}
              alt=""
              className="h-12 w-12 shrink-0 rounded-full object-cover"
            />
          ) : (
            <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-teal-50 text-sm font-bold text-teal-700">
              {iniciales}
            </span>
          )}

          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="truncate font-semibold text-slate-800">
                {tutor.nombre}
              </h2>
              {tutor.trustLevel && <TrustLevelBadge nivel={tutor.trustLevel} />}
            </div>
            {tutor.materias.length > 0 && (
              <div className="mt-1 flex flex-wrap gap-1">
                {tutor.materias.slice(0, 2).map((m) => (
                  <Insignia key={m} tono="neutro" className="px-2 py-0.5 font-normal">
                    {m}
                  </Insignia>
                ))}
                {tutor.materias.length > 2 && (
                  <Insignia tono="neutro" className="px-2 py-0.5 font-normal text-slate-500">
                    +{tutor.materias.length - 2}
                  </Insignia>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="mt-5 flex items-end justify-between">
          <div>
            <div className="text-xl font-bold text-slate-800">
              {tutor.precioProrateado !== null
                ? formatearPrecio(tutor.precioProrateado)
                : "A consultar"}
            </div>
            {tutor.precioProrateado !== null && (
              <div className="text-xs text-slate-500">por hora</div>
            )}
          </div>
          <span className="text-sm font-semibold text-teal-700">Ver perfil →</span>
        </div>
      </Link>

      {noAutorizado && (
        <div className="border-t border-slate-200 bg-slate-50 px-5 py-3">
          <Boton
            variante="secundario"
            tamano="sm"
            onClick={() => onSolicitarAutorizacion(tutor.id)}
          >
            Solicitar autorización
          </Boton>
          {avisoActivo && (
            <p
              className="mt-2 text-xs text-slate-500"
              role="status"
            >
              Tu adulto a cargo debe autorizar a este tutor para poder
              contactarte.
            </p>
          )}
        </div>
      )}
    </Tarjeta>
  );
}

/* ---- Skeleton de la grilla ---- */

function SearchResultsSkeleton() {
  return (
    <div
      className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3"
      role="status"
      aria-label="Cargando resultados"
    >
      {Array.from({ length: 6 }).map((_, i) => (
        // ponytail: grilla estática de carga — índice como key está bien
        // eslint-disable-next-line react/no-array-index-key
        <Tarjeta key={i} className="animate-pulse p-5">
          <div className="flex items-center gap-3">
            <Skeleton className="h-12 w-12 rounded-full" />
            <div className="flex-1 space-y-2">
              <Skeleton className="h-4 w-2/3" />
              <Skeleton className="h-3 w-1/3" />
            </div>
          </div>
          <Skeleton className="mt-4 h-3 w-1/2" />
          <Skeleton className="mt-3 h-3 w-1/3" />
        </Tarjeta>
      ))}
    </div>
  );
}

/* ---- Empty state ---- */

function SearchEmpty() {
  return (
    <EstadoVacio
      icono={
        <svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth={1.5}
          className="mx-auto h-12 w-12"
          aria-hidden="true"
        >
          <circle cx="11" cy="11" r="7" />
          <path d="m21 21-4.3-4.3" strokeLinecap="round" />
        </svg>
      }
    >
      No encontramos tutores exactos para esta búsqueda. Intenta usar
      palabras m&aacute;s generales o navega por las categorías.
    </EstadoVacio>
  );
}

/* ---- Contenedor de resultados ---- */

function SearchResults({
  results,
  noAutorizados,
  solicitadas,
  isLoading,
  onSolicitarAutorizacion,
}: {
  results: TutorResult[];
  noAutorizados: Record<string, boolean>;
  solicitadas: Set<string>;
  isLoading: boolean;
  onSolicitarAutorizacion: (id: string) => void;
}) {
  if (isLoading) return <SearchResultsSkeleton />;
  if (results.length === 0) return <SearchEmpty />;

  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
      {results.map((t) => (
        <TutorCard
          key={t.id}
          tutor={t}
          noAutorizado={noAutorizados[t.id] === true}
          avisoActivo={solicitadas.has(t.id)}
          onSolicitarAutorizacion={onSolicitarAutorizacion}
        />
      ))}
    </div>
  );
}

/* ---- Página ---- */

const ROTULO_NIVEL: Record<string, string> = {
  primario: "Primaria",
  secundario: "Secundaria",
  universitario: "Universidad",
};

function rotuloNivel(n: string): string {
  return ROTULO_NIVEL[n] ?? n.charAt(0).toUpperCase() + n.slice(1);
}

export default function BuscarPage() {
  const [catalogos, setCatalogos] = useState<NivelCatalogo[] | null>(null);
  const [errorCat, setErrorCat] = useState<string | null>(null);

  const [texto, setTexto] = useState("");
  const [nivel, setNivel] = useState("");
  const [materia, setMateria] = useState("");

  const [resultados, setResultados] = useState<TutorResult[] | null>(null);
  const [noAutorizados, setNoAutorizados] = useState<Record<string, boolean>>(
    {}
  );
  const [haBuscado, setHaBuscado] = useState(false);
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [solicitadas, setSolicitadas] = useState<Set<string>>(new Set());

  const [guardadas, setGuardadas] = useState<BusquedaGuardada[] | null>(null);
  const [guardandoBusqueda, setGuardandoBusqueda] = useState(false);
  const [errorGuardar, setErrorGuardar] = useState<string | null>(null);

  useEffect(() => {
    getBusquedasGuardadas()
      .then(setGuardadas)
      .catch(() => setGuardadas([]));
  }, []);

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

  const nivelSel = catalogos?.find((n) => n.nivel === nivel) ?? null;
  const materiasNivel = useMemo(() => {
    if (!nivelSel) return [];
    return [
      ...new Set(nivelSel.cursos.flatMap((c) => c.materias.map((m) => m.nombre))),
    ];
  }, [nivelSel]);

  /** Compartida entre una búsqueda nueva y la re-ejecución de una guardada:
   *  ambas devuelven la misma forma cruda y necesitan el mismo hidratado
   *  de perfiles. */
  async function hidratarYMostrar(lista: ResultadoBusqueda[]) {
    const perfiles = await Promise.allSettled(
      lista.map((r) => api.get<TutorPerfil>(`/api/tutores/${r.tutorId}`))
    );

    const tutorResults: TutorResult[] = lista.map((r, i) => {
      const perfil =
        perfiles[i].status === "fulfilled" ? perfiles[i].value : null;
      if (!perfil) {
        return {
          id: r.tutorId,
          nombre: `Tutor #${r.tutorId}`,
          materias: [],
          precioProrateado: null,
          trustLevel: null,
          avatarUrl: "",
        };
      }
      return {
        id: perfil.id,
        nombre: perfil.apellido
          ? `${perfil.nombre} ${perfil.apellido}`
          : perfil.nombre,
        materias: perfil.materias,
        precioProrateado: perfil.precioHora ?? null,
        trustLevel: nivelConfianza(
          perfil.calificacionPromedio,
          perfil.cantidadCalificaciones
        ),
        avatarUrl: "",
      };
    });

    setNoAutorizados(
      Object.fromEntries(lista.map((r) => [r.tutorId, r.noAutorizado]))
    );
    setResultados(tutorResults);
  }

  async function ejecutarBusqueda(filtros: BusquedaFiltros = {}) {
    const query = texto.trim();
    const filtroMateria = filtros.materia ?? materia;
    if (!query && !filtroMateria) {
      setError("Escribí qué necesitás para buscar o elegí una materia.");
      return;
    }

    setBuscando(true);
    setError(null);
    setResultados(null);
    setHaBuscado(true);
    try {
      const lista = await buscarTutores({
        textoBusqueda: query || undefined,
        filtroMateria: filtroMateria || undefined,
      });
      await hidratarYMostrar(lista);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo completar la búsqueda."));
    } finally {
      setBuscando(false);
    }
  }

  async function guardarBusquedaActual() {
    const query = texto.trim();
    if (!query && !materia) return;
    setGuardandoBusqueda(true);
    setErrorGuardar(null);
    try {
      const nueva = await guardarBusqueda({
        textoBusqueda: query || undefined,
        filtroMateria: materia || undefined,
      });
      setGuardadas((prev) => [nueva, ...(prev ?? [])]);
    } catch (err) {
      setErrorGuardar(mensajeDeError(err, "No se pudo guardar la búsqueda."));
    } finally {
      setGuardandoBusqueda(false);
    }
  }

  async function ejecutarGuardada(id: string) {
    setBuscando(true);
    setError(null);
    setResultados(null);
    setHaBuscado(true);
    try {
      const lista = await ejecutarBusquedaGuardada(id);
      await hidratarYMostrar(lista);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo ejecutar la búsqueda guardada."));
    } finally {
      setBuscando(false);
    }
  }

  function toggleNivel(n: string) {
    setMateria("");
    if (nivel === n) {
      setNivel("");
    } else {
      setNivel(n);
    }
  }

  function toggleMateria(m: string) {
    if (materia === m) {
      setMateria("");
      if (texto.trim()) {
        ejecutarBusqueda({ materia: "" });
      } else {
        setResultados(null);
        setHaBuscado(false);
      }
    } else {
      setMateria(m);
      ejecutarBusqueda({ materia: m });
    }
  }

  function toggleSolicitud(id: string) {
    setSolicitadas((prev) => {
      const s = new Set(prev);
      if (s.has(id)) {
        s.delete(id);
      } else {
        s.add(id);
      }
      return s;
    });
  }

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    ejecutarBusqueda();
  }

  return (
    <>
      <Cabecera enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]} />

      <main className="mx-auto max-w-6xl px-5 pb-16">
        <section className="mx-auto max-w-2xl pb-4 pt-10 text-center">
          <h1 className="text-3xl font-bold tracking-tight text-slate-800">
            Encontr&aacute; al tutor ideal
          </h1>
          <p className="mt-2 text-slate-500">
            Describí lo que necesit&aacute;s y te acercamos a los mejores
            tutores.
          </p>

          <form
            onSubmit={onSubmit}
            role="search"
            className="relative mt-6"
          >
            <span className="pointer-events-none absolute left-5 top-1/2 -translate-y-1/2 text-teal-700">
              <svg
                viewBox="0 0 24 24"
                fill="currentColor"
                className="h-5 w-5"
                aria-hidden="true"
              >
                <path d="M12 3l1.9 5.8a2 2 0 0 0 1.3 1.3L21 12l-5.8 1.9a2 2 0 0 0-1.3 1.3L12 21l-1.9-5.8a2 2 0 0 0-1.3-1.3L3 12l5.8-1.9a2 2 0 0 0 1.3-1.3L12 3z" />
              </svg>
            </span>
            <input
              type="text"
              value={texto}
              onChange={(e) => setTexto(e.target.value)}
              placeholder="Ej: repasar división para el secundario"
              maxLength={500}
              aria-label="Buscar tutores"
              className="w-full rounded-full border border-slate-200 bg-white py-3.5 pl-12 pr-32 text-base shadow-sm transition placeholder:text-slate-500 focus:shadow-lg focus:outline-none focus:ring-2 focus:ring-teal-500"
            />
            <Boton
              type="submit"
              cargando={buscando}
              textoCargando="Buscando…"
              className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-full px-5 text-sm"
            >
              Buscar
            </Boton>
          </form>

          <p className="mt-3 flex items-center justify-center gap-1.5 text-xs text-slate-500">
            <svg
              viewBox="0 0 24 24"
              fill="currentColor"
              className="h-3.5 w-3.5 text-teal-500"
              aria-hidden="true"
            >
              <path d="M12 3l1.9 5.8a2 2 0 0 0 1.3 1.3L21 12l-5.8 1.9a2 2 0 0 0-1.3 1.3L12 21l-1.9-5.8a2 2 0 0 0-1.3-1.3L3 12l5.8-1.9a2 2 0 0 0 1.3-1.3L12 3z" />
            </svg>
            Tutores recomendados para lo que necesitás
          </p>
        </section>

        <section className="mt-6">
          {errorCat && (
            <Alerta tono="error" className="mb-4">
              {errorCat}{" "}
              <Boton
                variante="secundario"
                tamano="sm"
                className="ml-2"
                onClick={cargarCatalogos}
              >
                Reintentar
              </Boton>
            </Alerta>
          )}

          <div className="no-scrollbar flex gap-2 overflow-x-auto py-1">
            {(catalogos ?? []).map((n) => (
              <Chip
                key={n.nivel}
                activo={nivel === n.nivel}
                onClick={() => toggleNivel(n.nivel)}
              >
                {rotuloNivel(n.nivel)}
              </Chip>
            ))}
          </div>

          {nivelSel && materiasNivel.length > 0 && (
            <div className="no-scrollbar mt-3 flex gap-2 overflow-x-auto py-1">
              {materiasNivel.map((m) => (
                <Chip key={m} activo={materia === m} onClick={() => toggleMateria(m)}>
                  {m}
                </Chip>
              ))}
            </div>
          )}

          {guardadas !== null && guardadas.length > 0 && (
            <div className="mt-4">
              <p className="text-xs font-semibold text-slate-500">Tus búsquedas guardadas</p>
              <div className="no-scrollbar mt-1.5 flex gap-2 overflow-x-auto py-1">
                {guardadas.map((g) => (
                  <Boton
                    key={g.id}
                    variante="secundario"
                    tamano="sm"
                    className="shrink-0 rounded-full"
                    onClick={() => ejecutarGuardada(g.id)}
                  >
                    {g.textoBusqueda}
                  </Boton>
                ))}
              </div>
            </div>
          )}
        </section>

        <section className="mt-8">
          {error && (
            <Alerta tono="error" className="mb-4">
              {error}
            </Alerta>
          )}

          {haBuscado && !buscando && (texto.trim() || materia) && (
            <div className="mb-4 flex items-center gap-2">
              <Boton
                variante="secundario"
                tamano="sm"
                cargando={guardandoBusqueda}
                textoCargando="Guardando…"
                onClick={guardarBusquedaActual}
              >
                Guardar esta búsqueda
              </Boton>
              {errorGuardar && <span className="text-sm text-red-700">{errorGuardar}</span>}
            </div>
          )}

          {buscando || haBuscado ? (
            <SearchResults
              results={resultados ?? []}
              noAutorizados={noAutorizados}
              solicitadas={solicitadas}
              isLoading={buscando}
              onSolicitarAutorizacion={toggleSolicitud}
            />
          ) : null}
        </section>
      </main>
    </>
  );
}