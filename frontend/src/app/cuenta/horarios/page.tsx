"use client";

import { useIdPropio } from "@/lib/useIdPropio";
import TabHorarios from "@/components/tutor/TabHorarios";
import ChecklistTutor from "@/components/tutor/ChecklistTutor";
import { Skeleton, Tarjeta } from "@/components/ui";

/** "Mi agenda": la home del tutor (UX-06 §1–2): qué le falta y su disponibilidad. */
export default function CuentaHorariosPage() {
  // B4: el id del Tutor sale de GET /api/usuarios/me, no del `sub` del JWT (DNI).
  const tutorId = useIdPropio();

  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-6">
      <h1 className="text-[28px] font-extrabold sm:text-[40px]">Mi agenda</h1>
      <ChecklistTutor tutorId={tutorId} compacto />
      <Tarjeta>
        {tutorId === null ? (
          <div role="status">
            <span className="sr-only">Cargando tu agenda…</span>
            <Skeleton className="h-64 w-full rounded-2xl" />
          </div>
        ) : (
          <TabHorarios tutorId={tutorId} />
        )}
      </Tarjeta>
    </div>
  );
}
