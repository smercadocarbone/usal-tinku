"use client";

import TabMaterias from "@/components/tutor/TabMaterias";
import { Tarjeta } from "@/components/ui";

export default function CuentaMateriasPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Mis Materias</h2>
      <Tarjeta className="mt-4 w-full">
        <TabMaterias />
      </Tarjeta>
    </section>
  );
}
