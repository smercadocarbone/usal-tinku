import Link from "next/link";
import type { ReactNode } from "react";
import { ChevronLeft } from "lucide-react";

/** Encabezado común de las subpantallas de "Mi perfil" del tutor (materias, precio). */
export default function SubpaginaTutor({ titulo, descripcion, children }: { titulo: string; descripcion?: string; children: ReactNode }) {
  return (
    <div className="mx-auto max-w-3xl">
      <Link
        href="/cuenta/perfil-tutor"
        className="-ml-2 mb-4 inline-flex min-h-11 items-center gap-1 rounded-control px-2 text-[15px] font-semibold text-tinta no-underline hover:bg-superficie-hundida"
      >
        <ChevronLeft className="size-5" aria-hidden /> Mi perfil
      </Link>
      <h1 className="text-[28px] font-extrabold sm:text-[40px]">{titulo}</h1>
      {descripcion && <p className="mt-1 text-[15px] text-tinta-suave">{descripcion}</p>}
      <div className="mt-6">{children}</div>
    </div>
  );
}
