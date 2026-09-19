import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface EstadoVacioProps {
  /** Ícono decorativo. Debe venir con `aria-hidden` desde el consumidor. */
  icono?: ReactNode;
  titulo?: ReactNode;
  children: ReactNode;
  /** Acción sugerida (botón o enlace) para que el vacío no sea un callejón sin salida. */
  accion?: ReactNode;
  className?: string;
}

export default function EstadoVacio({
  icono,
  titulo,
  children,
  accion,
  className,
}: EstadoVacioProps) {
  return (
    <div role="status" className={cn("mx-auto max-w-md py-12 text-center", className)}>
      {icono && <div className="mx-auto mb-4 text-slate-300">{icono}</div>}
      {titulo && <p className="text-base font-semibold text-slate-800">{titulo}</p>}
      <p className="mt-1 text-sm text-slate-500">{children}</p>
      {accion && <div className="mt-5 flex justify-center">{accion}</div>}
    </div>
  );
}
