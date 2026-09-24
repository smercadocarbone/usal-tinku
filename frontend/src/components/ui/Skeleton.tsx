import { cn } from "@/lib/cn";

export interface SkeletonProps {
  className?: string;
}

/**
 * Bloque de carga, decorativo: va SIEMPRE dentro de un contenedor con
 * `role="status"` y una etiqueta de qué se carga (ver `CargandoLista` y cía.).
 */
export default function Skeleton({ className }: SkeletonProps) {
  return <div aria-hidden className={cn("rounded-lg bg-superficie-hundida motion-safe:animate-pulse", className)} />;
}
Skeleton.displayName = "Skeleton";

function Contenedor({ etiqueta, children, className }: { etiqueta: string; children: React.ReactNode; className?: string }) {
  return (
    <div role="status" aria-live="polite" className={className}>
      <span className="sr-only">{etiqueta}</span>
      {children}
    </div>
  );
}

/** Lista de filas con avatar (clases, resultados, colas). */
export function SkeletonLista({ filas = 3, etiqueta = "Cargando…" }: { filas?: number; etiqueta?: string }) {
  return (
    <Contenedor etiqueta={etiqueta} className="flex flex-col gap-3">
      {Array.from({ length: filas }, (_, i) => (
        <div key={i} className="flex items-center gap-4 rounded-tarjeta border border-borde bg-superficie p-4">
          <Skeleton className="size-12 shrink-0 rounded-full" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="h-3 w-3/5" />
          </div>
          <Skeleton className="hidden h-9 w-24 rounded-control sm:block" />
        </div>
      ))}
    </Contenedor>
  );
}

export function SkeletonTarjetas({ cantidad = 3, etiqueta = "Cargando…" }: { cantidad?: number; etiqueta?: string }) {
  return (
    <Contenedor etiqueta={etiqueta} className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {Array.from({ length: cantidad }, (_, i) => (
        <div key={i} className="flex flex-col gap-4 rounded-tarjeta border border-borde bg-superficie p-5">
          <div className="flex items-center gap-3">
            <Skeleton className="size-14 rounded-full" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-4 w-3/5" />
              <Skeleton className="h-3 w-2/5" />
            </div>
          </div>
          <Skeleton className="h-3 w-full" />
          <Skeleton className="h-3 w-4/5" />
          <Skeleton className="h-10 w-full rounded-control" />
        </div>
      ))}
    </Contenedor>
  );
}

export function SkeletonPerfil({ etiqueta = "Cargando perfil…" }: { etiqueta?: string }) {
  return (
    <Contenedor etiqueta={etiqueta} className="flex flex-col gap-6">
      <div className="flex items-center gap-5">
        <Skeleton className="size-24 rounded-full" />
        <div className="flex flex-1 flex-col gap-3">
          <Skeleton className="h-6 w-1/2" />
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-4 w-1/4" />
        </div>
      </div>
      <Skeleton className="h-28 w-full rounded-tarjeta" />
      <Skeleton className="h-40 w-full rounded-tarjeta" />
    </Contenedor>
  );
}
