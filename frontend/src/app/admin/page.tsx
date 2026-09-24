"use client";

import Link from "next/link";
import { AlertOctagon, ChevronRight, FileCheck2, Flag, LifeBuoy, WalletCards, type LucideIcon } from "lucide-react";
import { tiempoRelativo } from "@/lib/formatos";
import { useAdmin, type Cola } from "@/components/admin/ContextoAdmin";
import { useAhora } from "@/lib/useAhora";
import { cn } from "@/lib/cn";
import { Skeleton } from "@/components/ui";

const COLAS: Record<Cola["clave"], { titulo: string; href: string; icono: LucideIcon; vacio: string }> = {
  alertas: { titulo: "Alertas de seguridad", href: "/admin/alertas", icono: AlertOctagon, vacio: "Sin alertas por revisar" },
  denuncias: { titulo: "Denuncias", href: "/admin/denuncias", icono: Flag, vacio: "Sin denuncias en revisión" },
  credenciales: { titulo: "Credenciales", href: "/admin/credenciales", icono: FileCheck2, vacio: "Sin credenciales pendientes" },
  pagos: { titulo: "Pagos fallidos", href: "/admin/pagos", icono: WalletCards, vacio: "Sin pagos fallidos" },
  tickets: { titulo: "Tickets de soporte", href: "/admin/tickets", icono: LifeBuoy, vacio: "Sin tickets abiertos" },
};

const ORDEN: Cola["clave"][] = ["alertas", "denuncias", "credenciales", "pagos", "tickets"];

/** `/admin` = tablero (UX-08 §1): por cada cola del rol, cuántas hay y el plazo más próximo. */
export default function AdminPage() {
  const { rol, colas, cargando } = useAdmin();
  const ahora = useAhora();

  if (!rol && !cargando) {
    return <p className="text-tinta-suave">No tenés un rol de administración asignado.</p>;
  }

  const presentes = ORDEN.filter((c) => colas[c]);

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-2xl font-bold">Resumen</h2>
      {cargando && presentes.length === 0 ? (
        <div role="status" className="grid gap-3 sm:grid-cols-2">
          <span className="sr-only">Cargando colas…</span>
          <Skeleton className="h-32 rounded-tarjeta" />
          <Skeleton className="h-32 rounded-tarjeta" />
        </div>
      ) : (
        <ul className="grid list-none grid-cols-1 gap-3 p-0 sm:grid-cols-2">
          {presentes.map((clave) => {
            const c = colas[clave]!;
            const m = COLAS[clave];
            return (
              <li key={clave}>
                <Link
                  href={m.href}
                  className={cn(
                    "flex h-full flex-col gap-3 rounded-tarjeta border bg-superficie p-5 text-tinta no-underline transition-shadow hover:shadow-elevado",
                    c.urgente ? "border-peligro/40" : "border-borde"
                  )}
                >
                  <span className="flex items-center justify-between">
                    <span className="flex items-center gap-2 font-bold">
                      <m.icono className={cn("size-5", c.urgente ? "text-peligro" : "text-marca-700")} aria-hidden />
                      {m.titulo}
                    </span>
                    <ChevronRight className="size-5 text-tinta-tenue" aria-hidden />
                  </span>
                  <span className="tabular text-4xl font-extrabold">{c.cantidad}</span>
                  <span className={cn("text-sm", c.urgente ? "font-semibold text-peligro" : "text-tinta-suave")}>
                    {c.cantidad === 0
                      ? m.vacio
                      : c.proximoVence && ahora
                        ? `El plazo más próximo vence ${tiempoRelativo(c.proximoVence, new Date(ahora))}`
                        : "Pendientes"}
                  </span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
