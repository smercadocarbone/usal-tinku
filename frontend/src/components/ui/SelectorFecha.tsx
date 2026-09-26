"use client";

import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";
import { clasesControl } from "./Campo";

/**
 * Selector de fecha propio (reemplaza a `<input type="date">`, que cambia según el
 * navegador y el dispositivo, y en algunos celulares no deja ir rápido a otro año).
 *
 * - El valor entra y sale como ISO `YYYY-MM-DD` (mismo contrato que el input nativo): `""`
 *   mientras la fecha esté incompleta o sea inválida.
 * - Se puede escribir (`DD/MM/AAAA`, con o sin barras, o ISO) o elegir en el calendario.
 * - El calendario sigue el patrón de grilla de WAI-ARIA: flechas mueven de a un día o una
 *   semana, RePág/AvPág de a un mes, Inicio/Fin a la semana, Enter elige, Escape cierra.
 * - Cabecera con mes y año como listas: para una fecha de nacimiento se llega en dos toques.
 * - `min`/`max` (ISO) deshabilitan lo que queda fuera, también al escribir.
 */
export interface SelectorFechaProps {
  id: string;
  etiqueta: ReactNode;
  value: string;
  onChange: (iso: string) => void;
  min?: string;
  max?: string;
  error?: string | null;
  ayuda?: ReactNode;
  required?: boolean;
  disabled?: boolean;
  className?: string;
  /** Mes que muestra el calendario vacío (ISO); por defecto hoy, acotado a min/max. */
  mesInicial?: string;
  autoComplete?: string;
}

const MESES = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];
const DIAS = ["lu", "ma", "mi", "ju", "vi", "sá", "do"];
const DIAS_LARGOS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"];

interface Fecha {
  a: number;
  m: number; // 0-11
  d: number;
}

function pad(n: number) {
  return String(n).padStart(2, "0");
}

export function aIso(f: Fecha): string {
  return `${f.a}-${pad(f.m + 1)}-${pad(f.d)}`;
}

function valida(a: number, m: number, d: number): boolean {
  if (a < 1900 || a > 2200 || m < 0 || m > 11 || d < 1) return false;
  return d <= diasDelMes(a, m);
}

function diasDelMes(a: number, m: number) {
  return new Date(Date.UTC(a, m + 1, 0)).getUTCDate();
}

export function deIso(iso: string | undefined | null): Fecha | null {
  const r = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso ?? "");
  if (!r) return null;
  const f = { a: +r[1], m: +r[2] - 1, d: +r[3] };
  return valida(f.a, f.m, f.d) ? f : null;
}

/** Interpreta lo escrito: ISO (`1995-04-10`), `10/04/1995`, `10-4-1995` o `10041995`. */
export function interpretarFecha(texto: string): Fecha | null {
  const t = texto.trim();
  const iso = deIso(t);
  if (iso) return iso;
  let r = /^(\d{1,2})[/.\-\s](\d{1,2})[/.\-\s](\d{4})$/.exec(t);
  if (!r) r = /^(\d{2})(\d{2})(\d{4})$/.exec(t);
  if (!r) return null;
  const f = { a: +r[3], m: +r[2] - 1, d: +r[1] };
  return valida(f.a, f.m, f.d) ? f : null;
}

function mostrar(f: Fecha | null): string {
  return f ? `${pad(f.d)}/${pad(f.m + 1)}/${f.a}` : "";
}

function comparar(x: Fecha, y: Fecha) {
  return x.a - y.a || x.m - y.m || x.d - y.d;
}

function sumarDias(f: Fecha, n: number): Fecha {
  const dt = new Date(Date.UTC(f.a, f.m, f.d + n));
  return { a: dt.getUTCFullYear(), m: dt.getUTCMonth(), d: dt.getUTCDate() };
}

function sumarMeses(f: Fecha, n: number): Fecha {
  const total = f.a * 12 + f.m + n;
  const a = Math.floor(total / 12);
  const m = total % 12;
  return { a, m, d: Math.min(f.d, diasDelMes(a, m)) };
}

function hoy(): Fecha {
  const dt = new Date();
  return { a: dt.getFullYear(), m: dt.getMonth(), d: dt.getDate() };
}

/** Lunes = 0 … domingo = 6 (en Argentina la semana arranca el lunes). */
function diaSemana(f: Fecha) {
  return (new Date(Date.UTC(f.a, f.m, f.d)).getUTCDay() + 6) % 7;
}

export default function SelectorFecha({
  id,
  etiqueta,
  value,
  onChange,
  min,
  max,
  error,
  ayuda,
  required,
  disabled,
  className,
  mesInicial,
  autoComplete,
}: SelectorFechaProps) {
  const fMin = useMemo(() => deIso(min), [min]);
  const fMax = useMemo(() => deIso(max), [max]);
  const seleccion = useMemo(() => deIso(value), [value]);
  const [texto, setTexto] = useState(mostrar(seleccion));
  const [abierto, setAbierto] = useState(false);
  const [foco, setFoco] = useState<Fecha>(() => seleccion ?? acotar(deIso(mesInicial) ?? hoy()));
  const [errorTexto, setErrorTexto] = useState<string | null>(null);
  const raiz = useRef<HTMLDivElement>(null);
  const grilla = useRef<HTMLDivElement>(null);
  const idCalendario = useId();

  function acotar(f: Fecha): Fecha {
    if (fMin && comparar(f, fMin) < 0) return fMin;
    if (fMax && comparar(f, fMax) > 0) return fMax;
    return f;
  }

  function fueraDeRango(f: Fecha) {
    return (fMin && comparar(f, fMin) < 0) || (fMax && comparar(f, fMax) > 0);
  }

  // Si el valor cambia desde afuera, el texto lo sigue (sin pisar lo que se está escribiendo).
  useEffect(() => {
    const actual = interpretarFecha(texto);
    if ((actual ? aIso(actual) : "") !== value) setTexto(mostrar(seleccion));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  // Cerrar al hacer clic afuera.
  useEffect(() => {
    if (!abierto) return;
    function fuera(e: MouseEvent) {
      if (raiz.current && !raiz.current.contains(e.target as Node)) setAbierto(false);
    }
    document.addEventListener("mousedown", fuera);
    return () => document.removeEventListener("mousedown", fuera);
  }, [abierto]);

  // Al abrir o mover el foco, el día enfocado recibe el foco real (tabindex móvil).
  useEffect(() => {
    if (!abierto) return;
    grilla.current?.querySelector<HTMLButtonElement>(`[data-fecha="${aIso(foco)}"]`)?.focus();
  }, [abierto, foco]);

  function escribir(t: string) {
    setTexto(t);
    const f = interpretarFecha(t);
    if (!f) {
      setErrorTexto(null);
      onChange("");
      return;
    }
    if (fueraDeRango(f)) {
      setErrorTexto(fMin && comparar(f, fMin) < 0 ? `Tiene que ser desde el ${mostrar(fMin)}.` : `Tiene que ser hasta el ${mostrar(fMax)}.`);
      onChange("");
      return;
    }
    setErrorTexto(null);
    setFoco(f);
    onChange(aIso(f));
  }

  function elegir(f: Fecha) {
    if (fueraDeRango(f)) return;
    setTexto(mostrar(f));
    setErrorTexto(null);
    onChange(aIso(f));
    setAbierto(false);
    document.getElementById(id)?.focus();
  }

  function abrir() {
    setFoco(acotar(seleccion ?? deIso(mesInicial) ?? hoy()));
    setAbierto(true);
  }

  function teclaGrilla(e: KeyboardEvent) {
    const mover: Record<string, () => Fecha> = {
      ArrowLeft: () => sumarDias(foco, -1),
      ArrowRight: () => sumarDias(foco, 1),
      ArrowUp: () => sumarDias(foco, -7),
      ArrowDown: () => sumarDias(foco, 7),
      PageUp: () => sumarMeses(foco, e.shiftKey ? -12 : -1),
      PageDown: () => sumarMeses(foco, e.shiftKey ? 12 : 1),
      Home: () => sumarDias(foco, -diaSemana(foco)),
      End: () => sumarDias(foco, 6 - diaSemana(foco)),
    };
    if (mover[e.key]) {
      e.preventDefault();
      setFoco(acotar(mover[e.key]()));
    } else if (e.key === "Escape") {
      e.preventDefault();
      setAbierto(false);
      document.getElementById(id)?.focus();
    }
  }

  // Años para la lista: del mínimo al máximo (o ±100 años alrededor de hoy).
  const anios = useMemo(() => {
    const desde = fMin?.a ?? hoy().a - 100;
    const hasta = fMax?.a ?? hoy().a + 10;
    return Array.from({ length: hasta - desde + 1 }, (_, i) => hasta - i);
  }, [fMin, fMax]);

  const primeroDelMes: Fecha = { a: foco.a, m: foco.m, d: 1 };
  const huecos = diaSemana(primeroDelMes);
  const cantidad = diasDelMes(foco.a, foco.m);
  // Cada celda con su clave: los huecos antes del día 1 se nombran por posición en el mes.
  const celdas: { clave: string; f: Fecha | null }[] = [
    ...Array.from({ length: huecos }, (_, i) => ({ clave: `hueco-${foco.a}-${foco.m}-${i}`, f: null })),
    ...Array.from({ length: cantidad }, (_, i) => {
      const f = { a: foco.a, m: foco.m, d: i + 1 };
      return { clave: aIso(f), f };
    }),
  ];
  const semanas: { clave: string; celdas: typeof celdas }[] = [];
  for (let i = 0; i < celdas.length; i += 7) {
    const fila = celdas.slice(i, i + 7);
    semanas.push({ clave: `semana-${fila[fila.length - 1].clave}`, celdas: fila });
  }
  const hoyF = hoy();
  const mensajeError = error ?? errorTexto;
  const describedBy = [ayuda ? `${id}-ayuda` : null, mensajeError ? `${id}-error` : null].filter(Boolean).join(" ") || undefined;
  const mesAnteriorDeshabilitado = !!fMin && foco.m === fMin.m && foco.a === fMin.a;
  const mesSiguienteDeshabilitado = !!fMax && foco.m === fMax.m && foco.a === fMax.a;

  return (
    <div ref={raiz} className={cn("relative flex flex-col gap-1.5", className)}>
      <label htmlFor={id} className="text-sm font-semibold text-tinta">
        {etiqueta}
      </label>
      <div className="relative">
        <input
          id={id}
          type="text"
          inputMode="numeric"
          autoComplete={autoComplete}
          placeholder="DD/MM/AAAA"
          value={texto}
          required={required}
          disabled={disabled}
          aria-invalid={mensajeError ? true : undefined}
          aria-describedby={describedBy}
          onChange={(e) => escribir(e.target.value)}
          onBlur={() => {
            const f = interpretarFecha(texto);
            if (f && !fueraDeRango(f)) setTexto(mostrar(f));
          }}
          onKeyDown={(e) => {
            if (e.key === "ArrowDown" && e.altKey) {
              e.preventDefault();
              abrir();
            }
          }}
          className={cn(clasesControl, "pr-12")}
        />
        <button
          type="button"
          disabled={disabled}
          aria-label="Abrir calendario"
          aria-haspopup="dialog"
          aria-expanded={abierto}
          aria-controls={abierto ? idCalendario : undefined}
          onClick={() => (abierto ? setAbierto(false) : abrir())}
          className="absolute right-1 top-1/2 flex size-10 -translate-y-1/2 items-center justify-center rounded-control text-tinta-suave hover:bg-superficie-hundida hover:text-tinta disabled:opacity-50"
        >
          <CalendarDays className="size-5" aria-hidden />
        </button>
      </div>
      {ayuda && (
        <span id={`${id}-ayuda`} className="text-[13px] text-tinta-tenue">
          {ayuda}
        </span>
      )}
      {mensajeError && (
        <span id={`${id}-error`} className="text-[13px] font-medium text-peligro">
          {mensajeError}
        </span>
      )}

      {abierto && (
        <div
          id={idCalendario}
          role="dialog"
          aria-modal="false"
          aria-label="Elegí una fecha"
          className="absolute left-0 top-full z-30 mt-2 w-full min-w-[18rem] max-w-[22rem] rounded-tarjeta border border-borde bg-superficie p-3 shadow-elevado"
        >
          <div className="flex items-center gap-1">
            <button
              type="button"
              aria-label="Mes anterior"
              disabled={mesAnteriorDeshabilitado}
              onClick={() => setFoco(acotar(sumarMeses(foco, -1)))}
              className="flex size-10 items-center justify-center rounded-control hover:bg-superficie-hundida disabled:opacity-40"
            >
              <ChevronLeft className="size-5" aria-hidden />
            </button>
            <select
              aria-label="Mes"
              value={foco.m}
              onChange={(e) => setFoco(acotar({ ...foco, m: +e.target.value, d: Math.min(foco.d, diasDelMes(foco.a, +e.target.value)) }))}
              className="min-h-10 flex-1 rounded-control bg-transparent px-1 text-[15px] font-semibold capitalize hover:bg-superficie-hundida"
            >
              {MESES.map((nombre, i) => (
                <option key={nombre} value={i}>
                  {nombre}
                </option>
              ))}
            </select>
            <select
              aria-label="Año"
              value={foco.a}
              onChange={(e) => setFoco(acotar({ ...foco, a: +e.target.value, d: Math.min(foco.d, diasDelMes(+e.target.value, foco.m)) }))}
              className="min-h-10 rounded-control bg-transparent px-1 text-[15px] font-semibold hover:bg-superficie-hundida"
            >
              {anios.map((a) => (
                <option key={a} value={a}>
                  {a}
                </option>
              ))}
            </select>
            <button
              type="button"
              aria-label="Mes siguiente"
              disabled={mesSiguienteDeshabilitado}
              onClick={() => setFoco(acotar(sumarMeses(foco, 1)))}
              className="flex size-10 items-center justify-center rounded-control hover:bg-superficie-hundida disabled:opacity-40"
            >
              <ChevronRight className="size-5" aria-hidden />
            </button>
          </div>

          <div ref={grilla} role="grid" tabIndex={-1} aria-label={`${MESES[foco.m]} de ${foco.a}`} onKeyDown={teclaGrilla} className="mt-2">
            <div role="row" className="grid grid-cols-7">
              {DIAS.map((d, i) => (
                <span key={d} role="columnheader" aria-label={DIAS_LARGOS[i]} className="py-1 text-center text-xs font-semibold uppercase text-tinta-tenue">
                  {d}
                </span>
              ))}
            </div>
            {semanas.map((semana) => (
              <div role="row" key={semana.clave} className="grid grid-cols-7">
                {semana.celdas.map(({ clave, f }) => {
                  if (!f) return <span key={clave} role="gridcell" aria-label="Sin día" />;
                  const iso = aIso(f);
                  const elegido = !!seleccion && comparar(f, seleccion) === 0;
                  const enfocado = comparar(f, foco) === 0;
                  const esHoy = comparar(f, hoyF) === 0;
                  const deshabilitado = !!fueraDeRango(f);
                  return (
                    <span key={iso} role="gridcell" aria-selected={elegido}>
                      <button
                        type="button"
                        data-fecha={iso}
                        tabIndex={enfocado ? 0 : -1}
                        disabled={deshabilitado}
                        aria-label={`${DIAS_LARGOS[diaSemana(f)]} ${f.d} de ${MESES[f.m]} de ${f.a}`}
                        aria-current={esHoy ? "date" : undefined}
                        onClick={() => elegir(f)}
                        className={cn(
                          "mx-auto flex size-10 items-center justify-center rounded-full text-[15px] tabular-nums transition-colors",
                          elegido
                            ? "bg-marca-700 font-bold text-white"
                            : "text-tinta hover:bg-marca-50",
                          esHoy && !elegido && "font-bold ring-1 ring-marca-300",
                          deshabilitado && "cursor-not-allowed text-tinta-tenue opacity-40 hover:bg-transparent"
                        )}
                      >
                        {f.d}
                      </button>
                    </span>
                  );
                })}
              </div>
            ))}
          </div>
          {!fueraDeRango(hoyF) && (
            <div className="mt-2 flex justify-end">
              <button type="button" onClick={() => elegir(hoyF)} className="min-h-10 rounded-control px-3 text-sm font-semibold text-marca-700 hover:bg-marca-50">
                Hoy
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
SelectorFecha.displayName = "SelectorFecha";
