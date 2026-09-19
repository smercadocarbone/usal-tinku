"use client";

import { useRef, type ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface OpcionTab<T extends string> {
  id: T;
  label: ReactNode;
}

export interface TabsProps<T extends string> {
  opciones: ReadonlyArray<OpcionTab<T>>;
  activo: T;
  onCambio: (id: T) => void;
  /** Nombre del grupo para lectores de pantalla ("Secciones del panel", etc.). */
  etiqueta: string;
  variante?: "subrayado" | "segmentado";
  className?: string;
}

/**
 * Tabs según el patrón WAI-ARIA, no botones disfrazados.
 *
 * Lo que había antes usaba `aria-pressed`, que describe un botón que queda
 * apretado — no una pestaña. La diferencia no es cosmética: un lector de
 * pantalla anuncia "pestaña 2 de 4" solo con `role="tab"` dentro de un
 * `role="tablist"`, y ahí recién la persona sabe cuántas hay y dónde está.
 *
 * Suma navegación por flechas con foco móvil (un solo tab-stop para todo el
 * grupo), que es lo que el patrón exige y ninguna de las tres pantallas tenía.
 */
export default function Tabs<T extends string>({
  opciones,
  activo,
  onCambio,
  etiqueta,
  variante = "subrayado",
  className,
}: TabsProps<T>) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  function alTeclado(e: React.KeyboardEvent, i: number) {
    const ultimo = opciones.length - 1;
    let destino: number | null = null;
    if (e.key === "ArrowRight") destino = i === ultimo ? 0 : i + 1;
    else if (e.key === "ArrowLeft") destino = i === 0 ? ultimo : i - 1;
    else if (e.key === "Home") destino = 0;
    else if (e.key === "End") destino = ultimo;
    if (destino === null) return;

    e.preventDefault();
    const opcion = opciones[destino];
    if (!opcion) return;
    onCambio(opcion.id);
    refs.current[destino]?.focus();
  }

  return (
    <div
      role="tablist"
      aria-label={etiqueta}
      className={cn(
        "no-scrollbar flex overflow-x-auto",
        variante === "subrayado"
          ? "gap-6 border-b border-slate-200"
          : "w-fit gap-1 rounded-lg bg-slate-100 p-1",
        className
      )}
    >
      {opciones.map((o, i) => {
        const seleccionado = o.id === activo;
        return (
          <button
            key={o.id}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="tab"
            id={`tab-${o.id}`}
            aria-selected={seleccionado}
            aria-controls={`panel-${o.id}`}
            // Foco móvil: solo la pestaña activa entra en el orden de tabulación.
            tabIndex={seleccionado ? 0 : -1}
            onClick={() => onCambio(o.id)}
            onKeyDown={(e) => alTeclado(e, i)}
            className={cn(
              "cursor-pointer whitespace-nowrap transition-colors",
              variante === "subrayado"
                ? seleccionado
                  ? "border-b-2 border-teal-700 pb-3 text-sm font-semibold text-teal-700"
                  : "border-b-2 border-transparent pb-3 text-sm font-medium text-slate-500 hover:text-slate-800"
                : seleccionado
                  ? "rounded-md bg-teal-700 px-4 py-1.5 text-sm font-semibold text-white"
                  : "rounded-md px-4 py-1.5 text-sm font-medium text-slate-600 hover:text-slate-900"
            )}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

export interface PanelTabProps {
  /** Mismo id que la opción de `Tabs` que lo controla. */
  id: string;
  children: ReactNode;
  className?: string;
}

/** Contenido de una pestaña. Cierra el vínculo `aria-controls` → `aria-labelledby`. */
export function PanelTab({ id, children, className }: PanelTabProps) {
  return (
    <div role="tabpanel" id={`panel-${id}`} aria-labelledby={`tab-${id}`} tabIndex={0} className={className}>
      {children}
    </div>
  );
}
