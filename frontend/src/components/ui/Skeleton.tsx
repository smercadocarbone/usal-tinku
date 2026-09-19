import { cn } from "@/lib/cn";

export interface SkeletonProps {
  className?: string;
}

/**
 * Bloque de carga. Es puramente decorativo: va SIEMPRE dentro de un contenedor
 * con `role="status"` y una etiqueta que diga qué se está cargando — si no, un
 * lector de pantalla anuncia silencio mientras la pantalla parece llenarse.
 */
export default function Skeleton({ className }: SkeletonProps) {
  return <div aria-hidden className={cn("animate-pulse rounded bg-slate-200", className)} />;
}
