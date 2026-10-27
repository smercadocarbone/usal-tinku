"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError, mensajeDeError, quitarFranja } from "@/lib/api";
import { agruparPorDia, unirContiguas } from "@/lib/agenda";
import { formatearFechaCorta, hoyIso } from "@/lib/formatos";
import { Alerta, Boton, ModalConfirmacion, Tabs, SelectorFecha } from "@/components/ui";
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

/** Orden de la lista: Lunes → Domingo. */
const ORDEN_DIAS = [1, 2, 3, 4, 5, 6, 0];

interface GrupoLista {
  clave: string;
  titulo: string;
  franjas: Franja[];
}

/**
 * R-D D-8 (T7-3): una fila por día con las franjas contiguas unidas ("Lunes de 08:00 a 23:00"),
 * en vez de una fila por franja (el seed tiene 105).
 */
function gruposDeLista(franjas: Franja[]): GrupoLista[] {
  const { semanal, puntual } = agruparPorDia(franjas);
  const rotulo = (fs: Franja[]) =>
    unirContiguas(fs).map((r) => `de ${r.desde} a ${r.hasta}`).join(" y ");
  const ordenarPorHora = (fs: Franja[]) => [...fs].sort((a, b) => a.horaInicio.localeCompare(b.horaInicio));
  return [
    ...ORDEN_DIAS.filter((d) => semanal.has(d)).map((d) => ({
      clave: `s${d}`,
      titulo: `${DIAS_BACK[d]} ${rotulo(semanal.get(d)!)}`,
      franjas: ordenarPorHora(semanal.get(d)!),
    })),
    ...[...puntual.keys()].sort().map((fecha) => ({
      clave: `p${fecha}`,
      titulo: `${formatearFechaCorta(fecha)} ${rotulo(puntual.get(fecha)!)}`,
      franjas: ordenarPorHora(puntual.get(fecha)!),
    })),
  ];
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
  const [aQuitar, setAQuitar] = useState<Franja | null>(null);
  const [quitando, setQuitando] = useState(false);

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
        (err: unknown) => ({ ok: false as const, msj: err instanceof ApiError && err.message ? err.message : "No pudimos publicar ese horario. Probá de nuevo." })
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

  /** R-D D-1 (T2-H1, FR-RES-025): baja lógica; las clases ya reservadas se mantienen. */
  async function confirmarQuitar() {
    if (!aQuitar) return;
    setQuitando(true);
    setError("");
    setExito("");
    try {
      const { reservasFuturasEnLaFranja: n } = await quitarFranja(aQuitar.id);
      setExito(
        n > 0
          ? `Quitaste ese horario. Tenés ${n} clase${n > 1 ? "s" : ""} reservada${n > 1 ? "s" : ""} en él: siguen en pie.`
          : "Quitaste ese horario."
      );
      setAQuitar(null);
      cargarFranjas();
    } catch (err) {
      setError(mensajeDeError(err, "No pudimos quitar ese horario."));
      setAQuitar(null);
    } finally {
      setQuitando(false);
    }
  }

  /** Lo ya publicado para la fecha elegida, para poder quitarlo desde la misma vista. */
  const puntualesDelDia =
    modo === "puntual" && fechaPuntual
      ? franjas
          .filter((f) => f.activa && f.fechaEspecifica === fechaPuntual)
          .sort((a, b) => a.horaInicio.localeCompare(b.horaInicio))
      : [];

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
        <SelectorFecha
          id="fechaPuntual"
          etiqueta="Fecha específica"
          className="mt-4 w-64"
          value={fechaPuntual}
          min={hoyIso()}
          onChange={(iso) => {
            setFechaPuntual(iso);
            setSeleccion((prev) => {
              const next = { ...prev };
              delete next[CLAVE_PUNTUAL];
              return next;
            });
          }}
        />
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

      {modo === "puntual" && fechaPuntual && (
        <div className="mt-4">
          <h3 className="text-sm font-bold text-slate-800">
            Publicado para el {formatearFechaCorta(fechaPuntual)}
          </h3>
          {puntualesDelDia.length === 0 ? (
            <p className="mt-1 text-sm text-slate-500">Todavía no publicaste horarios puntuales para esta fecha.</p>
          ) : (
            <ul className="mt-1 list-none p-0">
              {puntualesDelDia.map((f) => (
                <li key={f.id} className="flex items-center justify-between gap-4 border-b border-slate-200 py-1.5 text-sm">
                  <span className="tabular-nums">
                    {f.horaInicio.slice(0, 5)}–{f.horaFin.slice(0, 5)}
                  </span>
                  <Boton
                    variante="secundario"
                    tamano="sm"
                    aria-label={`Quitar ${formatearFechaCorta(fechaPuntual)} ${f.horaInicio.slice(0, 5)}–${f.horaFin.slice(0, 5)}`}
                    onClick={() => setAQuitar(f)}
                  >
                    Quitar
                  </Boton>
                </li>
              ))}
            </ul>
          )}
        </div>
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
          No pudimos cargar tus franjas. Probá de nuevo en unos minutos.
        </Alerta>
      ) : franjas.length === 0 ? (
        <p className="mt-2 text-slate-500">No publicaste franjas todavía.</p>
      ) : (
        <ul className="mt-2 list-none p-0">
          {gruposDeLista(franjas.filter((f) => f.activa)).map((g) => (
            <li key={g.clave} className="border-b border-slate-200 py-2 text-sm">
              <details>
                <summary className="flex min-h-11 cursor-pointer items-center justify-between gap-4">
                  <span>{g.titulo}</span>
                  <span className="text-sm font-medium text-teal-700">
                    {g.franjas.length} franja{g.franjas.length > 1 ? "s" : ""}
                  </span>
                </summary>
                <ul className="mt-1 list-none p-0 pl-4">
                  {g.franjas.map((f) => (
                    <li key={f.id} className="flex items-center justify-between gap-4 py-1">
                      <span className="tabular-nums">
                        {f.horaInicio.slice(0, 5)}–{f.horaFin.slice(0, 5)}
                      </span>
                      <Boton
                        variante="secundario"
                        tamano="sm"
                        aria-label={`Quitar ${g.titulo.split(" de ")[0]} ${f.horaInicio.slice(0, 5)}–${f.horaFin.slice(0, 5)}`}
                        onClick={() => setAQuitar(f)}
                      >
                        Quitar
                      </Boton>
                    </li>
                  ))}
                </ul>
              </details>
            </li>
          ))}
        </ul>
      )}

      <ModalConfirmacion
        abierto={aQuitar !== null}
        onCerrar={() => setAQuitar(null)}
        onConfirmar={confirmarQuitar}
        titulo="¿Quitar este horario?"
        textoConfirmar="Quitar horario"
        cargando={quitando}
      >
        Dejás de ofrecer este horario. Las clases ya reservadas se mantienen.
      </ModalConfirmacion>
    </section>
  );
}