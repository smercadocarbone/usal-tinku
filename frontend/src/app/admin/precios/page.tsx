"use client";

import PreciosRegionales from "@/components/admin/PreciosRegionales";

export default function AdminPreciosPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Precios regionales</h2>
      <div className="mt-4">
        <PreciosRegionales />
      </div>
    </section>
  );
}
