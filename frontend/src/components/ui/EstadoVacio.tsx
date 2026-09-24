import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface EstadoVacioProps {
  /** Ícono decorativo (se le pone `aria-hidden` acá). */
  icono?: ReactNode;
  titulo?: ReactNode;
  /** Por qué está vacío. */
  children: ReactNode;
  /** Una acción, para que el vacío no sea un callejón sin salida. */
  accion?: ReactNode;
  className?: string;
}

export default function EstadoVacio({ icono, titulo, children, accion, className }: EstadoVacioProps) {
  return (
    <div role="status" className={cn("mx-auto flex max-w-sm flex-col items-center py-12 text-center", className)}>
      {icono && (
        <div
          aria-hidden
          className="mb-5 flex size-16 items-center justify-center rounded-full bg-marca-50 text-marca-700 ring-8 ring-marca-50/50 [&>svg]:size-7"
        >
          {icono}
        </div>
      )}
      {titulo && <p className="text-lg font-bold tracking-tight text-tinta">{titulo}</p>}
      <div className="mt-1.5 text-[15px] leading-relaxed text-tinta-suave">{children}</div>
      {accion && <div className="mt-6 flex justify-center">{accion}</div>}
    </div>
  );
}
EstadoVacio.displayName = "EstadoVacio";
