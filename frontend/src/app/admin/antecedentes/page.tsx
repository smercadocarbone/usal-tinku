"use client";

import ColaCap from "@/components/admin/ColaCap";

export default function AdminAntecedentesPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Antecedentes penales (CAP)</h2>
      <p className="mt-1 text-sm text-slate-500">Solo para tutores que quieren dar clases a menores.</p>
      <div className="mt-4">
        <ColaCap />
      </div>
    </section>
  );
}
