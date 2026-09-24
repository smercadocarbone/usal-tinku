"use client";

import { useEffect, useId, useRef, useState, type ReactNode } from "react";
import { MoreHorizontal } from "lucide-react";
import { cn } from "@/lib/cn";

export interface ItemMenu {
  texto: string;
  onClick: () => void;
  icono?: ReactNode;
  peligro?: boolean;
}

export interface MenuProps {
  /** Nombre accesible del botón ("Más opciones de este perfil"). */
  etiqueta: string;
  items: ItemMenu[];
  /** Contenido del disparador; por defecto "⋯". */
  disparador?: ReactNode;
  alineacion?: "izquierda" | "derecha";
  className?: string;
}

/**
 * Menú de acciones secundarias (patrón WAI-ARIA menu button). Lo que no es la acción
 * principal de la pantalla va acá, nunca al lado del CTA (UX-04 §2: "Denunciar").
 */
export default function Menu({ etiqueta, items, disparador, alineacion = "derecha", className }: MenuProps) {
  const [abierto, setAbierto] = useState(false);
  const id = useId();
  const contenedor = useRef<HTMLDivElement>(null);
  const refs = useRef<Array<HTMLButtonElement | null>>([]);

  useEffect(() => {
    if (!abierto) return;
    refs.current[0]?.focus();
    const fuera = (e: MouseEvent) => {
      if (!contenedor.current?.contains(e.target as Node)) setAbierto(false);
    };
    document.addEventListener("mousedown", fuera);
    return () => document.removeEventListener("mousedown", fuera);
  }, [abierto]);

  function teclado(e: React.KeyboardEvent, i: number) {
    const n = items.length;
    let d: number | null = null;
    if (e.key === "ArrowDown") d = (i + 1) % n;
    else if (e.key === "ArrowUp") d = (i - 1 + n) % n;
    else if (e.key === "Home") d = 0;
    else if (e.key === "End") d = n - 1;
    else if (e.key === "Escape" || e.key === "Tab") {
      setAbierto(false);
      if (e.key === "Escape") document.getElementById(`${id}-btn`)?.focus();
      return;
    }
    if (d === null) return;
    e.preventDefault();
    refs.current[d]?.focus();
  }

  return (
    <div ref={contenedor} className={cn("relative inline-block", className)}>
      <button
        id={`${id}-btn`}
        type="button"
        aria-haspopup="menu"
        aria-expanded={abierto}
        aria-controls={`${id}-menu`}
        aria-label={disparador ? undefined : etiqueta}
        onClick={() => setAbierto((a) => !a)}
        className="inline-flex min-h-11 min-w-11 cursor-pointer items-center justify-center gap-2 rounded-full px-2 text-tinta-suave hover:bg-superficie-hundida hover:text-tinta"
      >
        {disparador ?? <MoreHorizontal className="size-5" aria-hidden />}
      </button>
      {abierto && (
        <div
          id={`${id}-menu`}
          role="menu"
          aria-labelledby={`${id}-btn`}
          className={cn(
            "absolute z-40 mt-1 min-w-56 overflow-hidden rounded-control border border-borde bg-superficie p-1.5 shadow-flotante motion-safe:animate-aparecer",
            alineacion === "derecha" ? "right-0" : "left-0"
          )}
        >
          {items.map((it, i) => (
            <button
              key={it.texto}
              ref={(el) => {
                refs.current[i] = el;
              }}
              type="button"
              role="menuitem"
              tabIndex={-1}
              onKeyDown={(e) => teclado(e, i)}
              onClick={() => {
                setAbierto(false);
                it.onClick();
              }}
              className={cn(
                "flex min-h-11 w-full cursor-pointer items-center gap-3 rounded-lg px-3 text-left text-[15px] font-medium focus:outline-none",
                it.peligro ? "text-peligro hover:bg-peligro-suave focus:bg-peligro-suave" : "text-tinta hover:bg-fondo focus:bg-fondo"
              )}
            >
              {it.icono && <span aria-hidden className="inline-flex [&>svg]:size-[18px]">{it.icono}</span>}
              {it.texto}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
Menu.displayName = "Menu";
