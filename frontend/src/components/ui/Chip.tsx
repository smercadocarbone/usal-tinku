import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Estado del filtro. Se refleja en `aria-pressed`, no solo en el color. */
  activo?: boolean;
}

/**
 * Píldora de filtro con estado. No es un `<Boton>`: un botón ejecuta una
 * acción, un chip expresa un estado que se enciende y se apaga — por eso lleva
 * `aria-pressed` y por eso un lector de pantalla lo anuncia distinto.
 */
export default function Chip({ activo = false, className, ...props }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={activo}
      className={cn(
        "shrink-0 cursor-pointer rounded-full px-4 py-2 text-sm font-medium transition",
        activo
          ? "bg-teal-700 text-white shadow-sm"
          : "bg-slate-100 text-slate-700 hover:bg-slate-200",
        className
      )}
      {...props}
    />
  );
}
