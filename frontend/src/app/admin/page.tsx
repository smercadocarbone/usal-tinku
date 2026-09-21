"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";

const BREAKPOINT_LG = "(min-width: 1024px)";

/**
 * `/admin` ya no tiene contenido propio — cada sección vive en su propia
 * ruta (`/admin/alertas`, `/admin/denuncias`, etc.). En desktop redirige a
 * la primera sección por defecto (Artículo II: seguridad del menor
 * primero). En mobile no hace falta: `SettingsShell` ya muestra la lista
 * agrupada de secciones en la raíz del shell, sin importar qué reciba acá.
 */
export default function AdminPage() {
  const router = useRouter();

  useEffect(() => {
    if (window.matchMedia(BREAKPOINT_LG).matches) {
      router.replace("/admin/alertas");
    }
  }, [router]);

  return null;
}
