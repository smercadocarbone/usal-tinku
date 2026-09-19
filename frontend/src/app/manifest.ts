import type { MetadataRoute } from "next";

/**
 * Manifest de la PWA. Hasta acá el proyecto se describía como PWA sin tener
 * ni manifest ni íconos (el `next.config.js` lo admitía en un TODO).
 *
 * Esto habilita la instalación y la marca en la pantalla de inicio. NO incluye
 * service worker: cachear una app con sesión, pagos y videollamada necesita
 * una estrategia pensada (qué se cachea, qué nunca, cómo se invalida al
 * cerrar sesión), y meter uno genérico acá sería peor que no tenerlo.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Tinku — Tutorías en línea",
    short_name: "Tinku",
    description:
      "Clases particulares en línea con Tutores verificados, pagos protegidos y aulas seguras.",
    start_url: "/",
    display: "standalone",
    background_color: "#f8fafc",
    theme_color: "#0d9488",
    lang: "es-AR",
    orientation: "portrait",
    icons: [
      {
        src: "/icon.svg",
        type: "image/svg+xml",
        sizes: "any",
        purpose: "any",
      },
    ],
  };
}
