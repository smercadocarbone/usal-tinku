"use client";

import ColaPagosFallidos from "@/components/admin/ColaPagosFallidos";

export default function AdminPagosPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Pagos fallidos</h2>
      <div className="mt-4">
        <ColaPagosFallidos />
      </div>
    </section>
  );
}
