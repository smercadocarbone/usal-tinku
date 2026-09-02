/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  // TODO: agregar soporte PWA (service worker) una vez que el flujo de
  // autenticación esté probado — ver Cap. 1.11 del documento original
  // ("procesamiento local, sin costo de API" del matching) y la nota de
  // la Constitución sobre video con adaptación automática de calidad
  // (no depende del service worker, es responsabilidad de LiveKit).
};

module.exports = nextConfig;
