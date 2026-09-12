"use client";

/** Grilla semanal de disponibilidad estilo Google Calendar con "pintado".
 *
 * El tutor pinta sus horarios: clic pinta/despinta un bloque, clic y arrastre
 * pinta o borra un rango contiguo. Cada columna es un día (o una fecha
 * puntual) y cada fila es un bloque de una hora (08:00→22:00). Las franjas ya
 * publicadas se pintan como bloques fijos no interactivos. Máximo 3 filas por
 * franja (180min, FR-RES-024).
 */

import { useEffect, useState } from "react";
import { Loader2 } from "lucide-react";

export const INICIO_DIA = 8;
export const FILAS_DIA = 14; // 08:00..21:00 (la última celda termina 22:00)
export const ULTIMA_FILA = INICIO_DIA + FILAS_DIA - 1;
export const MAX_FRANJA_FILAS = 3;

/** Rango de filas ya publicado por el Tutor (bloques fijos). */
export interface Ocupada {
  columna: string;
  desde: number; // fila inclusive
  hasta: number; // fila inclusive
}

/** Filas pintadas por columna (ordenadas). */
export type Seleccion = Record<string, number[]>;

/** Divide una lista de filas en rangos contiguos. */
export function rangosDe(filas: number[]): [number, number][] {
  const ordenadas = [...filas].sort((a, b) => a - b);
  const rangos: [number, number][] = [];
  for (const fila of ordenadas) {
    const ultimo = rangos[rangos.length - 1];
    if (ultimo && fila === ultimo[1] + 1) ultimo[1] = fila;
    else rangos.push([fila, fila]);
  }
  return rangos;
}

interface Columna {
  clave: string;
  etiqueta: string;
}

interface Props {
  columnas: Columna[];
  ocupadas: Ocupada[];
  seleccion: Seleccion;
  onCambioSeleccion: (actualizar: (prev: Seleccion) => Seleccion) => void;
  onGuardar: () => void;
  guardando: boolean;
}

function horaLabel(fila: number): string {
  return `${String(fila).padStart(2, "0")}:00`;
}

const GUTTER = "3.5rem";

export default function WeeklyAvailabilityGrid({
  columnas,
  ocupadas,
  seleccion,
  onCambioSeleccion,
  onGuardar,
  guardando,
}: Props) {
  const [arrastrando, setArrastrando] = useState<{
    columna: string;
    desde: number;
    pintar: boolean;
  } | null>(null);

  useEffect(() => {
    if (!arrastrando) return;
    const soltar = () => setArrastrando(null);
    window.addEventListener("mouseup", soltar);
    return () => window.removeEventListener("mouseup", soltar);
  }, [arrastrando]);

  function estaOcupada(columna: string, fila: number): boolean {
    return ocupadas.some(
      (o) => o.columna === columna && fila >= o.desde && fila <= o.hasta
    );
  }

  function estaPintada(columna: string, fila: number): boolean {
    return (seleccion[columna] ?? []).includes(fila);
  }

  function aplicarRango(
    columna: string,
    desde: number,
    hasta: number,
    pintar: boolean
  ) {
    onCambioSeleccion((prev) => {
      let a = Math.min(desde, hasta);
      let b = Math.max(desde, hasta);
      const filas = new Set(prev[columna] ?? []);
      if (pintar) {
        if (b - a + 1 > MAX_FRANJA_FILAS) {
          // Mantener el ancla fija y recortar del lado hacia donde arrastra.
          if (hasta >= desde) {
            a = desde;
            b = desde + MAX_FRANJA_FILAS - 1;
          } else {
            b = desde;
            a = desde - MAX_FRANJA_FILAS + 1;
          }
        }
        for (let f = a; f <= b; f++) filas.add(f);
      } else {
        for (let f = a; f <= b; f++) filas.delete(f);
      }
      const siguiente = { ...prev };
      if (filas.size > 0) siguiente[columna] = [...filas].sort((x, y) => x - y);
      else delete siguiente[columna];
      return siguiente;
    });
  }

  function empezarArrastre(
    columna: string,
    fila: number,
    pintar: boolean,
    e: React.MouseEvent
  ) {
    e.preventDefault();
    setArrastrando({ columna, desde: fila, pintar });
    aplicarRango(columna, fila, fila, pintar);
  }

  const totalHoras = Object.values(seleccion).reduce(
    (acc, filas) => acc + filas.length,
    0
  );
  const textoResumen =
    totalHoras === 0
      ? "Tocá los bloques para pintar tus horarios disponibles."
      : `Has seleccionado un total de ${totalHoras} hora${
          totalHoras === 1 ? "" : "s"
        } disponibles ${columnas.length === 7 ? "esta semana" : "en esa fecha"}.`;

  return (
    <div className="select-none">
      <div className="overflow-x-auto">
        <div
          className="min-w-[42rem]"
          style={{
            gridTemplateColumns: `${GUTTER} repeat(${columnas.length}, minmax(0, 1fr))`,
            display: "grid",
          }}
        >
          {/* Encabezados de días */}
          <div className="sticky left-0 z-10 bg-superficie" />
          {columnas.map((c) => (
            <div
              key={c.clave}
              className="border-b border-gray-100 pb-2 text-center text-xs font-semibold text-texto"
            >
              {c.etiqueta}
            </div>
          ))}

          {/* Filas de horas */}
          {Array.from({ length: FILAS_DIA }, (_, i) => {
            const fila = INICIO_DIA + i;
            const hora = horaLabel(fila);
            return (
              <div key={fila} className="contents">
                <div className="sticky left-0 z-10 flex items-center justify-end border-b border-gray-100 bg-superficie pr-2 text-xs text-gray-400">
                  {hora}
                </div>
                {columnas.map((c) => {
                  const ocupada = estaOcupada(c.clave, fila);
                  const pintada = estaPintada(c.clave, fila);
                  const iniciosDeRango = new Set(
                    rangosDe(seleccion[c.clave] ?? []).map(([inicio]) => inicio)
                  );
                  const muestraRango =
                    pintada && iniciosDeRango.has(fila);
                  const rango = rangosDe(seleccion[c.clave] ?? []).find(
                    ([inicio, fin]) => fila >= inicio && fila <= fin
                  );
                  const etiquetaRango =
                    rango &&
                    `${horaLabel(rango[0])} - ${horaLabel(rango[1] + 1)}`;
                  const clases = ocupada
                    ? "cursor-default bg-teal-100 border-l-4 border-l-teal-500"
                    : pintada
                      ? "border-l-4 border-l-teal-600 bg-teal-100 text-teal-800"
                      : "border-l border-gray-100 hover:bg-gray-50";
                  return (
                    <button
                      key={`${c.clave}-${fila}`}
                      type="button"
                      disabled={ocupada}
                      aria-label={`${c.etiqueta} ${hora}${pintada ? " (disponible)" : ""}`}
                      aria-pressed={pintada}
                      onMouseDown={(e) =>
                        !ocupada &&
                        empezarArrastre(
                          c.clave,
                          fila,
                          !pintada,
                          e
                        )
                      }
                      onMouseEnter={() => {
                        if (arrastrando && arrastrando.columna === c.clave) {
                          aplicarRango(
                            c.clave,
                            arrastrando.desde,
                            fila,
                            arrastrando.pintar
                          );
                        }
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          aplicarRango(c.clave, fila, fila, !pintada);
                        }
                      }}
                      className={`h-9 w-full overflow-hidden border-b border-gray-100 text-left text-[0.6rem] leading-tight transition-colors duration-100 ${clases}`}
                    >
                      {muestraRango && (
                        <span className="px-1 font-medium">{etiquetaRango}</span>
                      )}
                    </button>
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      {/* Resumen dinámico */}
      <p className="mt-3 text-[0.9rem] font-medium text-texto-suave">
        {textoResumen}
      </p>

      {/* Botón flotante de guardado */}
      <footer className="sticky bottom-0 z-10 -mx-1 mt-3 flex justify-end bg-gradient-to-t from-superficie via-superficie/95 to-transparent px-1 pb-2 pt-3">
        <button
          type="button"
          onClick={onGuardar}
          disabled={guardando || totalHoras === 0}
          className="cursor-pointer rounded-lg bg-accent px-4 py-[0.65rem] font-semibold text-white transition-all duration-200 enabled:hover:bg-accent-hover disabled:cursor-not-allowed disabled:opacity-60"
        >
          {guardando && (
            <Loader2 className="mr-1 inline animate-spin" size={16} />
          )}
          {guardando ? "Guardando…" : "Guardar disponibilidad"}
        </button>
      </footer>
    </div>
  );
}