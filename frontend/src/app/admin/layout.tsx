"use client";

import { useEffect, useState, type ReactNode } from "react";
import Cabecera from "@/components/Cabecera";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";
import { api, type RolAdmin } from "@/lib/api";

// Orden por el Artículo II de la Constitución (seguridad del menor primero),
// no por módulo ni alfabético: Alertas de kill-switch > Denuncias >
// Credenciales (bloquea a un Tutor de dar clases) > lo financiero > soporte
// general > infraestructura.
interface GrupoAdmin extends GrupoNavAjustes {
  /** Solo visible para este rol; sin la propiedad, visible para todo Admin. (B10) */
  rol?: RolAdmin;
}

const GRUPOS: GrupoAdmin[] = [
  {
    titulo: "Seguridad",
    rol: "moderacion_seguridad",
    items: [
      { href: "/admin/alertas", label: "Alertas de Seguridad" },
      { href: "/admin/denuncias", label: "Denuncias" },
      { href: "/admin/credenciales", label: "Credenciales" },
    ],
  },
  {
    titulo: "Financiero",
    rol: "soporte_financiero",
    items: [
      { href: "/admin/pagos", label: "Pagos fallidos" },
      { href: "/admin/precios", label: "Precios regionales" },
    ],
  },
  {
    titulo: "Soporte",
    items: [{ href: "/admin/tickets", label: "Tickets de soporte" }],
  },
  {
    titulo: "Sistema",
    items: [{ href: "/admin/salud", label: "Salud de Infraestructura" }],
  },
];

export default function AdminLayout({ children }: { children: ReactNode }) {
  const [rol, setRol] = useState<RolAdmin | null>(null);

  useEffect(() => {
    api
      .get<{ rol: RolAdmin }>("/api/admin/yo")
      .then((r) => setRol(r.rol))
      .catch(() => setRol(null));
  }, []);

  // El backend sigue validando el rol en cada cola igual: ocultar del menú lo
  // que el rol no puede usar es UX, no seguridad (B10). Sin rol conocido
  // (cargando / no admin) no se muestra nada del menú; cada página igual
  // 403ea con su propio mensaje.
  const grupos = rol ? GRUPOS.filter((g) => !g.rol || g.rol === rol) : [];

  return (
    <>
      <Cabecera />
      <main className="mx-auto max-w-6xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Panel de Administración</h1>
        <p className="mt-1 text-sm text-slate-500">
          Panel para los equipos de moderación, finanzas y soporte de Tinku.
        </p>
        <SettingsShell base="/admin" grupos={grupos}>
          {children}
        </SettingsShell>
      </main>
    </>
  );
}
