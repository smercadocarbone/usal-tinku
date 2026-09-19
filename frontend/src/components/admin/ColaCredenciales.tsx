"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  getColaCredenciales,
  mensajeDeError,
  resolverCredencial,
  type CredencialCola,
  type DecisionCredencial,
} from "@/lib/api";
import { Alerta, Boton, Cargando, Tarjeta } from "@/components/ui";

const ETIQUETA_TIPO: Record<string, string> = {
  TITULO: "Título",
  CERTIFICADO_ANALITICO: "Certificado analítico",
  MATRICULA: "Matrícula",
};

function formatFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function ColaCredenciales() {
  const [credenciales, setCredenciales] = useState<CredencialCola[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [procesandoId, setProcesandoId] = useState<string | null>(null);

  function cargar() {
    setCargando(true);
    setError(null);
    setProhibido(false);
    getColaCredenciales()
      .then(setCredenciales)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) {
          setProhibido(true);
        } else {
          setError(mensajeDeError(err, "No se pudo cargar la cola de credenciales."));
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargar, []);

  async function resolver(id: string, decision: DecisionCredencial) {
    setProcesandoId(id);
    try {
      await resolverCredencial(id, decision);
      setCredenciales((prev) => (prev ? prev.filter((c) => c.id !== id) : prev));
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo resolver la credencial."));
    } finally {
      setProcesandoId(null);
    }
  }

  if (cargando) {
    return <Cargando>Cargando credenciales…</Cargando>;
  }

  if (prohibido) {
    return (
      <p className="text-sm text-slate-500">
        No tenés permiso de Moderación y Seguridad para ver esta cola.
      </p>
    );
  }

  if (!credenciales || credenciales.length === 0) {
    return <p className="text-sm text-slate-500">No hay credenciales pendientes de revisión.</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      {error && (
        <Alerta tono="error" className="flex items-start gap-2">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
          <span>{error}</span>
        </Alerta>
      )}
      <ul className="flex flex-col gap-3">
        {credenciales.map((c) => (
          <Tarjeta
            as="li"
            key={c.id}
            className="flex flex-wrap items-center justify-between gap-3 p-5"
          >
            <div>
              <p className="text-sm font-semibold text-slate-800">
                {c.tutorNombre} {c.tutorApellido}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {ETIQUETA_TIPO[c.tipoDocumento] ?? c.tipoDocumento} · Intento {c.numeroIntento} ·
                Enviado {formatFecha(c.createdAt)}
                {c.cicloEsperaHasta && ` · Backoff hasta ${formatFecha(c.cicloEsperaHasta)}`}
              </p>
            </div>
            <div className="flex gap-2">
              <Boton
                variante="peligro"
                tamano="sm"
                onClick={() => resolver(c.id, "RECHAZAR")}
                disabled={procesandoId === c.id}
              >
                Rechazar
              </Boton>
              <Boton
                tamano="sm"
                onClick={() => resolver(c.id, "APROBAR")}
                disabled={procesandoId === c.id}
              >
                {procesandoId === c.id ? "Procesando…" : "Aprobar"}
              </Boton>
            </div>
          </Tarjeta>
        ))}
      </ul>
    </div>
  );
}
