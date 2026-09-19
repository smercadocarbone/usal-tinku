import { Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

export interface CargandoProps {
  children?: React.ReactNode;
  className?: string;
}

/** Indicador de carga en línea. Anuncia el cambio sin interrumpir la lectura. */
export default function Cargando({ children = "Cargando…", className }: CargandoProps) {
  return (
    <p
      role="status"
      aria-live="polite"
      className={cn("flex items-center gap-2 text-sm text-slate-500", className)}
    >
      <Loader2 className="animate-spin" size={16} aria-hidden />
      {children}
    </p>
  );
}
