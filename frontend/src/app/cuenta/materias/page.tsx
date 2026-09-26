"use client";

import TabMaterias from "@/components/tutor/TabMaterias";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Tarjeta } from "@/components/ui";

export default function CuentaMateriasPage() {
  return (
    <SubpaginaTutor titulo="Materias" descripcion="Qué temas enseñás. Contalo con tus palabras o elegilos del catálogo; se guardan solos.">
      <Tarjeta>
        <TabMaterias />
      </Tarjeta>
    </SubpaginaTutor>
  );
}
