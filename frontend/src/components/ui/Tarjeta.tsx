import type { CSSProperties, ElementType, ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface TarjetaProps {
  children: ReactNode;
  className?: string;
  /** Elemento a renderizar. `article`/`li` cuando la tarjeta es un ítem de una lista. */
  as?: ElementType;
  /** Elevación al pasar el mouse: solo para tarjetas que son enlaces o acciones. */
  interactiva?: boolean;
  /** Escape hatch para casos que Tailwind no cubre (ej. un `transitionDelay` calculado por índice). */
  style?: CSSProperties;
}

export default function Tarjeta({
  children,
  className,
  as: Componente = "div",
  interactiva = false,
  style,
}: TarjetaProps) {
  return (
    <Componente
      style={style}
      className={cn(
        "rounded-2xl border border-slate-200 bg-white p-6 shadow-sm",
        interactiva && "transition hover:-translate-y-0.5 hover:shadow-lg",
        className
      )}
    >
      {children}
    </Componente>
  );
}
