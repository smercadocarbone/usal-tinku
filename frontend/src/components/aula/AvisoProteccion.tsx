"use client";

import Link from "next/link";

export type MotivoAvisoProteccion = "no-disponible" | "seguridad" | "sin-proteccion";

const TEXTOS: Record<MotivoAvisoProteccion, { titulo: string; texto: string; volver: boolean }> = {
  "no-disponible": {
    titulo: "No pudimos activar la protección del aula",
    texto:
      "No pudimos activar la protección del aula en este dispositivo. Para una clase con un menor tiene que estar activa. Probá con otro navegador (Chrome actualizado) o con otro dispositivo.",
    volver: true,
  },
  seguridad: {
    titulo: "La clase se cortó por seguridad",
    texto: "La clase se cortó por seguridad. El equipo de Tinku lo está revisando.",
    volver: true,
  },
  "sin-proteccion": {
    titulo: "Se cortó la clase",
    texto:
      "Se cortó la clase porque no se pudo reactivar la protección del aula. Si vuelve a pasar, probá con otro navegador o dispositivo.",
    volver: true,
  },
};

/**
 * Pantalla de bloqueo a pantalla completa, sin botón para volver a entrar a la sala. No dice qué se
 * detectó ni a quién (Artículo II, BR-KS-03).
 */
export default function AvisoProteccion({ motivo }: { motivo: MotivoAvisoProteccion }) {
  const t = TEXTOS[motivo];
  return (
    <main
      role="alert"
      aria-live="assertive"
      className="fixed inset-0 z-50 flex flex-col items-center justify-center bg-gray-900 px-6 text-center text-gray-50"
    >
      <h1 className="mb-3 max-w-md text-2xl font-extrabold">{t.titulo}</h1>
      <p className="max-w-md text-base text-gray-300">{t.texto}</p>
      {t.volver && (
        <Link
          href="/cuenta/reservas"
          className="mt-8 inline-flex min-h-12 items-center rounded-control bg-marca-600 px-6 font-bold text-white hover:bg-marca-500"
        >
          Volver a mis clases
        </Link>
      )}
    </main>
  );
}
