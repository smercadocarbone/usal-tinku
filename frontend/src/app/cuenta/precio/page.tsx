"use client";

import TabPrecio from "@/components/tutor/TabPrecio";
import SubpaginaTutor from "@/components/tutor/SubpaginaTutor";
import { Tarjeta } from "@/components/ui";

export default function CuentaPrecioPage() {
  return (
    <SubpaginaTutor titulo="Mi precio" descripcion="Cuánto cobrás por clase.">
      <Tarjeta>
        <TabPrecio />
      </Tarjeta>
    </SubpaginaTutor>
  );
}
