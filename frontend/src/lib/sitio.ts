/**
 * URL pública del sitio, para metadata absoluta (OpenGraph, sitemap, robots).
 * En producción se fija con `NEXT_PUBLIC_SITE_URL`; el fallback es solo para
 * desarrollo — si queda el de localhost en producción, los previews de
 * WhatsApp y el sitemap apuntan a ninguna parte.
 */
export const URL_SITIO =
  process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";
