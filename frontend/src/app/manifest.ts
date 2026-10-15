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
    background_color: "#f6f8f7",
    theme_color: "#146251",
    lang: "es-AR",
    // R-G G-10 (T7-7, D-8): sin forzar orientación (el aula se usa también apaisada).
    orientation: "any",
    icons: [
      {
        src: "/icon.svg",
        type: "image/svg+xml",
        sizes: "any",
        purpose: "any",
      },
      { src: "/iconos/icon-192.png", type: "image/png", sizes: "192x192", purpose: "any" },
      { src: "/iconos/icon-512.png", type: "image/png", sizes: "512x512", purpose: "any" },
      { src: "/iconos/icon-maskable-512.png", type: "image/png", sizes: "512x512", purpose: "maskable" },
    ],
  };
}
