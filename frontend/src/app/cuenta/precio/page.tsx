"use client";

import TabPrecio from "@/components/tutor/TabPrecio";
import { Tarjeta } from "@/components/ui";

export default function CuentaPrecioPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Configuración de Precio</h2>
      <Tarjeta className="mt-4 w-full">
        <TabPrecio />
      </Tarjeta>
    </section>
  );
}
