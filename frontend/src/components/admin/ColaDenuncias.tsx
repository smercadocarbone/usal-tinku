"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  getColaDenuncias,
  mensajeDeError,
  resolverDenuncia,
  type DenunciaCola,
  type ResolucionDenuncia,
  type TipoSancion,
} from "@/lib/api";
import {
  Alerta,
  Boton,
  Campo,
  CampoSelect,
  Cargando,
  EstadoVacio,
  Insignia,
  Tarjeta,
} from "@/components/ui";

const ETIQUETA_MOTIVO: Record<string, string> = {
  comportamiento_inapropiado: "Comportamiento inapropiado",
  incumplimiento: "Incumplimiento",
  fraude: "Fraude",
  contenido_ilegal: "Contenido ilegal",
  acoso: "Acoso",
};

const ETIQUETA_SANCION: Record<TipoSancion, string> = {
  advertencia: "Advertencia",
  suspension_temporal: "Suspensión temporal",
  suspension_definitiva: "Suspensión definitiva",
  baneo_autoridades: "Baneo + derivación a autoridades",
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

function FormularioResolucion({
  denuncia,
  onResuelto,
}: {
  denuncia: DenunciaCola;
  onResuelto: (id: string) => void;
}) {
  const [resolucion, setResolucion] = useState<ResolucionDenuncia>("infundada");
  const [tipoSancion, setTipoSancion] = useState<TipoSancion>("advertencia");
  const [diasSuspension, setDiasSuspension] = useState("7");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirmar() {
    setEnviando(true);
    setError(null);
    try {
      await resolverDenuncia(denuncia.id, {
        resolucion,
        // "escalada" fuerza suspensión definitiva del lado del backend
        // (FR-SEC-009): no hace falta ni se pide tipoSancion acá.
        ...(resolucion === "fundada"
          ? {
              tipoSancion,
              ...(tipoSancion === "suspension_temporal"
                ? { diasSuspension: Number(diasSuspension) || 1 }
                : {}),
            }
          : {}),
      });
      onResuelto(denuncia.id);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo resolver la denuncia."));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mt-4 flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div
        className="flex flex-col gap-1.5"
        role="radiogroup"
        aria-label="Resolución de la denuncia"
      >
        {(
          [
            ["infundada", "Infundada"],
            ["fundada", "Fundada"],
            ["escalada", "Escalada (contenido ilegal → suspensión definitiva)"],
          ] as [ResolucionDenuncia, string][]
        ).map(([valor, etiqueta]) => (
          <label key={valor} className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="radio"
              name={`resolucion-${denuncia.id}`}
              checked={resolucion === valor}
              onChange={() => setResolucion(valor)}
            />
            {etiqueta}
          </label>
        ))}
      </div>

      {resolucion === "fundada" && (
        <div className="flex flex-wrap items-end gap-3">
          <CampoSelect
            id={`sancion-${denuncia.id}`}
            etiqueta="Tipo de sanción"
            value={tipoSancion}
            onChange={(e) => setTipoSancion(e.target.value as TipoSancion)}
          >
            {(Object.keys(ETIQUETA_SANCION) as TipoSancion[]).map((t) => (
              <option key={t} value={t}>
                {ETIQUETA_SANCION[t]}
              </option>
            ))}
          </CampoSelect>
          {tipoSancion === "suspension_temporal" && (
            <Campo
              id={`dias-${denuncia.id}`}
              etiqueta="Días de suspensión"
              type="number"
              min={1}
              value={diasSuspension}
              onChange={(e) => setDiasSuspension(e.target.value)}
              className="w-24"
            />
          )}
        </div>
      )}

      {error && <Alerta tono="error">{error}</Alerta>}

      <Boton
        tamano="sm"
        className="w-fit"
        onClick={confirmar}
        cargando={enviando}
        textoCargando="Resolviendo…"
      >
        Confirmar resolución
      </Boton>
    </div>
  );
}

function FilaDenuncia({
  denuncia,
  onResuelto,
}: {
  denuncia: DenunciaCola;
  onResuelto: (id: string) => void;
}) {
  const [abierta, setAbierta] = useState(false);

  return (
    <Tarjeta as="li" className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-slate-800">
              {ETIQUETA_MOTIVO[denuncia.motivo] ?? denuncia.motivo}
            </span>
            {denuncia.prioridadAlta && (
              <Insignia tono="peligro" className="px-2 py-0.5">
                Prioridad alta
              </Insignia>
            )}
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Denunciado #{denuncia.denunciadoId.slice(0, 8)}
            {denuncia.sesionId && ` · Sesión #${denuncia.sesionId.slice(0, 8)}`} · Recibida{" "}
            {formatFecha(denuncia.createdAt)}
          </p>
        </div>
        {denuncia.slaResolucionVenceAt && (
          <Insignia tono="aviso">
            SLA vence {formatFecha(denuncia.slaResolucionVenceAt)}
          </Insignia>
        )}
      </div>

      {denuncia.descargoTexto && (
        <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800">
          <p className="mb-1 text-xs font-semibold text-slate-500">
            Descargo ({formatFecha(denuncia.descargoVenceAt)} vencimiento):
          </p>
          {denuncia.descargoTexto}
        </div>
      )}

      <Boton
        variante="secundario"
        tamano="sm"
        className="mt-3"
        onClick={() => setAbierta((v) => !v)}
      >
        {abierta ? "Ocultar resolución" : "Resolver"}
      </Boton>

      {abierta && <FormularioResolucion denuncia={denuncia} onResuelto={onResuelto} />}
    </Tarjeta>
  );
}

export default function ColaDenuncias() {
  const [denuncias, setDenuncias] = useState<DenunciaCola[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);

  function cargar() {
    setCargando(true);
    setError(null);
    setProhibido(false);
    getColaDenuncias()
      .then(setDenuncias)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) {
          setProhibido(true);
        } else {
          setError(mensajeDeError(err, "No se pudo cargar la cola de denuncias."));
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargar, []);

  function onResuelto(id: string) {
    setDenuncias((prev) => (prev ? prev.filter((d) => d.id !== id) : prev));
  }

  if (cargando) {
    return <Cargando>Cargando denuncias…</Cargando>;
  }

  if (prohibido) {
    return (
      <EstadoVacio>
        No tenés permiso de Moderación y Seguridad para ver esta cola.
      </EstadoVacio>
    );
  }

  if (error) {
    return (
      <Alerta tono="error" className="flex items-start gap-2">
        <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
        <span>{error}</span>
      </Alerta>
    );
  }

  if (!denuncias || denuncias.length === 0) {
    return <EstadoVacio>No hay denuncias en revisión.</EstadoVacio>;
  }

  return (
    <ul className="flex flex-col gap-3">
      {denuncias.map((d) => (
        <FilaDenuncia key={d.id} denuncia={d} onResuelto={onResuelto} />
      ))}
    </ul>
  );
}
