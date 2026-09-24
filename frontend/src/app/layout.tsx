import type { Metadata, Viewport } from "next";
import localFont from "next/font/local";
import { URL_SITIO } from "@/lib/sitio";
import { ProveedorToast } from "@/components/ui/Toast";
import "./globals.css";

// Plus Jakarta Sans (OFL, ver `fonts/OFL-PlusJakartaSans.txt`) servida desde el
// propio dominio: no hay requests a Google en runtime ni en el build (el build de
// la imagen Docker funciona sin red). Variable 200–800, subset latin (ñ y tildes).
const jakarta = localFont({
  src: "./fonts/PlusJakartaSans-latin-variable.woff2",
  weight: "200 800",
  display: "swap",
  variable: "--font-jakarta",
});

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
  themeColor: "#146251",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="es-AR" className={jakarta.variable}>
      <body>
        {/* Salto al contenido: primer tabulable de la página, visible solo al
            enfocarlo con teclado. Sin esto, quien navega con teclado tiene que
            recorrer toda la cabecera en cada pantalla. */}
        <a
          href="#contenido"
          className="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-control focus:bg-marca-700 focus:px-4 focus:py-2.5 focus:font-semibold focus:text-white"
        >
          Saltar al contenido
        </a>
        <ProveedorToast>
          <div id="contenido">{children}</div>
        </ProveedorToast>
      </body>
    </html>
  );
}
