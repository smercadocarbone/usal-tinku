"use client";

import TicketsSoporte from "@/components/admin/TicketsSoporte";

export default function AdminTicketsPage() {
  return (
    <section>
      <h2 className="text-lg font-semibold text-slate-800">Tickets de soporte</h2>
      <div className="mt-4">
        <TicketsSoporte />
      </div>
    </section>
  );
}
