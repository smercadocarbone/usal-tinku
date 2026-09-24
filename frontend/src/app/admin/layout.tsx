"use client";

import type { ReactNode } from "react";
import Cabecera from "@/components/Cabecera";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";

// Orden por el Artículo II de la Constitución (seguridad del menor primero),
// no por módulo ni alfabético: Alertas de kill-switch > Denuncias >
// Credenciales (bloquea a un Tutor de dar clases) > lo financiero > soporte
// general > infraestructura.
const GRUPOS: GrupoNavAjustes[] = [
  {
    titulo: "Seguridad",
    items: [
      { href: "/admin/alertas", label: "Alertas de Seguridad" },
      { href: "/admin/denuncias", label: "Denuncias" },
      { href: "/admin/credenciales", label: "Credenciales" },
    ],
  },
  {
    titulo: "Financiero",
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
  return (
    <>
      <Cabecera enlaces={[{ href: "/cuenta", label: "Mi cuenta" }]} />
      <main className="mx-auto max-w-6xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Panel de Administración</h1>
        <p className="mt-1 text-sm text-slate-500">
          Panel para los equipos de moderación, finanzas y soporte de Tinku.
        </p>
        <SettingsShell base="/admin" grupos={GRUPOS}>
          {children}
        </SettingsShell>
      </main>
    </>
  );
}
