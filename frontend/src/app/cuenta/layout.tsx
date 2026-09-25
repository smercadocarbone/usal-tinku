"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname } from "next/navigation";
import { getAlertasMias, getDenunciasRecibidas } from "@/lib/api";
import { useSesion } from "@/lib/useSesion";
import AppShell from "@/components/shell/AppShell";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";

/** Rutas de "ajustes" de la cuenta: van con el menú lateral. El resto son destinos
 *  principales (Mis clases, Mis chicos, Mi agenda…) y ocupan la pantalla entera. */
const RUTAS_AJUSTES = ["/cuenta", "/cuenta/acceso", "/cuenta/seguridad"];

export default function CuentaLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const sesion = useSesion();
  const [tieneCasos, setTieneCasos] = useState(false);

  // "Casos y reportes" solo aparece si hay alguno (UX-05 §1): a quien no tiene
  // ninguno, "Denuncias y alertas" en el menú le suena alarmante.
  useEffect(() => {
    if (!sesion || sesion.payload.tipo === "MENOR") return;
    let vivo = true;
    Promise.all([getDenunciasRecibidas().catch(() => []), getAlertasMias().catch(() => [])]).then(([d, a]) => {
      if (vivo) setTieneCasos(d.length + a.length > 0);
    });
    return () => {
      vivo = false;
    };
  }, [sesion]);

  if (!RUTAS_AJUSTES.includes(pathname)) {
    return <AppShell>{children}</AppShell>;
  }

  const payload = sesion?.payload;
  const tutorQueAprende = payload?.tipo === "TUTOR" && (payload.cap_est === true || payload.cap_ar === true);
  const grupos: GrupoNavAjustes[] = [
    {
      titulo: "Tu cuenta",
      items: [
        { href: "/cuenta", label: "Perfil" },
        { href: "/cuenta/acceso", label: "Seguridad y acceso" },
        ...(tieneCasos || pathname === "/cuenta/seguridad" ? [{ href: "/cuenta/seguridad", label: "Casos y reportes" }] : []),
      ],
    },
    // ADR-M1-07: un Tutor que también aprende o tiene chicos a cargo llega desde acá.
    ...(tutorQueAprende
      ? [
          {
            titulo: "Para aprender",
            items: [
              ...(payload?.cap_est === true || payload?.cap_ar === true ? [{ href: "/buscar", label: "Buscar tutores" }] : []),
              ...(payload?.cap_ar === true ? [{ href: "/cuenta/menores", label: "Mis chicos" }] : []),
            ],
          },
        ]
      : []),
  ];

  return (
    <AppShell>
      <h1 className="text-[28px] font-extrabold sm:text-[40px]">Mi cuenta</h1>
      <SettingsShell base="/cuenta" grupos={grupos}>
        {children}
      </SettingsShell>
    </AppShell>
  );
}
