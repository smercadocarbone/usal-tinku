import { Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

export interface CargandoProps {
  children?: React.ReactNode;
  className?: string;
}

/** Indicador de carga en línea (para acciones cortas; el contenido usa `Skeleton*`). */
export default function Cargando({ children = "Cargando…", className }: CargandoProps) {
  return (
    <p role="status" aria-live="polite" className={cn("flex items-center gap-2 text-sm text-tinta-tenue", className)}>
      <Loader2 className="size-4 motion-safe:animate-spin" aria-hidden />
      {children}
    </p>
  );
}
