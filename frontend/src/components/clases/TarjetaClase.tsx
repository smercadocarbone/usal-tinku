"use client";

import Link from "next/link";
import { ChevronRight, Clock } from "lucide-react";
import { duracionLegible, fechaHoraCorta } from "@/lib/formatos";
import { nombreCorto } from "@/lib/tutores";
import type { Reserva } from "@/lib/reservas";
import { useAhora } from "@/lib/useAhora";
import { Avatar, EstadoReserva, Tarjeta, clasesBoton, enlaceTarjeta } from "@/components/ui";

export interface TarjetaClaseProps {
  reserva: Reserva;
  /** Quién mira: cambia a quién se nombra ("con Jorge" / "con Sofía"). */
  vista: "alumno" | "tutor";
  miId?: string;
}

/**
 * Una clase en la lista (UX-05 §3): con quién, cuándo, cuánto dura, su estado y
 * LA acción que corresponde al estado (pagar, entrar). Toda la tarjeta lleva al
 * detalle; la acción va por encima del enlace.
 */
export default function TarjetaClase({ reserva: r, vista }: TarjetaClaseProps) {
  const ahora = useAhora();
  const otroNombre = vista === "tutor" ? r.beneficiarioNombre : r.tutorNombre;
  const otroApellido = vista === "tutor" ? r.beneficiarioApellido : r.tutorApellido;
  const esDeOtro = vista === "alumno" && r.beneficiarioId && r.beneficiarioId !== r.pagadorId && r.beneficiarioNombre;
  const titulo = otroNombre
    ? esDeOtro
      ? `Clase de ${r.beneficiarioNombre} con ${nombreCorto(r.tutorNombre ?? "", r.tutorApellido)}`
      : `Clase con ${nombreCorto(otroNombre, otroApellido)}`
    : "Clase";

  const inicio = new Date(r.horario).getTime();
  const minutosParaEmpezar = ahora ? (inicio - ahora) / 60000 : Infinity;
  const entrarPronto = (r.estado === "confirmada" && minutosParaEmpezar <= 15) || r.estado === "en_curso";
  const vencePago = r.pagoVenceAt && ahora ? Math.max(0, Math.ceil((new Date(r.pagoVenceAt).getTime() - ahora) / 60000)) : null;

  return (
    <Tarjeta as="article" variante="interactiva" className="flex items-center gap-4 p-4 sm:p-5">
      <Avatar nombre={otroNombre ?? "?"} apellido={otroApellido ?? undefined} semilla={vista === "tutor" ? (r.beneficiarioId ?? r.id) : r.tutorId} />
      <div className="min-w-0 flex-1">
        <h3 className="line-clamp-2 text-[16px] font-bold">
          <Link href={`/cuenta/reservas/${r.id}`} className={enlaceTarjeta}>
            {titulo}
          </Link>
        </h3>
        <p className="mt-0.5 text-sm capitalize text-tinta-suave">
          <time dateTime={r.horario} suppressHydrationWarning>
            {fechaHoraCorta(r.horario)}
          </time>
          {r.duracionMinutos ? <span className="normal-case"> · {duracionLegible(r.duracionMinutos)}</span> : null}
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <EstadoReserva estado={r.estado} />
          {r.puedePagar && vencePago !== null && (
            <span className="inline-flex items-center gap-1 text-[13px] font-semibold text-aviso">
              <Clock className="size-3.5" aria-hidden /> {vencePago} min para pagar
            </span>
          )}
        </div>
      </div>
      <div className="relative z-10 shrink-0">
        {r.puedePagar ? (
          <Link href={`/pagar?reserva=${r.id}`} className={clasesBoton("primario", "sm")}>
            Pagar
          </Link>
        ) : entrarPronto ? (
          <Link href={`/cuenta/reservas/${r.id}`} className={clasesBoton("primario", "sm")}>
            Entrar al aula
          </Link>
        ) : (
          <ChevronRight className="size-5 text-tinta-tenue" aria-hidden />
        )}
      </div>
    </Tarjeta>
  );
}
