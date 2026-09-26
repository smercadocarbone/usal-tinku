"use client";

import TabPrecio from "@/components/tutor/TabPrecio";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Tarjeta } from "@/components/ui";

export default function CuentaPrecioPage() {
  return (
    <SubpaginaTutor titulo="Precio" descripcion="Cuánto cobrás por hora.">
      <Tarjeta>
        <TabPrecio />
      </Tarjeta>
    </SubpaginaTutor>
  );
}
