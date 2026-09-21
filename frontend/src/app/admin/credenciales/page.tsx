"use client";

import ColaCredenciales from "@/components/admin/ColaCredenciales";

export default function AdminCredencialesPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Credenciales</h2>
      <div className="mt-4">
        <ColaCredenciales />
      </div>
    </section>
  );
}
