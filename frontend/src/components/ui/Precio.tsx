import { cn } from "@/lib/cn";
import { formatearPesos } from "@/lib/formatos";

export interface PrecioProps {
  valor: number | null | undefined;
  /** Agrega "/ hora" en chico. */
  porHora?: boolean;
  tamano?: "sm" | "md" | "lg";
  className?: string;
  /** Texto si no hay precio (el tutor todavía no lo definió). */
  sinValor?: string;
}

const TAMANOS = { sm: "text-sm", md: "text-lg", lg: "text-3xl" } as const;

/** `$ 15.000` con `Intl.NumberFormat('es-AR', ARS)`; números tabulares. */
export default function Precio({ valor, porHora, tamano = "md", className, sinValor = "A consultar" }: PrecioProps) {
  if (valor === null || valor === undefined) {
    return <span className={cn("text-sm font-medium text-tinta-tenue", className)}>{sinValor}</span>;
  }
  return (
    <span className={cn("tabular inline-flex items-baseline gap-1 font-bold tracking-tight text-tinta", TAMANOS[tamano], className)}>
      {formatearPesos(valor)}
      {porHora && <span className="text-[0.55em] font-medium text-tinta-tenue">/ hora</span>}
    </span>
  );
}
Precio.displayName = "Precio";
