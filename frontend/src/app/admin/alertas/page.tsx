"use client";

import ColaAlertas from "@/components/admin/ColaAlertas";

export default function AdminAlertasPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Alertas de Seguridad</h2>
      <div className="mt-4">
        <ColaAlertas />
      </div>
    </section>
  );
}
