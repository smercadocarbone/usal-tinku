"use client";

import PreciosRegionales from "@/components/admin/PreciosRegionales";

export default function AdminPreciosPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Precios regionales</h2>
      <div className="mt-4">
        <PreciosRegionales />
      </div>
    </section>
  );
}
