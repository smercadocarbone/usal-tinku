"use client";

import ColaDenuncias from "@/components/admin/ColaDenuncias";

export default function AdminDenunciasPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Denuncias</h2>
      <div className="mt-4">
        <ColaDenuncias />
      </div>
    </section>
  );
}
