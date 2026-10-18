"use client";

import { useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { rutaCuentaPermitida, rutaCuentaRestringida } from "@/lib/navegacion";
import { getAlertasMias, getDenunciasRecibidas } from "@/lib/api";
import { useSesion } from "@/lib/useSesion";
import AppShell from "@/components/shell/AppShell";
import SettingsShell, { type GrupoNavAjustes } from "@/components/settings/SettingsShell";
import EncuestaNps from "@/components/evaluacion/EncuestaNps";

/** Rutas de "ajustes" de la cuenta: van con el menú lateral. El resto son destinos
 *  principales (Mis clases, Mis chicos, Mi agenda…) y ocupan la pantalla entera. */
const RUTAS_AJUSTES = [
  "/cuenta",
  "/cuenta/perfil",
  "/cuenta/acceso",
  "/cuenta/seguridad",
  // Tutor: todo lo de su perfil vive en Mi cuenta (antes "Mi perfil", aparte).
  "/cuenta/presentacion",
  "/cuenta/materias",
  "/cuenta/precio",
  "/cuenta/credenciales",
  "/cuenta/clases-con-menores",
];

export default function CuentaLayout({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const sesion = useSesion();
  const [tieneCasos, setTieneCasos] = useState(false);
  const permitida = rutaCuentaPermitida(pathname, sesion?.payload);

  // T3-03 / T7-M3: una pantalla de otro perfil se reemplaza por el inicio de "Mi cuenta".
  useEffect(() => {
    if (sesion && !permitida) router.replace("/cuenta");
  }, [sesion, permitida, router]);

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

  // Una pantalla que depende del perfil no se monta hasta saber quién es: si se montara antes,
  // pediría datos de otro rol (p. ej. estado-perfil del Tutor) y recién después redirigiría.
  if ((sesion && !permitida) || (!sesion && rutaCuentaRestringida(pathname))) return <AppShell>{null}</AppShell>;

  // T-TES-11b (I-04): NPS in-app; decide el backend y nunca a un Menor.
  const encuesta = sesion ? <EncuestaNps esMenor={sesion.payload.tipo === "MENOR"} /> : null;

  if (!RUTAS_AJUSTES.includes(pathname)) {
    return (
      <AppShell>
        {children}
        {encuesta}
      </AppShell>
    );
  }

  const payload = sesion?.payload;
  const tutorQueAprende = payload?.tipo === "TUTOR" && (payload.cap_est === true || payload.cap_ar === true);
  const grupos: GrupoNavAjustes[] = [
    {
      titulo: "Tu cuenta",
      items: [
        // En el celular la raíz es esta lista: "Perfil" abre su propia ruta (antes volvía a la lista).
        { href: "/cuenta/perfil", label: "Perfil", activoEn: ["/cuenta"] },
        { href: "/cuenta/acceso", label: "Seguridad y acceso" },
        ...(tieneCasos || pathname === "/cuenta/seguridad" ? [{ href: "/cuenta/seguridad", label: "Casos y reportes" }] : []),
      ],
    },
    ...(payload?.tipo === "TUTOR"
      ? [
          {
            titulo: "Como tutor",
            items: [
              { href: "/cuenta/presentacion", label: "Presentación" },
              { href: "/cuenta/materias", label: "Materias" },
              { href: "/cuenta/precio", label: "Precio" },
              { href: "/cuenta/credenciales", label: "Credenciales" },
              { href: "/cuenta/clases-con-menores", label: "Clases con menores" },
            ],
          },
        ]
      : []),
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
      {encuesta}
    </AppShell>
  );
}
