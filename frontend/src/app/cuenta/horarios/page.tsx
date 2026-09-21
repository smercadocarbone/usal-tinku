"use client";

import { getSession } from "@/lib/auth";
import TabHorarios from "@/components/tutor/TabHorarios";
import { Tarjeta } from "@/components/ui";

export default function CuentaHorariosPage() {
  const session = getSession();
  const tutorId = String(session?.payload.sub ?? "");

  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Mis Horarios</h2>
      <Tarjeta className="mt-4 w-full">
        <TabHorarios tutorId={tutorId} />
      </Tarjeta>
    </section>
  );
}
