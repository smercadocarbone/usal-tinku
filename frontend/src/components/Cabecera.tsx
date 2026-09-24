"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Settings, ShieldCheck, UserRound } from "lucide-react";
import { clearSession } from "@/lib/auth";
import { useSesion } from "@/lib/useSesion";
import { usePerfilPropio, useRolAdmin } from "@/lib/usePerfil";
import { itemActivo, navegacionPorRol } from "@/lib/navegacion";
import { cn } from "@/lib/cn";
import { Avatar, Menu, clasesBoton } from "@/components/ui";
import Logo from "./Logo";
import IconoNav from "./shell/IconoNav";

export interface CabeceraProps {
  /**
   * `publica`: logo, "Buscar tutores", "Ingresar", "Crear cuenta".
   * `auto` (default): pública sin sesión; con sesión, navegación según el rol.
   */
  variante?: "auto" | "publica";
  /** Cabecera transparente sobre un hero (solo la landing). */
  transparente?: boolean;
}

/**
 * La única cabecera de la app (UX-01 §4). En mobile queda logo + avatar; la
 * navegación principal pasa a la barra inferior (`NavInferior`).
 */
export default function Cabecera({ variante = "auto", transparente }: CabeceraProps) {
  const sesion = useSesion();
  const logueado = variante === "auto" && !!sesion;

  return (
    <header
      className={cn(
        "sticky top-0 z-30 w-full",
        transparente ? "bg-transparent" : "border-b border-borde/80 bg-superficie/85 backdrop-blur-md supports-[backdrop-filter]:bg-superficie/75"
      )}
    >
      <div className="mx-auto flex h-16 max-w-[1120px] items-center justify-between gap-6 px-4 sm:px-6">
        <Logo href={logueado ? (sesion?.payload.tipo === "TUTOR" ? "/cuenta/horarios" : "/buscar") : "/"} />
        {logueado ? <CabeceraUsuario /> : <CabeceraPublica />}
      </div>
    </header>
  );
}

function CabeceraPublica() {
  return (
    <nav aria-label="Principal" className="flex items-center gap-1 sm:gap-2">
      <Link href="/#como-funciona" className="hidden min-h-11 items-center rounded-control px-3 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida md:inline-flex">
        Cómo funciona
      </Link>
      <Link href="/registro/tutor" className="hidden min-h-11 items-center rounded-control px-3 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida md:inline-flex">
        Dar clases
      </Link>
      <Link href="/login" className={clasesBoton("fantasma", "md", "text-tinta")}>
        Ingresar
      </Link>
      <Link href="/registro" className={clasesBoton("oscuro", "md", "rounded-pastilla")}>
        Crear cuenta
      </Link>
    </nav>
  );
}

function CabeceraUsuario() {
  const sesion = useSesion();
  const perfil = usePerfilPropio();
  const rol = useRolAdmin();
  const pathname = usePathname();
  const router = useRouter();
  const items = navegacionPorRol(sesion?.payload, rol !== null);

  function salir() {
    clearSession();
    try {
      window.sessionStorage.clear();
    } catch {
      /* nada que limpiar */
    }
    router.replace("/");
  }

  const nombre = perfil ? `${perfil.nombre} ${perfil.apellido}` : "Tu cuenta";

  return (
    <div className="flex items-center gap-2">
      <nav aria-label="Principal" className="hidden lg:block">
        <ul className="flex list-none items-center gap-1 p-0">
          {items.map((it) => {
            const activo = itemActivo(it, pathname);
            return (
              <li key={it.href}>
                <Link
                  href={it.href}
                  aria-current={activo ? "page" : undefined}
                  className={cn(
                    "inline-flex min-h-11 items-center gap-2 rounded-pastilla px-4 text-[15px] font-semibold no-underline transition-colors",
                    activo ? "bg-tinta text-white" : "text-tinta hover:bg-superficie-hundida"
                  )}
                >
                  <IconoNav icono={it.icono} className="size-[18px]" />
                  {it.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <Menu
        etiqueta="Menú de tu cuenta"
        disparador={
          <span className="flex items-center gap-2">
            {perfil ? (
              <Avatar nombre={perfil.nombre} apellido={perfil.apellido} semilla={perfil.id} tamano="sm" />
            ) : (
              <span className="inline-flex size-10 items-center justify-center rounded-full bg-superficie-hundida">
                <UserRound className="size-5" aria-hidden />
              </span>
            )}
            <span className="sr-only">Menú de {nombre}</span>
          </span>
        }
        items={[
          { texto: "Mi cuenta", icono: <Settings />, onClick: () => router.push("/cuenta") },
          ...(rol ? [{ texto: "Panel de administración", icono: <ShieldCheck />, onClick: () => router.push("/admin") }] : []),
          { texto: "Cerrar sesión", icono: <LogOut />, onClick: salir },
        ]}
      />
    </div>
  );
}
