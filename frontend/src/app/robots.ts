import type { MetadataRoute } from "next";
import { URL_SITIO } from "@/lib/sitio";

/**
 * Todo lo que está detrás de `middleware.ts` se bloquea explícitamente: un
 * crawler ahí solo recibe un 307 a /login, y dejarlo indexable solo genera
 * ruido y filtra la existencia de rutas privadas.
 */
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/cuenta", "/admin", "/aula", "/pagar", "/reservar", "/buscar", "/tutores"],
    },
    sitemap: `${URL_SITIO}/sitemap.xml`,
  };
}
