/**
 * Cabeceras de seguridad. Antes no había ninguna.
 *
 * `Permissions-Policy` habilita cámara y micrófono SOLO para el propio origen
 * (los necesita el Aula Virtual vía LiveKit) y apaga lo que la app no usa.
 * Cuidado al tocar esa línea: sacar `self` de camera/microphone rompe la
 * videollamada entera.
 *
 * Falta una `Content-Security-Policy`, a propósito: una CSP mal escrita rompe
 * en silencio (Next inyecta scripts inline que necesitan nonce, y hay que
 * enumerar los orígenes de LiveKit y MercadoPago). Se agrega cuando se pueda
 * verificar contra el backend y LiveKit reales, no a ciegas.
 */
const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  {
    key: "Strict-Transport-Security",
    value: "max-age=63072000; includeSubDomains; preload",
  },
  {
    key: "Permissions-Policy",
    value: "camera=(self), microphone=(self), geolocation=(), browsing-topics=()",
  },
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
};

module.exports = nextConfig;
