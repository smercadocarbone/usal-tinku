"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ArrowUpDown, Bookmark, BookmarkCheck, History, Search, SearchX, SlidersHorizontal, Sparkles } from "lucide-react";
import AppShell from "@/components/shell/AppShell";
import TarjetaTutor from "@/components/tutores/TarjetaTutor";
import {
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
import { getTutor, normalizarTutor, type TutorPerfil } from "@/lib/tutores";
import { useToast } from "@/components/ui";
import { Alerta, Boton, Chip, EstadoVacio, Modal, SkeletonTarjetas } from "@/components/ui";
import { cn } from "@/lib/cn";

type Orden = "relevancia" | "precio" | "calificacion";

const ORDENES: { id: Orden; label: string }[] = [
  { id: "relevancia", label: "Más relevantes" },
  { id: "precio", label: "Menor precio" },
  { id: "calificacion", label: "Mejor calificados" },
];

const MATERIAS_SUGERIDAS = ["Matemática", "Física", "Química", "Inglés", "Lengua", "Historia", "Programación"];

const ROTULO_NIVEL: Record<string, string> = {
  primario: "Primaria",
  secundario: "Secundaria",
  universitario: "Universidad",
};

function rotuloNivel(n: string): string {
  return ROTULO_NIVEL[n] ?? n.charAt(0).toUpperCase() + n.slice(1);
}

interface Resultado {
  tutor: TutorPerfil;
  noAutorizado: boolean;
  score: number;
}

/** "Encontramos 3 tutores para ayudarte con divisiones en primario", sin contadores robóticos. */
function resumenResultados(
  cantidad: number,
  consulta: { texto: string; materia: string } | null,
  area: string | null
): React.ReactNode {
  if (cantidad === 0) return null;
  const tutores = cantidad === 1 ? "un tutor" : `${cantidad} tutores`;
  if (area) return <>Te recomendamos {tutores} de {area}</>;
  const tema = consulta?.texto || consulta?.materia;
  return tema ? (
    <>
      Encontramos {tutores} para ayudarte con <strong className="text-tinta">{tema}</strong>
    </>
  ) : (
    <>Encontramos {tutores}</>
  );
}

export default function BuscarPage() {
  const toast = useToast();
  const [catalogos, setCatalogos] = useState<NivelCatalogo[] | null>(null);
  const [errorCat, setErrorCat] = useState<string | null>(null);

  const [texto, setTexto] = useState("");
  const [nivel, setNivel] = useState("");
  const [materia, setMateria] = useState("");
  const [orden, setOrden] = useState<Orden>("relevancia");
  const [hojaFiltros, setHojaFiltros] = useState(false);

  const [resultados, setResultados] = useState<Resultado[] | null>(null);
  // FR-MATCH-011: la lista es una recomendación del área reconocida, no un match exacto.
  const [areaRecomendada, setAreaRecomendada] = useState<string | null>(null);
  const [consulta, setConsulta] = useState<{ texto: string; materia: string } | null>(null);
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [solicitadas, setSolicitadas] = useState<Set<string>>(new Set());

  const [guardadas, setGuardadas] = useState<BusquedaGuardada[] | null>(null);
  const [guardando, setGuardando] = useState(false);
  const [yaGuardada, setYaGuardada] = useState(false);

  const inicializado = useRef(false);

  const cargarCatalogos = useCallback(() => {
    setErrorCat(null);
    getCatalogos()
      .then(setCatalogos)
      .catch((err) => setErrorCat(mensajeDeError(err, "No pudimos cargar las materias.")));
  }, []);

  useEffect(() => {
    cargarCatalogos();
    getBusquedasGuardadas()
      .then(setGuardadas)
      .catch(() => setGuardadas([]));
  }, [cargarCatalogos]);

  const nivelSel = catalogos?.find((n) => n.nivel === nivel) ?? null;
  const materiasNivel = useMemo(
    () => (nivelSel ? [...new Set(nivelSel.cursos.flatMap((c) => c.materias.map((m) => m.nombre)))] : []),
    [nivelSel]
  );

  const hidratar = useCallback(async (lista: ResultadoBusqueda[]) => {
    setAreaRecomendada(lista[0]?.porArea ? (lista[0].area ?? null) : null);
    const perfiles = await Promise.allSettled(lista.map((r) => getTutor(r.tutorId)));
    setResultados(
      lista.map((r, i) => {
        const p = perfiles[i];
        return {
          tutor: p?.status === "fulfilled" ? p.value : normalizarTutor({ id: r.tutorId, nombre: "Tutor" }),
          noAutorizado: r.noAutorizado,
          score: r.score,
        };
      })
    );
  }, []);

  const ejecutar = useCallback(async (q: string, m: string, n = "") => {
    if (!q.trim() && !m) {
      setError("Escribí qué necesitás aprender o elegí una materia.");
      return;
    }
    setBuscando(true);
    setError(null);
    setResultados(null);
    setYaGuardada(false);
    setConsulta({ texto: q.trim(), materia: m });
    try {
      const lista = await buscarTutores({
        textoBusqueda: q.trim() || undefined,
        filtroMateria: m || undefined,
        filtroNivel: n || undefined,
      });
      await hidratar(lista);
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos completar la búsqueda. Revisá tu conexión y probá de nuevo."));
    } finally {
      setBuscando(false);
    }
  }, [hidratar]);

  // Búsqueda que viene de la landing o de un link (?q=, ?materia=).
  useEffect(() => {
    if (inicializado.current) return;
    inicializado.current = true;
    const params = new URLSearchParams(window.location.search);
    const q = params.get("q") ?? "";
    const m = params.get("materia") ?? "";
    if (q || m) {
      setTexto(q);
      setMateria(m);
      void ejecutar(q, m);
    }
  }, [ejecutar]);

  async function ejecutarGuardada(g: BusquedaGuardada) {
    setBuscando(true);
    setError(null);
    setResultados(null);
    setTexto(g.textoBusqueda);
    setConsulta({ texto: g.textoBusqueda, materia: "" });
    setYaGuardada(true);
    try {
      await hidratar(await ejecutarBusquedaGuardada(g.id));
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos repetir esa búsqueda."));
    } finally {
      setBuscando(false);
    }
  }

  async function guardarActual() {
    if (!consulta) return;
    setGuardando(true);
    try {
      const nueva = await guardarBusqueda({
        textoBusqueda: consulta.texto || undefined,
        filtroMateria: consulta.materia || undefined,
      });
      setGuardadas((prev) => [nueva, ...(prev ?? [])]);
      setYaGuardada(true);
      toast.mostrar("Guardamos la búsqueda");
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos guardar la búsqueda."), { tono: "error" });
    } finally {
      setGuardando(false);
    }
  }

  function elegirMateria(m: string) {
    const nueva = materia === m ? "" : m;
    setMateria(nueva);
    if (nueva || texto.trim()) void ejecutar(texto, nueva, nivel);
    else {
      setResultados(null);
      setConsulta(null);
    }
  }

  const ordenados = useMemo(() => {
    if (!resultados) return null;
    const copia = [...resultados];
    if (orden === "precio") {
      copia.sort((a, b) => (a.tutor.precioHora ?? Infinity) - (b.tutor.precioHora ?? Infinity));
    } else if (orden === "calificacion") {
      copia.sort((a, b) => (b.tutor.calificacionPromedio ?? -1) - (a.tutor.calificacionPromedio ?? -1));
    }
    return copia;
  }, [resultados, orden]);

  const filtrosActivos = (nivel ? 1 : 0) + (materia ? 1 : 0);
  const hayBusqueda = buscando || consulta !== null;

  const panelFiltros = (
    <div className="flex flex-col gap-5">
      {errorCat && (
        <Alerta tono="peligro" accion={<Boton variante="secundario" tamano="sm" onClick={cargarCatalogos}>Reintentar</Boton>}>
          {errorCat}
        </Alerta>
      )}
      {(catalogos ?? []).length > 0 && (
        <fieldset>
          <legend className="mb-2 text-sm font-bold">Nivel</legend>
          <div className="flex flex-wrap gap-2">
            {(catalogos ?? []).map((n) => (
              <Chip
                key={n.nivel}
                activo={nivel === n.nivel}
                onClick={() => {
                  const nuevo = nivel === n.nivel ? "" : n.nivel;
                  setNivel(nuevo);
                  // El nivel acota la búsqueda en el backend; si ya hay una, se rehace.
                  if (texto.trim() || materia) void ejecutar(texto, materia, nuevo);
                }}
              >
                {rotuloNivel(n.nivel)}
              </Chip>
            ))}
          </div>
        </fieldset>
      )}
      <fieldset>
        <legend className="mb-2 text-sm font-bold">Materia</legend>
        <div className="flex flex-wrap gap-2">
          {(materiasNivel.length > 0 ? materiasNivel : MATERIAS_SUGERIDAS).map((m) => (
            <Chip key={m} activo={materia === m} onClick={() => elegirMateria(m)}>
              {m}
            </Chip>
          ))}
        </div>
      </fieldset>
    </div>
  );

  return (
    <AppShell>
      <section>
        <h1 className="text-[28px] font-extrabold sm:text-[40px]">¿Qué querés aprender?</h1>
        <form
          role="search"
          className="mt-5 flex items-center gap-2 rounded-[18px] bg-superficie p-2 shadow-elevado ring-1 ring-borde focus-within:ring-2 focus-within:ring-marca-600"
          onSubmit={(e) => {
            e.preventDefault();
            void ejecutar(texto, materia, nivel);
          }}
        >
          <Search className="ml-3 size-5 shrink-0 text-tinta-tenue" aria-hidden />
          <label htmlFor="buscar-texto" className="sr-only">
            Buscar tutores
          </label>
          <input
            id="buscar-texto"
            type="search"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Ej: repasar división para el secundario"
            maxLength={500}
            className="min-h-12 w-full min-w-0 bg-transparent px-1 text-[17px] text-tinta placeholder:text-tinta-tenue focus:outline-none"
          />
          <Boton type="submit" variante="oscuro" cargando={buscando} className="shrink-0">
            Buscar
          </Boton>
        </form>
        <p className="mt-3 flex items-center gap-1.5 text-sm text-tinta-tenue">
          <Sparkles className="size-4 text-acento-700" aria-hidden />
          Tutores recomendados para lo que necesitás
        </p>
      </section>

      {/* Barra de filtros: chips en desktop, hoja inferior en mobile. */}
      <div className="mt-6 flex items-center gap-2">
        <Boton
          variante="secundario"
          tamano="sm"
          className="rounded-pastilla lg:hidden"
          icono={<SlidersHorizontal />}
          onClick={() => setHojaFiltros(true)}
        >
          Filtros{filtrosActivos > 0 ? ` · ${filtrosActivos}` : ""}
        </Boton>
        {materia && (
          <Chip removible onClick={() => elegirMateria(materia)} aria-label={`Quitar filtro ${materia}`}>
            {materia}
          </Chip>
        )}
        {nivel && (
          <Chip removible onClick={() => { setNivel(""); if (texto.trim() || materia) void ejecutar(texto, materia, ""); }} aria-label={`Quitar filtro ${rotuloNivel(nivel)}`} className="hidden lg:inline-flex">
            {rotuloNivel(nivel)}
          </Chip>
        )}
      </div>
      <div className="mt-4 hidden lg:block">{panelFiltros}</div>

      <Modal
        abierto={hojaFiltros}
        onCerrar={() => setHojaFiltros(false)}
        titulo="Filtros"
        variante="hoja"
        pie={
          <>
            <Boton
              variante="fantasma"
              onClick={() => {
                setNivel("");
                elegirMateria(materia);
              }}
              disabled={filtrosActivos === 0}
            >
              Limpiar
            </Boton>
            <Boton onClick={() => setHojaFiltros(false)}>Ver resultados</Boton>
          </>
        }
      >
        {panelFiltros}
      </Modal>

      <section className="mt-8" aria-live="polite" aria-busy={buscando}>
        {error && (
          <Alerta tono="peligro" className="mb-6" accion={<Boton variante="secundario" tamano="sm" onClick={() => void ejecutar(texto, materia, nivel)}>Probar de nuevo</Boton>}>
            {error}
          </Alerta>
        )}

        {!hayBusqueda && (
          <div className="flex flex-col gap-8">
            {guardadas !== null && guardadas.length > 0 && (
              <div>
                <h2 className="flex items-center gap-2 text-lg font-bold">
                  <History className="size-5 text-tinta-tenue" aria-hidden /> Tus búsquedas guardadas
                </h2>
                <div className="mt-3 flex flex-wrap gap-2">
                  {guardadas.map((g) => (
                    <Boton key={g.id} variante="secundario" tamano="sm" className="rounded-pastilla" onClick={() => void ejecutarGuardada(g)}>
                      {g.textoBusqueda}
                    </Boton>
                  ))}
                </div>
              </div>
            )}
            <EstadoVacio icono={<Search />} titulo="Empezá por lo que necesitás" className="py-8">
              Contanos con tus palabras qué querés aprender (&ldquo;ecuaciones de segundo grado&rdquo;, &ldquo;inglés para viajar&rdquo;) o elegí una materia.
            </EstadoVacio>
          </div>
        )}

        {hayBusqueda && (
          <>
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
              <p className="text-[15px] text-tinta-suave">
                {buscando ? "Buscando tutores…" : resumenResultados(ordenados?.length ?? 0, consulta, areaRecomendada)}
              </p>
              {!buscando && (ordenados?.length ?? 0) > 0 && (
                <div className="flex items-center gap-2">
                  <label htmlFor="orden" className="sr-only">
                    Ordenar por
                  </label>
                  <div className="relative">
                    <ArrowUpDown className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-tinta-tenue" aria-hidden />
                    <select
                      id="orden"
                      value={orden}
                      onChange={(e) => setOrden(e.target.value as Orden)}
                      className="min-h-10 cursor-pointer appearance-none rounded-pastilla border border-borde-fuerte bg-superficie pl-9 pr-4 text-sm font-semibold text-tinta"
                    >
                      {ORDENES.map((o) => (
                        <option key={o.id} value={o.id}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </div>
                  <Boton
                    variante="secundario"
                    tamano="sm"
                    className="rounded-pastilla"
                    icono={yaGuardada ? <BookmarkCheck /> : <Bookmark />}
                    cargando={guardando}
                    disabled={yaGuardada}
                    onClick={guardarActual}
                  >
                    {yaGuardada ? (
                      "Guardada"
                    ) : (
                      <>
                        <span className="sm:hidden">Guardar</span>
                        <span className="hidden sm:inline">Guardar esta búsqueda</span>
                      </>
                    )}
                  </Boton>
                </div>
              )}
            </div>

            {buscando && <SkeletonTarjetas cantidad={6} etiqueta="Buscando tutores…" />}

            {!buscando && areaRecomendada && (ordenados?.length ?? 0) > 0 && (
              <Alerta tono="info" className="mb-5" titulo={`Todavía nadie da clases de ${consulta?.texto ?? "ese tema"}`}>
                Pero estos tutores de {areaRecomendada} seguro te pueden ayudar con eso.
              </Alerta>
            )}

            {!buscando && ordenados && ordenados.length === 0 && !error && (
              <EstadoVacio
                icono={<SearchX />}
                titulo={
                  consulta?.texto
                    ? `Todavía no encontramos tutores para enseñarte ${consulta.texto}`
                    : "Todavía no hay tutores con esos filtros"
                }
                accion={
                  filtrosActivos > 0 ? (
                    <Boton
                      variante="secundario"
                      onClick={() => {
                        setNivel("");
                        elegirMateria(materia);
                      }}
                    >
                      Sacar los filtros
                    </Boton>
                  ) : undefined
                }
              >
                Probá contándolo con otras palabras o eligiendo la materia. Estamos sumando tutores todo el tiempo.
              </EstadoVacio>
            )}

            {!buscando && ordenados && ordenados.length > 0 && (
              <ul className="grid list-none grid-cols-1 gap-4 p-0 md:grid-cols-2 lg:grid-cols-3">
                {ordenados.map((r, i) => (
                  <li key={r.tutor.id} className={cn("motion-safe:animate-aparecer")} style={{ animationDelay: `${Math.min(i, 8) * 40}ms` }}>
                    <TarjetaTutor
                      tutor={r.tutor}
                      noAutorizado={r.noAutorizado}
                      avisoAutorizacion={solicitadas.has(r.tutor.id)}
                      onSolicitarAutorizacion={() =>
                        setSolicitadas((prev) => {
                          const s = new Set(prev);
                          s.add(r.tutor.id);
                          return s;
                        })
                      }
                    />
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </section>
    </AppShell>
  );
}
