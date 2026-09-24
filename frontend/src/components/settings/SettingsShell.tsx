"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { cn } from "@/lib/cn";

export interface ItemNavAjustes {
  href: string;
  label: string;
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
    <div className="mt-6 flex flex-col gap-6 lg:flex-row">
      <aside className={cn("w-full shrink-0 lg:block lg:w-56", !enRaiz && "hidden")}>
        <nav aria-label="Secciones" className="flex flex-col gap-6">
          {grupos.map((grupo) => (
            <div key={grupo.titulo}>
              <p className="px-3 text-xs font-semibold uppercase tracking-wide text-slate-400">
                {grupo.titulo}
              </p>
              <ul className="mt-2 flex list-none flex-col gap-1 p-0">
                {grupo.items.map((item) => {
                  // La ruta raíz del shell (p. ej. "/cuenta" = "Perfil") solo
                  // está activa en esa ruta exacta; si no, "Perfil" quedaría
                  // marcado en todas las sub-rutas (B10).
                  const activo =
                    pathname === item.href ||
                    (item.href !== base && pathname.startsWith(`${item.href}/`));
                  return (
                    <li key={item.href}>
                      <Link
                        href={item.href}
                        aria-current={activo ? "page" : undefined}
                        className={cn(
                          "block rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                          activo
                            ? "bg-teal-700 text-white"
                            : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                        )}
                      >
                        {item.label}
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
            className="mb-4 inline-flex items-center gap-1 text-sm font-semibold text-teal-700 lg:hidden"
          >
            ← Volver
          </Link>
        )}
        {children}
      </div>
    </div>
  );
}
