import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

export type VarianteBoton = "primario" | "secundario" | "peligro" | "fantasma";
export type TamanoBoton = "sm" | "md";

const BASE =
  "inline-flex cursor-pointer items-center justify-center gap-2 rounded-lg font-semibold no-underline transition-colors disabled:cursor-not-allowed disabled:opacity-60";

/**
 * Los tonos de marca que llevan texto son `teal-700`, no `teal-600`:
 * teal-600 sobre blanco da 3.75:1 y WCAG AA exige 4.5:1 para texto normal
 * (lo detectó axe en `tests/accesibilidad`). teal-700 da 5.53:1.
 * teal-600 sigue sirviendo para bordes y foco, que solo necesitan 3:1.
 */
const VARIANTES: Record<VarianteBoton, string> = {
  primario: "bg-teal-700 text-white enabled:hover:bg-teal-800",
  secundario:
    "border border-slate-200 bg-transparent text-teal-700 enabled:hover:border-teal-700 enabled:hover:bg-teal-50",
  peligro:
    "border border-red-200 bg-transparent text-red-700 enabled:hover:bg-red-50",
  fantasma: "bg-transparent font-medium text-slate-600 enabled:hover:text-slate-900",
};

const TAMANOS: Record<TamanoBoton, string> = {
  sm: "px-3 py-1.5 text-sm",
  md: "px-4 py-2.5",
};

/**
 * Clases del botón, expuestas aparte para que un `<Link>` que visualmente es
 * un botón no tenga que duplicarlas (no usamos un prop `as` polimórfico: un
 * link y un botón son cosas distintas para el teclado y el lector de pantalla).
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
  /** Deshabilita y muestra `textoCargando` en lugar de los children. */
  cargando?: boolean;
  textoCargando?: ReactNode;
}

export default function Boton({
  variante = "primario",
  tamano = "md",
  cargando = false,
  textoCargando,
  className,
  children,
  disabled,
  type = "button",
  ...props
}: BotonProps) {
  return (
    <button
      type={type}
      className={clasesBoton(variante, tamano, className)}
      disabled={disabled || cargando}
      aria-busy={cargando || undefined}
      {...props}
    >
      {cargando && textoCargando ? textoCargando : children}
    </button>
  );
}
