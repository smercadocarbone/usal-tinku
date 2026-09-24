"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  actualizarEstadoTicket,
  getTicketsAdmin,
  mensajeDeError,
  type EstadoTicket,
  type TicketAdmin,
} from "@/lib/api";
import {
  Alerta,
  CampoSelect,
  Cargando,
  EstadoVacio,
  Insignia,
  Tarjeta,
  type TonoInsignia,
} from "@/components/ui";
import { ETIQUETA_ESTADO_TICKET as ETIQUETA_ESTADO } from "@/lib/etiquetas";

const TONO_ESTADO: Record<string, TonoInsignia> = {
  abierto: "peligro",
  en_proceso: "aviso",
  resuelto: "exito",
  cerrado: "neutro",
};

const ESTADOS: EstadoTicket[] = ["abierto", "en_proceso", "resuelto", "cerrado"];

function formatFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function TicketsSoporte() {
  const [tickets, setTickets] = useState<TicketAdmin[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [guardandoId, setGuardandoId] = useState<string | null>(null);
  const [errorPorTicket, setErrorPorTicket] = useState<Record<string, string>>({});

  useEffect(() => {
    getTicketsAdmin()
      .then(setTickets)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) {
          setProhibido(true);
        } else {
          setError(mensajeDeError(err, "No se pudieron cargar los tickets."));
        }
      })
      .finally(() => setCargando(false));
  }, []);

  async function cambiarEstado(id: string, estado: EstadoTicket) {
    setGuardandoId(id);
    setErrorPorTicket((prev) => ({ ...prev, [id]: "" }));
    try {
      const actualizado = await actualizarEstadoTicket(id, estado);
      setTickets((prev) => (prev ? prev.map((t) => (t.id === id ? actualizado : t)) : prev));
    } catch (err) {
      setErrorPorTicket((prev) => ({
        ...prev,
        [id]: mensajeDeError(err, "No se pudo actualizar el estado."),
      }));
    } finally {
      setGuardandoId(null);
    }
  }

  if (cargando) {
    return <Cargando>Cargando tickets…</Cargando>;
  }

  if (prohibido) {
    return <EstadoVacio>No tenés tickets asignados a tu rol.</EstadoVacio>;
  }

  if (error) {
    return (
      <Alerta tono="error" className="flex items-start gap-2">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
        <span>{error}</span>
      </Alerta>
    );
  }

  if (!tickets || tickets.length === 0) {
    return <EstadoVacio>No hay tickets de soporte para tu rol.</EstadoVacio>;
  }

  return (
    <ul className="flex flex-col gap-3">
      {tickets.map((t) => (
        <Tarjeta as="li" key={t.id} className="p-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <p className="text-sm font-semibold text-slate-800">{t.asunto}</p>
              <p className="mt-1 text-xs text-slate-500">
                Origen: {t.origenModulo} · Abierto {formatFecha(t.creadoEn)}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <Insignia tono={TONO_ESTADO[t.estado] ?? "neutro"}>
                {ETIQUETA_ESTADO[t.estado] ?? t.estado}
              </Insignia>
              <CampoSelect
                id={`estado-${t.id}`}
                etiqueta="Cambiar estado"
                etiquetaOculta
                className="w-auto py-1.5 text-xs"
                value={t.estado}
                disabled={guardandoId === t.id}
                onChange={(e) => cambiarEstado(t.id, e.target.value as EstadoTicket)}
              >
                {ESTADOS.map((estado) => (
                  <option key={estado} value={estado}>
                    {ETIQUETA_ESTADO[estado]}
                  </option>
                ))}
              </CampoSelect>
            </div>
          </div>
          <p className="mt-3 text-sm text-slate-800">{t.detalle}</p>
          {errorPorTicket[t.id] && <Alerta tono="error" className="mt-3">{errorPorTicket[t.id]}</Alerta>}
        </Tarjeta>
      ))}
    </ul>
  );
}
