"use client";

import TabMaterias from "@/components/tutor/TabMaterias";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Tarjeta } from "@/components/ui";

export default function CuentaMateriasPage() {
  return (
    <SubpaginaTutor titulo="Mis materias" descripcion="Elegí qué temas enseñás. Se guardan solos.">
      <Tarjeta>
        <TabMaterias />
      </Tarjeta>
    </SubpaginaTutor>
  );
}
