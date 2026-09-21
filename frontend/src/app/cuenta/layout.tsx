"use client";

import type { ReactNode } from "react";
import { getSession } from "@/lib/auth";
import Cabecera from "@/components/Cabecera";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";

export default function CuentaLayout({ children }: { children: ReactNode }) {
  const session = getSession();
  const payload = session?.payload;

  const grupos: GrupoNavAjustes[] = [
    {
      titulo: "Cuenta",
      items: [
        { href: "/cuenta", label: "Perfil" },
        { href: "/cuenta/acceso", label: "Acceso" },
      ],
    },
  ];

  if (payload?.tipo === "TUTOR") {
    grupos.push({
      titulo: "Tutor",
      items: [
        { href: "/cuenta/horarios", label: "Mis Horarios" },
        { href: "/cuenta/materias", label: "Mis Materias" },
        { href: "/cuenta/precio", label: "Configuración de Precio" },
      ],
    });
  }

  if (payload?.cap_ar === true) {
    grupos.push({
      titulo: "Adulto responsable",
      items: [{ href: "/cuenta/menores", label: "Menores a cargo" }],
    });
  }

  grupos.push({
    titulo: "General",
    items: [
      { href: "/cuenta/reservas", label: "Mis reservas" },
      { href: "/cuenta/seguridad", label: "Denuncias y alertas" },
    ],
  });

  return (
    <>
      <Cabecera enlaces={[{ href: "/buscar", label: "Buscar tutores" }]} />
      <main className="mx-auto max-w-5xl px-5 py-8">
        <h1 className="text-xl tracking-tight text-slate-800">Mi cuenta</h1>
        <SettingsShell base="/cuenta" grupos={grupos}>
          {children}
        </SettingsShell>
      </main>
    </>
  );
}
