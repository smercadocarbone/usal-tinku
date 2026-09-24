import type { ButtonHTMLAttributes, ReactNode } from "react";
import { Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

export type VarianteBoton = "primario" | "secundario" | "fantasma" | "peligro" | "oscuro";
export type TamanoBoton = "sm" | "md" | "lg";

const BASE =
  "inline-flex cursor-pointer select-none items-center justify-center gap-2 rounded-control font-semibold no-underline " +
  "transition-[background-color,border-color,color,box-shadow,transform] duration-150 ease-out " +
  "motion-safe:enabled:active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50 aria-busy:cursor-wait";

/**
 * Variantes (UX-01 §3). `primario` es UNA por pantalla: la acción que la pantalla
 * existe para hacer. `oscuro` es el primario sobre fondos de marca (hero, barras).
 * El texto de marca siempre es `marca-700` o más oscuro: sobre blanco pasa AA.
 */
const VARIANTES: Record<VarianteBoton, string> = {
  primario:
    "bg-marca-700 text-white shadow-[inset_0_-1px_0_rgb(0_0_0/0.15)] enabled:hover:bg-marca-800",
  secundario:
    "border border-borde-fuerte bg-superficie text-tinta enabled:hover:border-tinta enabled:hover:bg-fondo",
  fantasma: "bg-transparent text-tinta-suave enabled:hover:bg-superficie-hundida enabled:hover:text-tinta",
  peligro: "bg-peligro text-white enabled:hover:bg-[#991b1b]",
  oscuro: "bg-tinta text-white enabled:hover:bg-black",
};

// Alto mínimo 44 px en md/lg: target táctil de WCAG en mobile.
const TAMANOS: Record<TamanoBoton, string> = {
  sm: "min-h-9 px-3 text-sm",
  md: "min-h-11 px-4 text-[15px]",
  lg: "min-h-13 px-6 text-base",
};

/**
 * Clases del botón, aparte para que un `<Link>` que visualmente es un botón no las
 * duplique (un link y un botón son cosas distintas para el teclado y el lector de
 * pantalla: no hay prop `as` polimórfico).
 */
export function clasesBoton(
  variante: VarianteBoton = "primario",
  tamano: TamanoBoton = "md",
  className?: string
): string {
  return cn(BASE, VARIANTES[variante], TAMANOS[tamano], className);
}

export interface BotonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: VarianteBoton;
  tamano?: TamanoBoton;
  /** Bloquea el doble click y muestra un spinner con `textoCargando` (o los children). */
  cargando?: boolean;
  textoCargando?: ReactNode;
  /** Ícono a la izquierda del texto (decorativo). */
  icono?: ReactNode;
  anchoCompleto?: boolean;
}

export default function Boton({
  variante = "primario",
  tamano = "md",
  cargando = false,
  textoCargando,
  icono,
  anchoCompleto,
  className,
  children,
  disabled,
  type = "button",
  ...props
}: BotonProps) {
  return (
    <button
      type={type}
      className={clasesBoton(variante, tamano, cn(anchoCompleto && "w-full", className))}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      {...props}
    >
      {cargando ? (
        <Loader2 className="size-4 motion-safe:animate-spin" aria-hidden />
      ) : (
        icono && <span aria-hidden className="-ml-0.5 inline-flex [&>svg]:size-[18px]">{icono}</span>
      )}
      {cargando && textoCargando ? textoCargando : children}
    </button>
  );
}
Boton.displayName = "Boton";
