"use client";

import ColaPagosFallidos from "@/components/admin/ColaPagosFallidos";
import ColaReembolsosAdicional from "@/components/admin/ColaReembolsosAdicional";

export default function AdminPagosPage() {
  return (
    <div className="flex flex-col gap-10">
      <section>
        <h2 className="text-2xl font-bold">Pagos fallidos</h2>
        <div className="mt-4">
          <ColaPagosFallidos />
        </div>
      </section>
      <section>
        <h2 className="text-2xl font-bold">Devoluciones del resumen</h2>
        <p className="mt-1 text-sm text-tinta-suave">
          El resumen automático falló y no pudimos devolver el adicional sola: reintentalo o registrá que lo devolviste.
        </p>
        <div className="mt-4">
          <ColaReembolsosAdicional />
        </div>
      </section>
    </div>
  );
}
