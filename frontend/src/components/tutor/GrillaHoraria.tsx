"use client";

/** Grilla semanal de disponibilidad tipo Google Calendar.
 *
 * Cada columna es un día (o una fecha puntual); cada fila es un bloque de una
 * hora. El tutor hace clic en un bloque y luego en otro de la misma columna
 * para armar la franja (máximo 3 filas seguidas = 180min, FR-RES-012). Las
 * franjas ya publicadas se pintan como bloques fijos no interactivos.
 */

export const INICIO_DIA = 8; // fila 08:00
export const FIN_DIA = 20; // última fila termina 21:00
export const MAX_FRANJA_FILAS = 3;

export interface Ocupada {
  columna: string;
  desde: number; // fila inclusive
  hasta: number; // fila inclusive
}

export interface Seleccion {
  [columna: string]: { anchor: number; hasta: number } | null;
}

interface Columna {
  clave: string;
  etiqueta: string;
}

interface Props {
  columnas: Columna[];
  ocupadas: Ocupada[];
  seleccion: Seleccion;
  onCelda: (columna: string, fila: number) => void;
}

export function filaEnRango(
  sel: { anchor: number; hasta: number } | null | undefined
): [number, number] | null {
  if (!sel) return null;
  const a = sel.anchor;
  const h = sel.hasta;
  return a <= h ? [a, h] : [h, a];
}

const GUTTER = "3.5rem";

export default function GrillaHoraria({ columnas, ocupadas, seleccion, onCelda }: Props) {
  function estaOcupada(columna: string, fila: number): boolean {
    return ocupadas.some(
      (o) => o.columna === columna && fila >= o.desde && fila <= o.hasta
    );
  }

  function estaSeleccionada(columna: string, fila: number): boolean {
    const rango = filaEnRango(seleccion[columna]);
    return !!rango && fila >= rango[0] && fila <= rango[1];
  }

  return (
    <div className="overflow-x-auto">
      <div
        className="min-w-[42rem]"
        style={{ gridTemplateColumns: `${GUTTER} repeat(${columnas.length}, minmax(0, 1fr))`, display: "grid" }}
      >
        {/* Encabezados */}
        <div className="sticky left-0 z-10 bg-superficie" />
        {columnas.map((c) => (
          <div
            key={c.clave}
            className="border-b border-borde pb-2 text-center text-[0.8rem] font-semibold text-texto"
          >
            {c.etiqueta}
          </div>
        ))}

        {/* Filas de horas */}
        {Array.from({ length: FIN_DIA - INICIO_DIA + 1 }, (_, i) => {
          const fila = INICIO_DIA + i;
          const hora = `${String(fila).padStart(2, "0")}:00`;
          return (
            <div
              key={fila}
              className="contents"
            >
              <div className="sticky left-0 z-10 flex items-center justify-end border-b border-borde bg-superficie pr-2 text-[0.7rem] text-texto-suave">
                {hora}
              </div>
              {columnas.map((c) => {
                const columna = c.clave;
                const ocupada = estaOcupada(columna, fila);
                const marcada = estaSeleccionada(columna, fila);
                const base =
                  "h-9 w-full transition-all duration-200 border-b border-borde/60 ";
                const estilo = ocupada
                  ? "cursor-default bg-teal-100 border-l-4 border-teal-500"
                  : marcada
                    ? "bg-teal-600/20 border-l-4 border-teal-600"
                    : "border-l border-borde hover:bg-teal-600/10 cursor-pointer";
                return (
                  <button
                    key={`${columna}-${fila}`}
                    type="button"
                    disabled={ocupada}
                    aria-label={`${c.etiqueta} ${hora}${marcada ? " (seleccionado)" : ""}`}
                    aria-pressed={marcada}
                    onClick={() => onCelda(columna, fila)}
                    className={`${base} ${estilo}`}
                  />
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}