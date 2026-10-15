/**
 * Cabeceras de seguridad. Antes no había ninguna.
 *
 * `Permissions-Policy` habilita cámara y micrófono SOLO para el propio origen
 * (los necesita el Aula Virtual vía LiveKit) y apaga lo que la app no usa.
 * Cuidado al tocar esa línea: sacar `self` de camera/microphone rompe la
 * videollamada entera.
 *
 * CSP (R-G G-8, decisión D-9): una CSP mal escrita rompe en silencio (Next inyecta
 * scripts inline, LiveKit y MercadoPago tienen sus orígenes). Por eso va primero como
 * `Content-Security-Policy-Report-Only`: el navegador avisa en la consola lo que
 * bloquearía, sin bloquear. Después de una semana en producción sin violaciones se pasa
 * a `Content-Security-Policy` (acción del dueño, O-12 del plan de remediación).
 */
const API = process.env.NEXT_PUBLIC_API_URL || "https://api.tinku.site";
const DEV = process.env.NODE_ENV !== "production";
const csp = [
  "default-src 'self'",
  // 'wasm-unsafe-eval': TF.js corre el clasificador del kill-switch en WebAssembly (ADR-M3-07).
  // 'unsafe-eval' solo en desarrollo (recarga en caliente de Next).
  `script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'${DEV ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' data: blob: ${API}`,
  "media-src 'self' blob:",
  "font-src 'self'",
  `connect-src 'self' ${API} wss://*.livekit.cloud https://*.livekit.cloud${DEV ? " ws://localhost:* http://localhost:*" : ""}`,
  "worker-src 'self' blob:",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self' https://*.mercadopago.com https://*.mercadopago.com.ar",
].join("; ");

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
  { key: "Content-Security-Policy-Report-Only", value: csp },
];

/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  async headers() {
    return [
      { source: "/:path*", headers: securityHeaders },
      // Pesos de NSFWJS y .wasm de TF.js (kill-switch, ADR-M3-07): no cambian sin cambiar la ruta.
      {
        source: "/modelos/:path*",
        headers: [{ key: "Cache-Control", value: "public, max-age=31536000, immutable" }],
      },
      // La presentación de la tesis (/presentacion) es pública pero no debe indexarse.
      { source: "/presentacion/:path*", headers: [{ key: "X-Robots-Tag", value: "noindex, nofollow" }] },
    ];
  },
  async rewrites() {
    return [{ source: "/presentacion", destination: "/presentacion/index.html" }];
  },
};

module.exports = nextConfig;
