import type { Metadata, Viewport } from "next";
import { URL_SITIO } from "@/lib/sitio";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL(URL_SITIO),
  // `template` deja que cada pantalla ponga lo suyo sin repetir la marca.
  title: {
    default: "Tinku — Tutorías en línea",
    template: "%s · Tinku",
  },
  description:
    "Clases particulares en línea con Tutores verificados, pagos protegidos y aulas seguras para menores.",
  applicationName: "Tinku",
  openGraph: {
    type: "website",
    locale: "es_AR",
    siteName: "Tinku",
    title: "Tinku — Tutorías en línea",
    description:
      "Clases particulares en línea con Tutores verificados, pagos protegidos y aulas seguras para menores.",
  },
  appleWebApp: {
    capable: true,
    title: "Tinku",
    statusBarStyle: "default",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#0d9488",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="es-AR">
      <body>
        {/* Salto al contenido: primer tabulable de la página, visible solo al
            enfocarlo con teclado. Sin esto, quien navega con teclado tiene que
            recorrer toda la cabecera en cada pantalla. */}
        <a
          href="#contenido"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-teal-700 focus:px-4 focus:py-2.5 focus:font-semibold focus:text-white"
        >
          Saltar al contenido
        </a>
        <div id="contenido">{children}</div>
      </body>
    </html>
  );
}
