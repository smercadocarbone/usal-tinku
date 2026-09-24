import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Check, X } from "lucide-react";
import { cn } from "@/lib/cn";

export interface ChipProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Estado del filtro: se refleja en `aria-pressed`, no solo en el color. */
  activo?: boolean;
  /** Chip removible (filtros aplicados, materias elegidas): muestra una X. */
  removible?: boolean;
  icono?: ReactNode;
}

/**
 * Píldora de filtro con estado. No es un `<Boton>`: expresa un estado que se
 * enciende y se apaga, por eso `aria-pressed`.
 */
export default function Chip({ activo = false, removible, icono, className, children, ...props }: ChipProps) {
  return (
    <button
      type="button"
      aria-pressed={removible ? undefined : activo}
      className={cn(
        "inline-flex min-h-10 shrink-0 cursor-pointer items-center gap-1.5 rounded-pastilla border px-4 text-sm font-semibold transition-colors duration-150",
        activo || removible
          ? "border-tinta bg-tinta text-white hover:bg-black"
          : "border-borde-fuerte bg-superficie text-tinta hover:border-tinta",
        className
      )}
      {...props}
    >
      {activo && !removible && <Check className="-ml-1 size-4" aria-hidden />}
      {icono && <span aria-hidden className="-ml-1 inline-flex [&>svg]:size-4">{icono}</span>}
      {children}
      {removible && <X className="-mr-1 size-4 opacity-80" aria-hidden />}
    </button>
  );
}
Chip.displayName = "Chip";
