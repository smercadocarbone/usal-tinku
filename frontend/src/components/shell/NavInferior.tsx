"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSesion } from "@/lib/useSesion";
import { useRolAdmin } from "@/lib/usePerfil";
import { itemActivo, navegacionPorRol } from "@/lib/navegacion";
import { cn } from "@/lib/cn";
import IconoNav from "./IconoNav";

/** Barra de navegación inferior fija en mobile (UX-01 §4): 2–4 destinos según el rol. */
export default function NavInferior() {
  const sesion = useSesion();
  const rol = useRolAdmin();
  const pathname = usePathname();
  const items = navegacionPorRol(sesion?.payload, rol !== null);
  if (items.length === 0) return null;

  return (
    <nav
      aria-label="Navegación principal"
      className="safe-bottom fixed inset-x-0 bottom-0 z-30 border-t border-borde bg-superficie/95 shadow-barra backdrop-blur-md lg:hidden"
    >
      <ul className="mx-auto flex max-w-lg list-none justify-around p-0 px-2">
        {items.map((it) => {
          const activo = itemActivo(it, pathname);
          return (
            <li key={it.href} className="flex-1">
              <Link
                href={it.href}
                aria-current={activo ? "page" : undefined}
                className={cn(
                  "flex min-h-16 flex-col items-center justify-center gap-1 text-[12px] font-semibold no-underline transition-colors",
                  activo ? "text-tinta" : "text-tinta-tenue"
                )}
              >
                <span
                  className={cn(
                    "flex h-8 w-14 items-center justify-center rounded-pastilla transition-colors duration-200",
                    activo && "bg-marca-100 text-marca-800"
                  )}
                >
                  <IconoNav icono={it.icono} className="size-[22px]" />
                </span>
                {it.label}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
