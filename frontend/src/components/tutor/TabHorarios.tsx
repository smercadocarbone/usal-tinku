"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api";
import { formatearFechaCorta } from "@/lib/formatos";
import { Alerta, Tabs } from "@/components/ui";
import WeeklyAvailabilityGrid, {
  INICIO_DIA,
  ULTIMA_FILA,
  MAX_FRANJA_FILAS,
  rangosDe,
  partirFranja,
  type Ocupada,
  type Seleccion,
} from "./WeeklyAvailabilityGrid";

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

  /** Franjas ya publicadas, mapeadas a bloques de la grilla del modo activo. */
  function ocupadas(): Ocupada[] {
    return franjas
      .filter((f) => f.activa)
      .flatMap<Ocupada>((f) => {
        const desde = Number(f.horaInicio.slice(0, 2));
        const hasta = Number(f.horaFin.slice(0, 2)) - 1;
        if (desde < INICIO_DIA || desde > ULTIMA_FILA) return [];
        const filaHasta = Math.min(hasta, ULTIMA_FILA);
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
      for (const [inicio, fin] of rangosDe(seleccion[columna] ?? [])) {
        // Pintado libre: se parten las franjas contiguas en ≤3h (FR-RES-024).
        for (const [ini, ultimo] of partirFranja(inicio, fin, MAX_FRANJA_FILAS)) {
          peticiones.push({
            diaSemana,
            fechaEspecifica,
            horaInicio: horaDeFila(ini),
            horaFin: horaDeFila(ultimo + 1),
          });
        }
      }
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

  return (
    <section aria-label="Mis horarios">
      <h2 className="text-lg font-bold text-slate-800">Mis horarios</h2>
      <p className="text-sm text-slate-500">
        Publicá cuándo estás disponible. Pintá los bloques (clic o clic y
        arrastre); al guardar se parte en franjas de hasta 3 horas.
      </p>

      {/* Toggle semanal / puntual */}
      <Tabs
        variante="segmentado"
        className="mt-4"
        etiqueta="Tipo de disponibilidad"
        activo={modo}
        onCambio={setModo}
        opciones={[
          { id: "semanal", label: "Disponibilidad semanal fija" },
          { id: "puntual", label: "Disponibilidad puntual" },
        ]}
      />

      {modo === "puntual" && (
        <label className="mt-4 flex flex-col gap-1 text-sm font-semibold text-slate-800">
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
            className="w-56 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-base text-slate-800 focus:border-transparent focus:outline-2 focus:outline-teal-600 focus:outline-offset-1"
          />
        </label>
      )}

      {/* Grilla */}
      <div className="mt-4">
        <WeeklyAvailabilityGrid
          columnas={
            modo === "semanal"
              ? COLUMNAS_SEMANAL.map((c) => ({ clave: c.clave, etiqueta: c.etiqueta }))
              : fechaPuntual
                ? [{ clave: CLAVE_PUNTUAL, etiqueta: formatearFechaCorta(fechaPuntual) }]
                : [{ clave: CLAVE_PUNTUAL, etiqueta: "Sin fecha" }]
          }
          ocupadas={ocupadas()}
          seleccion={seleccion}
          onCambioSeleccion={setSeleccion}
          onGuardar={publicar}
          guardando={publicando}
        />
      </div>

      {modo === "puntual" && !fechaPuntual && (
        <p className="mt-2 text-sm text-slate-500">
          Elegí una fecha para ver y cargar tu horario puntual.
        </p>
      )}

      {error && (
        <Alerta tono="error" className="mt-3">
          {error}
        </Alerta>
      )}
      {exito && (
        <Alerta tono="exito" className="mt-3">
          {exito}
        </Alerta>
      )}

      {/* Lista de franjas publicadas */}
      <h3 className="mt-6 text-sm font-bold text-slate-800">Mis franjas publicadas</h3>
      {cargandoLista ? (
        <p className="mt-2 text-slate-500">Cargando…</p>
      ) : listaPendiente ? (
        <Alerta tono="aviso" className="mt-2 w-fit">
          El listado de franjas está pendiente en backend.
        </Alerta>
      ) : franjas.length === 0 ? (
        <p className="mt-2 text-slate-500">No publicaste franjas todavía.</p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {[...franjas].sort(ordenarFranjas).map((f) => (
            <li
              key={f.id}
              className="flex justify-between gap-4 border-b border-slate-200 py-3 text-sm"
            >
              <span>
                {f.diaSemana !== null
                  ? `${DIAS_BACK[f.diaSemana] ?? f.diaSemana} de ${f.horaInicio.slice(0, 5)} a ${f.horaFin.slice(0, 5)}`
                  : `${formatearFechaCorta(f.fechaEspecifica!)} de ${f.horaInicio.slice(0, 5)} a ${f.horaFin.slice(0, 5)}`}
              </span>
              <span
                className={
                  f.activa
                    ? "text-sm font-medium text-teal-700"
                    : "text-sm font-medium text-slate-500"
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