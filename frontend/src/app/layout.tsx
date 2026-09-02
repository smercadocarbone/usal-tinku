import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Tinku",
  description: "Tutorías en línea — Tinku",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="es-AR">
      <body>{children}</body>
    </html>
  );
}
