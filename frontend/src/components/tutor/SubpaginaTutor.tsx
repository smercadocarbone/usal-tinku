import type { ReactNode } from "react";

/** Encabezado de las secciones "Como tutor" de Mi cuenta (presentación, materias, precio…). */
export default function SubpaginaTutor({ titulo, descripcion, accion, children }: {
  titulo: string;
  descripcion?: ReactNode;
  accion?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="max-w-3xl">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold">{titulo}</h2>
          {descripcion && <p className="mt-1 text-[15px] text-tinta-suave">{descripcion}</p>}
        </div>
        {accion}
      </div>
      <div className="mt-6">{children}</div>
    </div>
  );
}
