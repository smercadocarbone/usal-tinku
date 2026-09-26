"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { cn } from "@/lib/cn";

export interface ItemNavAjustes {
  href: string;
  label: string;
  /** Contador de pendientes (colas del admin). */
  contador?: number;
  /** El contador es urgente (plazo por vencer). */
  urgente?: boolean;
  /** Otras rutas exactas en las que el ítem se ve activo (p. ej. "Perfil" también en la raíz). */
  activoEn?: string[];
}

export interface GrupoNavAjustes {
  titulo: string;
  items: ItemNavAjustes[];
}

export interface SettingsShellProps {
  /** Ruta raíz del shell ("/cuenta" o "/admin"): decide qué se ve en mobile. */
  base: string;
  grupos: GrupoNavAjustes[];
  children: ReactNode;
}

/**
 * Patrón de navegación tipo Ajustes de Apple: sidebar/lista agrupada +
 * panel de contenido, con rutas reales (no estado de tabs en memoria).
 *
 * Desktop (`lg:`): sidebar fija + panel de contenido, siempre los dos juntos.
 * Mobile (`<lg`): en la raíz del shell se ve la lista agrupada a ancho
 * completo; en cualquier sub-ruta se ve solo el contenido con "← Volver".
 * No hay estado de "abierto/cerrado" propio — el pathname decide qué se
 * ve, así atrás/adelante del navegador funciona nativo, sin JS extra.
 */
export default function SettingsShell({ base, grupos, children }: SettingsShellProps) {
  const pathname = usePathname();
  const enRaiz = pathname === base;

  return (
    <div className="mt-6 flex flex-col gap-8 lg:mt-8 lg:flex-row">
      <aside className={cn("w-full shrink-0 lg:block lg:w-60", !enRaiz && "hidden")}>
        <nav aria-label="Secciones" className="flex flex-col gap-6">
          {grupos.map((grupo) => (
            <div key={grupo.titulo}>
              <p className="px-3 text-xs font-bold uppercase tracking-wider text-tinta-tenue">
                {grupo.titulo}
              </p>
              <ul className="mt-2 flex list-none flex-col gap-0.5 overflow-hidden rounded-tarjeta border border-borde bg-superficie p-1.5 lg:border-0 lg:bg-transparent lg:p-0">
                {grupo.items.map((item) => {
                  // La ruta raíz del shell (p. ej. "/cuenta" = "Perfil") solo
                  // está activa en esa ruta exacta; si no, "Perfil" quedaría
                  // marcado en todas las sub-rutas (B10).
                  const activo =
                    pathname === item.href ||
                    (item.activoEn ?? []).includes(pathname) ||
                    (item.href !== base && pathname.startsWith(`${item.href}/`));
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        aria-current={activo ? "page" : undefined}
                        className={cn(
                          "flex min-h-12 items-center justify-between gap-3 rounded-control px-3.5 text-[15px] font-semibold no-underline transition-colors",
                          activo
                            ? "bg-tinta text-white"
                            : "text-tinta hover:bg-superficie-hundida"
                        )}
                      >
                        {item.label}
                        <span className="flex items-center gap-2">
                          {item.contador !== undefined && item.contador > 0 && (
                            <span
                              className={cn(
                                "tabular inline-flex min-w-6 items-center justify-center rounded-pastilla px-1.5 text-xs font-bold",
                                item.urgente ? "bg-peligro text-white" : activo ? "bg-white/20 text-white" : "bg-superficie-hundida text-tinta"
                              )}
                            >
                              {item.contador}
                              <span className="sr-only"> pendientes{item.urgente ? ", con plazo por vencer" : ""}</span>
                            </span>
                          )}
                          <ChevronRight aria-hidden className={cn("size-4 lg:hidden", activo ? "text-white" : "text-tinta-tenue")} />
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </nav>
      </aside>

      <div className={cn("min-w-0 flex-1", enRaiz && "hidden lg:block")}>
        {!enRaiz && (
          <Link
            href={base}
            className="-ml-2 mb-4 inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida lg:hidden"
          >
            <ChevronLeft className="size-5" aria-hidden /> Volver
          </Link>
        )}
        {children}
      </div>
    </div>
  );
}
