"use client";

import ColaCredenciales from "@/components/admin/ColaCredenciales";

export default function AdminCredencialesPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Credenciales</h2>
      <div className="mt-4">
        <ColaCredenciales />
      </div>
    </section>
  );
}
