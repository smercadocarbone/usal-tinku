"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { rolDeRutaAdmin, rutaAdminPermitida } from "@/lib/navegacion";

import type { ReactNode } from "react";
import { FlaskConical } from "lucide-react";
import type { RolAdmin } from "@/lib/api";
import AppShell from "@/components/shell/AppShell";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";
import { ProveedorAdmin, useAdmin, type Cola } from "@/components/admin/ContextoAdmin";
import { Alerta } from "@/components/ui";

// Orden por el Artículo II (seguridad del menor primero): Alertas de kill-switch >
// Denuncias > Credenciales > lo financiero > soporte > infraestructura.
interface ItemAdmin {
  href: string;
  label: string;
  cola?: Cola["clave"];
}
interface GrupoAdmin {
  titulo: string;
  /** Solo visible para este rol; sin la propiedad, para todo Admin (B10). */
  rol?: RolAdmin;
  items: ItemAdmin[];
}

const GRUPOS: GrupoAdmin[] = [
  { titulo: "Panel", items: [{ href: "/admin", label: "Resumen" }] },
  {
    titulo: "Seguridad",
    rol: "moderacion_seguridad",
    items: [
      { href: "/admin/alertas", label: "Alertas de Seguridad", cola: "alertas" },
      { href: "/admin/denuncias", label: "Denuncias", cola: "denuncias" },
      { href: "/admin/credenciales", label: "Credenciales", cola: "credenciales" },
      { href: "/admin/antecedentes", label: "Antecedentes (CAP)" },
      { href: "/admin/temas-sugeridos", label: "Temas sugeridos" },
    ],
  },
  {
    titulo: "Financiero",
    rol: "soporte_financiero",
    items: [
      { href: "/admin/pagos", label: "Pagos fallidos", cola: "pagos" },
      { href: "/admin/precios", label: "Precios regionales" },
    ],
  },
  { titulo: "Soporte", items: [{ href: "/admin/tickets", label: "Tickets de soporte", cola: "tickets" }] },
  { titulo: "Sistema", items: [{ href: "/admin/salud", label: "Salud de Infraestructura" }] },
];

const NOMBRE_ROL: Record<RolAdmin, string> = {
  moderacion_seguridad: "Moderación y Seguridad",
  soporte_financiero: "Soporte Financiero",
};

function Contenido({ children }: { children: ReactNode }) {
  const { rol, rolResuelto, colas, bypass } = useAdmin();
  const pathname = usePathname();

  // T3-03 / T5-1: quien no es admin no ve el panel ni queda en "Cargando colas…" para siempre.
  if (rolResuelto && !rol) {
    return (
      <AppShell>
        <h1 className="text-[28px] font-extrabold sm:text-[40px]">Panel de administración</h1>
        <Alerta tono="info" className="mt-6" titulo="Esta sección es solo para el equipo de Tinku">
          Desde <Link href="/cuenta" className="font-semibold underline">Mi cuenta</Link> tenés todo lo tuyo.
        </Alerta>
      </AppShell>
    );
  }

  // Ocultar del menú lo que el rol no puede usar es UX, no seguridad: el backend
  // valida el rol en cada cola igual (B10). Sin rol conocido, no hay menú.
  const grupos: GrupoNavAjustes[] = rol
    ? GRUPOS.filter((g) => !g.rol || g.rol === rol).map((g) => ({
        titulo: g.titulo,
        items: g.items.map((i) => ({
          href: i.href,
          label: i.label,
          contador: i.cola ? colas[i.cola]?.cantidad : undefined,
          urgente: i.cola ? colas[i.cola]?.urgente : undefined,
        })),
      }))
    : [];

  // R-F F-6 (T4-H6): una sección de otro rol no se monta (no pide nada al backend).
  const requerido = rolDeRutaAdmin(pathname);
  const permitida = rutaAdminPermitida(pathname, rol);

  return (
    <AppShell>
      {bypass && (
        <Alerta tono="aviso" className="mb-6" titulo="Modo Bypass activo">
          <span className="inline-flex items-center gap-1.5">
            <FlaskConical className="size-4" aria-hidden /> La pasarela de pagos está apagada: las reservas se confirman sin cobro real.
          </span>
        </Alerta>
      )}
      <h1 className="text-[28px] font-extrabold sm:text-[40px]">Panel de administración</h1>
      {rol && <p className="mt-1 text-[15px] text-tinta-suave">{NOMBRE_ROL[rol]}</p>}
      <SettingsShell base="/admin" grupos={grupos}>
        {!rolResuelto ? null : permitida ? (
          children
        ) : (
          <Alerta tono="info" titulo={`Esta sección es de ${requerido ? NOMBRE_ROL[requerido] : "otro rol"}`}>
            Volvé al <Link href="/admin" className="font-semibold underline">resumen del panel</Link>.
          </Alerta>
        )}
      </SettingsShell>
    </AppShell>
  );
}

export default function LayoutAdmin({ children }: { children: ReactNode }) {
  return (
    <ProveedorAdmin>
      <Contenido>{children}</Contenido>
    </ProveedorAdmin>
  );
}
