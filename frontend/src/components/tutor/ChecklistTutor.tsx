"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Check, ChevronRight, Eye } from "lucide-react";
import { api, getEstadoPerfilTutor, type EstadoPerfilTutor } from "@/lib/api";
import type { Franja } from "@/lib/agenda";
import { cn } from "@/lib/cn";
import { Skeleton, Tarjeta } from "@/components/ui";

interface Item {
  titulo: string;
  detalle?: string;
  hecho: boolean;
  href: string;
  opcional?: boolean;
}

function items(e: EstadoPerfilTutor, tieneHorarios: boolean): Item[] {
  const credencial: Item =
    e.tieneCredencialAprobada && e.ultimaCredencial === "PENDIENTE"
      ? { titulo: "Credencial académica", detalle: "Tu perfil está verificado. Tu nueva credencial está en revisión.", hecho: true, href: "/cuenta/perfil-tutor#credencial" }
      : e.tieneCredencialAprobada
        ? { titulo: "Credencial académica aprobada", hecho: true, href: "/cuenta/perfil-tutor#credencial" }
        : e.ultimaCredencial === "PENDIENTE"
          ? { titulo: "Credencial académica", detalle: "En revisión por el equipo de Tinku.", hecho: false, href: "/cuenta/perfil-tutor#credencial" }
          : e.ultimaCredencial === "RECHAZADO"
            ? { titulo: "Credencial académica", detalle: "Fue rechazada: podés volver a cargarla.", hecho: false, href: "/cuenta/perfil-tutor#credencial" }
            : { titulo: "Subí tu título o certificado", hecho: false, href: "/cuenta/perfil-tutor#credencial" };
  return [
    { titulo: "Identidad verificada", hecho: true, href: "/cuenta" },
    credencial,
    { titulo: "Elegí qué materias enseñás", hecho: e.tieneMaterias, href: "/cuenta/materias" },
    { titulo: "Publicá tus horarios", hecho: tieneHorarios, href: "/cuenta/horarios" },
    { titulo: "Poné tu precio", hecho: e.tienePrecio, href: "/cuenta/precio" },
    { titulo: "Presentación y foto", hecho: e.tieneBio && e.tieneFoto, href: "/cuenta/perfil-tutor", opcional: true },
  ];
}

/**
 * "Qué me falta para recibir alumnos" (UX-06 §1). La visibilidad la decide el
 * backend (`visibleEnBusquedas`); los ítems son guía.
 */
export default function ChecklistTutor({ tutorId, compacto }: { tutorId: string | null; compacto?: boolean }) {
  const [estado, setEstado] = useState<EstadoPerfilTutor | null>(null);
  const [franjas, setFranjas] = useState<Franja[] | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    getEstadoPerfilTutor()
      .then(setEstado)
      .catch(() => setError(true));
  }, []);
  useEffect(() => {
    if (!tutorId) return;
    api
      .get<Franja[]>(`/api/tutores/${tutorId}/franjas`)
      .then(setFranjas)
      .catch(() => setFranjas([]));
  }, [tutorId]);

  if (error) return null;
  if (!estado || franjas === null) {
    return (
      <div role="status">
        <span className="sr-only">Cargando tu progreso…</span>
        <Skeleton className="h-28 w-full rounded-tarjeta" />
      </div>
    );
  }

  const lista = items(estado, franjas.some((f) => f.activa));
  const obligatorios = lista.filter((i) => !i.opcional);
  const hechos = obligatorios.filter((i) => i.hecho).length;
  const pct = Math.round((hechos / obligatorios.length) * 100);
  const pendientes = lista.filter((i) => !i.hecho);

  if (estado.visibleEnBusquedas && compacto && pendientes.every((p) => p.opcional)) {
    return (
      <Tarjeta variante="plana" className="flex flex-wrap items-center justify-between gap-3 bg-exito-suave">
        <p className="flex items-center gap-2 font-semibold text-exito">
          <Check className="size-5" aria-hidden /> Tu perfil ya aparece en las búsquedas
        </p>
        {tutorId && (
          <Link href={`/tutores/${tutorId}`} className="inline-flex items-center gap-1.5 text-sm font-semibold">
            <Eye className="size-4" aria-hidden /> Ver cómo lo ve una familia
          </Link>
        )}
      </Tarjeta>
    );
  }

  return (
    <Tarjeta>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-bold">
          {estado.visibleEnBusquedas ? "Tu perfil ya aparece en las búsquedas" : "Te falta poco para recibir alumnos"}
        </h2>
        <span className="tabular text-sm font-semibold text-tinta-suave">
          {hechos} de {obligatorios.length}
        </span>
      </div>
      <div
        className="mt-3 h-2 overflow-hidden rounded-full bg-superficie-hundida"
        role="progressbar"
        aria-valuenow={pct}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label="Perfil completo"
      >
        <div className="h-full rounded-full bg-marca-700 transition-[width] duration-500" style={{ width: `${pct}%` }} />
      </div>
      <ul className="mt-4 flex list-none flex-col p-0">
        {(compacto ? pendientes : lista).map((i) => (
          <li key={i.titulo} className="border-t border-borde first:border-t-0">
            <Link href={i.href} className="flex min-h-14 items-center gap-3 py-2 text-tinta no-underline hover:text-marca-700">
              <span
                aria-hidden
                className={cn(
                  "flex size-6 shrink-0 items-center justify-center rounded-full border-2",
                  i.hecho ? "border-marca-700 bg-marca-700 text-white" : "border-borde-control"
                )}
              >
                {i.hecho && <Check className="size-3.5" />}
              </span>
              <span className="flex-1">
                <span className={cn("block text-[15px] font-semibold", i.hecho && "text-tinta-suave")}>
                  {i.titulo}
                  {i.opcional && <span className="font-normal text-tinta-tenue"> · opcional</span>}
                  <span className="sr-only">{i.hecho ? " (listo)" : " (pendiente)"}</span>
                </span>
                {i.detalle && <span className="block text-sm text-tinta-tenue">{i.detalle}</span>}
              </span>
              <ChevronRight className="size-4 text-tinta-tenue" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </Tarjeta>
  );
}
