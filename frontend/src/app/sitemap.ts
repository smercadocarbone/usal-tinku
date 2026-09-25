import type { MetadataRoute } from "next";
import { URL_SITIO } from "@/lib/sitio";

/**
 * Solo las rutas realmente públicas.
 *
 * Los perfiles de Tutor NO están acá a propósito: hoy `/tutores/[id]` está
 * detrás del proxy de sesión y es un componente de cliente, así que un
 * crawler nunca ve su contenido. Listarlos sería declararle a Google algo que
 * no puede leer. Esa es exactamente la decisión pendiente: si el SEO de los
 * perfiles importa (es lo que justifica Next.js en la Constitución), hay que
 * sacarlos del matcher y servirlos desde el server; si no importa, se asume y
 * se documenta.
 */
export default function sitemap(): MetadataRoute.Sitemap {
  const ahora = new Date();
  return [
    { url: `${URL_SITIO}/`, lastModified: ahora, changeFrequency: "weekly", priority: 1 },
    { url: `${URL_SITIO}/registro`, lastModified: ahora, changeFrequency: "monthly", priority: 0.8 },
    { url: `${URL_SITIO}/registro/tutor`, lastModified: ahora, changeFrequency: "monthly", priority: 0.8 },
    { url: `${URL_SITIO}/login`, lastModified: ahora, changeFrequency: "yearly", priority: 0.3 },
  ];
}
