"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { formatearFechaCorta } from "@/lib/formatos";
import { Loader2 } from "lucide-react";
import GrillaHoraria, {
  INICIO_DIA,
  FIN_DIA,
  MAX_FRANJA_FILAS,
  filaEnRango,
  type Ocupada,
  type Seleccion,
} from "./GrillaHoraria";

export interface Franja {
  id: string;
  tutorId: string;
  diaSemana: number | null;
  fechaEspecifica: string | null;
  horaInicio: string;
  horaFin: string;
  activa: boolean;
}

const DIAS_BACK: Record<number, string> = {
  0: "Domingo",
  1: "Lunes",
  2: "Martes",
  3: "Miércoles",
  4: "Jueves",
  5: "Viernes",
  6: "Sábado",
};

/** Columnas del grid en orden Lunes → Domingo. */
const COLUMNAS_SEMANAL = [
  { clave: "s0", etiqueta: "Lun", diaSemana: 1 },
  { clave: "s1", etiqueta: "Mar", diaSemana: 2 },
  { clave: "s2", etiqueta: "Mié", diaSemana: 3 },
  { clave: "s3", etiqueta: "Jue", diaSemana: 4 },
  { clave: "s4", etiqueta: "Vie", diaSemana: 5 },
  { clave: "s5", etiqueta: "Sáb", diaSemana: 6 },
  { clave: "s6", etiqueta: "Dom", diaSemana: 0 },
];

const CLAVE_PUNTUAL = "p";

function horaDeFila(fila: number): string {
  return `${String(fila).padStart(2, "0")}:00`;
}

interface PeticionFranja extends Record<string, unknown> {
  diaSemana: number | null;
  fechaEspecifica: string | null;
  horaInicio: string;
  horaFin: string;
}

function ordenarFranjas(unA: Franja, unB: Franja): number {
  const a = unA.fechaEspecifica ?? String(unA.diaSemana);
  const b = unB.fechaEspecifica ?? String(unB.diaSemana);
  return a.localeCompare(b);
}

export default function TabHorarios({ tutorId }: { tutorId: string }) {
  const [modo, setModo] = useState<"semanal" | "puntual">("semanal");
  const [fechaPuntual, setFechaPuntual] = useState("");
  const [franjas, setFranjas] = useState<Franja[]>([]);
  const [seleccion, setSeleccion] = useState<Seleccion>({});
  const [cargandoLista, setCargandoLista] = useState(true);
  const [listaPendiente, setListaPendiente] = useState(false);
  const [publicando, setPublicando] = useState(false);
  const [error, setError] = useState("");
  const [exito, setExito] = useState("");

  const cargarFranjas = useCallback(() => {
    setCargandoLista(true);
    setListaPendiente(false);
    api
      .get<Franja[]>(`/api/tutores/${tutorId}/franjas`)
      .then(setFranjas)
      .catch(() => setListaPendiente(true))
      .finally(() => setCargandoLista(false));
  }, [tutorId]);

  useEffect(() => {
    cargarFranjas();
  }, [cargarFranjas]);

  function manejarCelda(columna: string, fila: number) {
    setSeleccion((prev) => {
      const cur = prev[columna] ?? null;
      if (!cur) return { ...prev, [columna]: { anchor: fila, hasta: fila } };
      if (fila === cur.anchor) {
        const next = { ...prev };
        delete next[columna];
        return next;
      }
      const rango = filaEnRango({ anchor: cur.anchor, hasta: fila });
      if (rango && rango[1] - rango[0] + 1 > MAX_FRANJA_FILAS) return prev;
      return { ...prev, [columna]: { anchor: cur.anchor, hasta: fila } };
    });
  }

  /** Franjas ya publicadas, mapeadas a bloques de la grilla del modo activo. */
  function ocupadas(): Ocupada[] {
    return franjas
      .filter((f) => f.activa)
      .flatMap<Ocupada>((f) => {
        const desde = Number(f.horaInicio.slice(0, 2));
        const hasta = Number(f.horaFin.slice(0, 2)) - 1;
        if (desde < INICIO_DIA || desde > FIN_DIA) return [];
        const filaHasta = Math.min(hasta, FIN_DIA);
        if (f.diaSemana !== null) {
          // columna del grid = (diaSemana + 6) % 7 (Lunes=1 → col 0).
          const columna = `s${(f.diaSemana + 6) % 7}`;
          return [{ columna, desde, hasta: filaHasta }];
        }
        if (modo === "puntual" && f.fechaEspecifica === fechaPuntual) {
          return [{ columna: CLAVE_PUNTUAL, desde, hasta: filaHasta }];
        }
        return [];
      });
  }

  function peticionesDeSeleccion(): PeticionFranja[] {
    const peticiones: PeticionFranja[] = [];
    const completar = (columna: string, diaSemana: number | null, fechaEspecifica: string | null) => {
      const rango = filaEnRango(seleccion[columna]);
      if (!rango) return;
      peticiones.push({
        diaSemana,
        fechaEspecifica,
        horaInicio: horaDeFila(rango[0]),
        horaFin: horaDeFila(rango[1] + 1),
      });
    };

    if (modo === "semanal") {
      for (const col of COLUMNAS_SEMANAL) completar(col.clave, col.diaSemana, null);
    } else if (fechaPuntual) {
      completar(CLAVE_PUNTUAL, null, fechaPuntual);
    }
    return peticiones;
  }

  function publicar() {
    setError("");
    setExito("");
    if (modo === "puntual" && !fechaPuntual) {
      setError("Elegí una fecha para la franja puntual.");
      return;
    }
    const peticiones = peticionesDeSeleccion();
    if (peticiones.length === 0) {
      setError("Seleccioná al menos un bloque de hora para publicar.");
      return;
    }

    setPublicando(true);
    const resultados = peticiones.map((p) =>
      api.post("/api/tutores/franjas", p).then(
        () => ({ ok: true as const }),
        (err: unknown) => ({ ok: false as const, msj: err instanceof ApiError ? err.message : "Error inesperado." })
      )
    );
    Promise.all(resultados)
      .then((res) => {
        const exitosas = res.filter((r) => r.ok).length;
        const fallidas = res.filter((r) => !r.ok);
        if (exitosas) {
          setExito(
            `Se publicaron ${exitosas} franja${exitosas > 1 ? "s" : ""}.`
          );
          setSeleccion({});
          cargarFranjas();
        }
        if (fallidas.length) {
          setError(fallidas.map((f) => f.msj).join(" "));
        }
      })
      .finally(() => setPublicando(false));
  }

  const haySeleccion =
    Object.values(seleccion).some((s) => s !== null && s !== undefined);

  return (
    <section aria-label="Mis horarios">
      <h2 className="text-lg font-bold text-texto">Mis horarios</h2>
      <p className="text-[0.9rem] text-texto-suave">
        Publicá cuándo estás disponible. Tocá un horario y extendé hacia abajo
        para armar una franja de 1 a {MAX_FRANJA_FILAS} horas.
      </p>

      {/* Toggle semanal / puntual */}
      <div
        className="mt-4 inline-flex rounded-lg border border-borde bg-superficie p-1"
        role="group"
        aria-label="Tipo de disponibilidad"
      >
        <button
          type="button"
          className={
            modo === "semanal"
              ? "rounded-md bg-teal-600 px-4 py-[0.4rem] text-[0.85rem] font-semibold text-white transition-all duration-200"
              : "rounded-md px-4 py-[0.4rem] text-[0.85rem] font-semibold text-texto transition-all duration-200 hover:bg-stone-100"
          }
          onClick={() => setModo("semanal")}
        >
          Disponibilidad semanal fija
        </button>
        <button
          type="button"
          className={
            modo === "puntual"
              ? "rounded-md bg-teal-600 px-4 py-[0.4rem] text-[0.85rem] font-semibold text-white transition-all duration-200"
              : "rounded-md px-4 py-[0.4rem] text-[0.85rem] font-semibold text-texto transition-all duration-200 hover:bg-stone-100"
          }
          onClick={() => setModo("puntual")}
        >
          Disponibilidad puntual
        </button>
      </div>

      {modo === "puntual" && (
        <label className="mt-4 flex flex-col gap-1 text-[0.85rem] font-semibold text-texto">
          Fecha específica
          <input
            type="date"
            value={fechaPuntual}
            min={new Date().toISOString().slice(0, 10)}
            onChange={(e) => {
              setFechaPuntual(e.target.value);
              setSeleccion((prev) => {
                const next = { ...prev };
                delete next[CLAVE_PUNTUAL];
                return next;
              });
            }}
            className="w-56 rounded-lg border border-borde bg-superficie px-3 py-[0.6rem] text-base text-texto focus:border-transparent focus:outline-2 focus:outline-accent focus:outline-offset-1"
          />
        </label>
      )}

      {/* Grilla */}
      <div className="mt-4">
        <GrillaHoraria
          columnas={
            modo === "semanal"
              ? COLUMNAS_SEMANAL.map((c) => ({ clave: c.clave, etiqueta: c.etiqueta }))
              : fechaPuntual
                ? [{ clave: CLAVE_PUNTUAL, etiqueta: formatearFechaCorta(fechaPuntual) }]
                : [{ clave: CLAVE_PUNTUAL, etiqueta: "Sin fecha" }]
          }
          ocupadas={ocupadas()}
          seleccion={seleccion}
          onCelda={manejarCelda}
        />
      </div>

      {modo === "puntual" && !fechaPuntual && (
        <p className="mt-2 text-[0.85rem] text-texto-suave">
          Elegí una fecha para ver y cargar tu horario puntual.
        </p>
      )}

      <div className="mt-4 flex flex-wrap items-center gap-3">
        <button
          type="button"
          onClick={publicar}
          disabled={publicando || !haySeleccion}
          className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white transition-all duration-200 enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
        >
          {publicando ? (
            <>
              <Loader2 className="mr-1 inline animate-spin" size={16} />
              Publicando…
            </>
          ) : (
            "Publicar franjas"
          )}
        </button>
        {haySeleccion && (
          <span className="text-[0.8rem] text-texto-suave">
            Se publicará una franja por día seleccionado.
          </span>
        )}
      </div>

      {error && (
        <div
          className="mt-3 rounded-lg border border-red-200 bg-red-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-peligro"
          role="alert"
        >
          {error}
        </div>
      )}
      {exito && (
        <div
          className="mt-3 rounded-lg border border-teal-200 bg-teal-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-exito"
          role="status"
        >
          {exito}
        </div>
      )}

      {/* Lista de franjas publicadas */}
      <h3 className="mt-6 text-[0.95rem] font-bold text-texto">Mis franjas publicadas</h3>
      {cargandoLista ? (
        <p className="mt-2 text-texto-suave">Cargando…</p>
      ) : listaPendiente ? (
        <p className="mt-2 w-fit rounded-lg border border-amber-200 bg-amber-50 px-[0.9rem] py-[0.7rem] text-[0.9rem] text-aviso" role="status">
          El listado de franjas está pendiente en backend.
        </p>
      ) : franjas.length === 0 ? (
        <p className="mt-2 text-texto-suave">No publicaste franjas todavía.</p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {[...franjas].sort(ordenarFranjas).map((f) => (
            <li
              key={f.id}
              className="flex justify-between gap-4 border-b border-borde py-3 text-[0.9rem]"
            >
              <span>
                {f.diaSemana !== null
                  ? `${DIAS_BACK[f.diaSemana] ?? f.diaSemana} de ${f.horaInicio.slice(0, 5)} a ${f.horaFin.slice(0, 5)}`
                  : `${formatearFechaCorta(f.fechaEspecifica!)} de ${f.horaInicio.slice(0, 5)} a ${f.horaFin.slice(0, 5)}`}
              </span>
              <span
                className={
                  f.activa
                    ? "text-[0.85rem] font-medium text-accent"
                    : "text-[0.85rem] font-medium text-texto-suave"
                }
              >
                {f.activa ? "Activa" : "Inactiva"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}