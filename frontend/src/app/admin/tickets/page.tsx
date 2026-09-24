"use client";

import TicketsSoporte from "@/components/admin/TicketsSoporte";

export default function AdminTicketsPage() {
  return (
    <section>
      <h2 className="text-2xl font-bold">Tickets de soporte</h2>
      <div className="mt-4">
        <TicketsSoporte />
      </div>
    </section>
  );
}
