"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Inbox } from "lucide-react";
import { api, mensajeDeError } from "@/lib/api";
import type { Solicitud } from "@/lib/reservas";
import { duracionLegible, fechaHoraCorta } from "@/lib/formatos";
import { nombreCorto } from "@/lib/tutores";
import { Avatar, Boton, EstadoVacio, SkeletonLista, Tarjeta, useToast } from "@/components/ui";

/** Pedidos de clases de los hijos (US-3): el Adulto Responsable los aprueba y paga. */
export default function Pedidos({ pedidos }: { pedidos: Solicitud[] | null }) {
  const router = useRouter();
  const toast = useToast();
  const [aprobando, setAprobando] = useState<string | null>(null);

  async function aprobar(s: Solicitud) {
    setAprobando(s.id);
    try {
      const reserva = await api.post<{ id: string }>(`/api/solicitudes/${s.id}/aprobar`);
      router.push(`/pagar?reserva=${reserva.id}`);
    } catch (err) {
      toast.mostrar(mensajeDeError(err, "No pudimos aprobar el pedido."), { tono: "error" });
      setAprobando(null);
    }
  }

  if (pedidos === null) return <SkeletonLista filas={2} etiqueta="Cargando pedidos…" />;
  if (pedidos.length === 0) {
    return (
      <EstadoVacio icono={<Inbox />} titulo="No hay pedidos">
        Cuando tu hijo o hija te pida una clase, la vas a ver acá para aprobarla.
      </EstadoVacio>
    );
  }
  return (
    <ul className="flex list-none flex-col gap-3 p-0">
      {pedidos.map((s) => (
        <li key={s.id}>
          <Tarjeta className="flex flex-col gap-4 p-4 sm:flex-row sm:items-center sm:p-5">
            <div className="flex flex-1 items-center gap-4">
              <Avatar nombre={s.menorNombre ?? "?"} semilla={s.menorId ?? s.id} />
              <div>
                <p className="font-bold">
                  {s.menorNombre ?? "Tu hijo/a"} quiere una clase{s.tutorNombre ? ` con ${nombreCorto(s.tutorNombre, s.tutorApellido)}` : ""}
                </p>
                <p className="text-sm text-tinta-suave">
                  <span className="capitalize">{fechaHoraCorta(s.horarioPropuesto)}</span>
                  {s.duracionMinutos ? ` · ${duracionLegible(s.duracionMinutos)}` : ""}
                </p>
              </div>
            </div>
            <Boton cargando={aprobando === s.id} textoCargando="Aprobando…" onClick={() => void aprobar(s)}>
              Aprobar y pagar
            </Boton>
          </Tarjeta>
        </li>
      ))}
    </ul>
  );
}
