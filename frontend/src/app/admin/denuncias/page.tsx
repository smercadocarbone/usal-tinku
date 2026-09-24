"use client";

import ColaDenuncias from "@/components/admin/ColaDenuncias";

export default function AdminDenunciasPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Denuncias</h2>
      <div className="mt-4">
        <ColaDenuncias />
      </div>
    </section>
  );
}
