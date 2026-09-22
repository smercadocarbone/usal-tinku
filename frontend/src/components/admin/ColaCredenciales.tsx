"use client";

import { useEffect, useRef, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  getArchivoCredencial,
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

/** Documento abierto en el visor: URL de blob local (se revoca al cerrar) + su tipo. */
type Documento = { id: string; url: string; tipo: string };

export default function ColaCredenciales() {
  const [credenciales, setCredenciales] = useState<CredencialCola[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);
  const [procesandoId, setProcesandoId] = useState<string | null>(null);
  const [documento, setDocumento] = useState<Documento | null>(null);
  const [abriendoId, setAbriendoId] = useState<string | null>(null);
  const urlAbierta = useRef<string | null>(null);

  function cerrarDocumento() {
    if (urlAbierta.current) URL.revokeObjectURL(urlAbierta.current);
    urlAbierta.current = null;
    setDocumento(null);
  }

  // Al desmontar, liberar el blob del documento que haya quedado abierto.
  useEffect(
    () => () => {
      if (urlAbierta.current) URL.revokeObjectURL(urlAbierta.current);
    },
    []
  );

  // AUD-007: hasta acá la credencial se aprobaba sin verla. El archivo se pide
  // con el token (fetch → blob) porque un <a href> no manda el header de auth.
  async function verDocumento(id: string) {
    if (documento?.id === id) {
      cerrarDocumento();
      return;
    }
    setAbriendoId(id);
    setError(null);
    try {
      const blob = await getArchivoCredencial(id);
      cerrarDocumento();
      const url = URL.createObjectURL(blob);
      urlAbierta.current = url;
      setDocumento({ id, url, tipo: blob.type });
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo abrir el documento de la credencial."));
    } finally {
      setAbriendoId(null);
    }
  }

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
      if (documento?.id === id) cerrarDocumento();
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
                variante="secundario"
                tamano="sm"
                onClick={() => verDocumento(c.id)}
                disabled={abriendoId === c.id}
              >
                {abriendoId === c.id
                  ? "Abriendo…"
                  : documento?.id === c.id
                    ? "Ocultar documento"
                    : "Ver documento"}
              </Boton>
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
            {documento?.id === c.id && (
              <div className="w-full overflow-hidden rounded-lg border border-slate-200">
                {documento.tipo.startsWith("image/") ? (
                  // eslint-disable-next-line @next/next/no-img-element -- blob local, sin optimización posible
                  <img
                    src={documento.url}
                    alt={`Credencial de ${c.tutorNombre} ${c.tutorApellido}`}
                    className="max-h-[70vh] w-full object-contain"
                  />
                ) : documento.tipo === "application/pdf" ? (
                  // Sin sandbox a propósito: Chrome bloquea su visor de PDF en un iframe con
                  // sandbox. El backend ya verificó la firma %PDF y sirve application/pdf:
                  // el navegador lo abre con su visor aislado, nunca como HTML.
                  // oxlint-disable-next-line react/iframe-missing-sandbox
                  <iframe
                    src={documento.url}
                    title={`Credencial de ${c.tutorNombre} ${c.tutorApellido}`}
                    className="h-[70vh] w-full"
                  />
                ) : (
                  <p className="p-4 text-sm text-slate-500">
                    El archivo no es PDF ni imagen (fue subido antes de la validación de
                    formato).{" "}
                    <a
                      href={documento.url}
                      download={`credencial-${c.id}`}
                      className="font-medium text-slate-700 underline"
                    >
                      Descargarlo
                    </a>{" "}
                    y revisarlo con cuidado antes de decidir.
                  </p>
                )}
              </div>
            )}
          </Tarjeta>
        ))}
      </ul>
    </div>
  );
}
