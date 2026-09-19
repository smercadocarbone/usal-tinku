"use client";

import { useEffect, useState } from "react";
import { AlertTriangle, ShieldAlert } from "lucide-react";
import {
  getColaAlertas,
  mensajeDeError,
  resolverAlerta,
  type AlertaSeguridadCola,
  type DecisionAlerta,
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

const ETIQUETA_RAMA: Record<string, string> = {
  menor: "Corte directo (menor presente)",
  adultos: "Confirmación entre adultos",
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
  alerta,
  onResuelto,
}: {
  alerta: AlertaSeguridadCola;
  onResuelto: (id: string) => void;
}) {
  const [decision, setDecision] = useState<DecisionAlerta>("reactivar");
  const [tipoSancion, setTipoSancion] = useState<TipoSancion>("advertencia");
  const [diasSuspension, setDiasSuspension] = useState("7");
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirmar() {
    setEnviando(true);
    setError(null);
    try {
      await resolverAlerta(alerta.id, {
        decision,
        ...(decision === "sancionar"
          ? {
              tipoSancion,
              ...(tipoSancion === "suspension_temporal"
                ? { diasSuspension: Number(diasSuspension) || 1 }
                : {}),
            }
          : {}),
      });
      onResuelto(alerta.id);
    } catch (err) {
      setError(mensajeDeError(err, "No se pudo resolver la alerta."));
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="mt-4 flex flex-col gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div
        className="flex flex-col gap-1.5"
        role="radiogroup"
        aria-label="Decisión sobre la alerta"
      >
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="radio"
            name={`decision-${alerta.id}`}
            checked={decision === "reactivar"}
            onChange={() => setDecision("reactivar")}
          />
          Reactivar (acusación falsa)
        </label>
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="radio"
            name={`decision-${alerta.id}`}
            checked={decision === "sancionar"}
            onChange={() => setDecision("sancionar")}
          />
          Sancionar
        </label>
      </div>

      {decision === "sancionar" && (
        <div className="flex flex-wrap items-end gap-3">
          <CampoSelect
            id={`sancion-${alerta.id}`}
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
              id={`dias-${alerta.id}`}
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

function FilaAlerta({
  alerta,
  onResuelto,
}: {
  alerta: AlertaSeguridadCola;
  onResuelto: (id: string) => void;
}) {
  const [abierta, setAbierta] = useState(false);
  const esMenor = alerta.rama === "menor";

  return (
    <Tarjeta as="li" className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-2">
            <ShieldAlert
              size={16}
              className={esMenor ? "text-red-700" : "text-amber-800"}
              aria-hidden
            />
            <span className="text-sm font-semibold text-slate-800">
              {ETIQUETA_RAMA[alerta.rama] ?? alerta.rama}
            </span>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            Sesión #{alerta.sesionId.slice(0, 8)} · Detectado #{alerta.detectadoId.slice(0, 8)} ·
            Disparada {formatFecha(alerta.createdAt)}
          </p>
        </div>
        <Insignia tono="aviso">Ventana de 12hs</Insignia>
      </div>

      {alerta.descargoTexto && (
        <div className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-800">
          <p className="mb-1 text-xs font-semibold text-slate-500">
            Descargo del detectado ({formatFecha(alerta.descargoRecibidoAt)}):
          </p>
          {alerta.descargoTexto}
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

      {abierta && <FormularioResolucion alerta={alerta} onResuelto={onResuelto} />}
    </Tarjeta>
  );
}

export default function ColaAlertas() {
  const [alertas, setAlertas] = useState<AlertaSeguridadCola[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);

  function cargar() {
    setCargando(true);
    setError(null);
    setProhibido(false);
    getColaAlertas()
      .then(setAlertas)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) {
          setProhibido(true);
        } else {
          setError(mensajeDeError(err, "No se pudo cargar la cola de alertas."));
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargar, []);

  function onResuelto(id: string) {
    setAlertas((prev) => (prev ? prev.filter((a) => a.id !== id) : prev));
  }

  if (cargando) {
    return <Cargando>Cargando alertas…</Cargando>;
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

  if (!alertas || alertas.length === 0) {
    return <EstadoVacio>No hay alertas de seguridad pendientes.</EstadoVacio>;
  }

  return (
    <ul className="flex flex-col gap-3">
      {alertas.map((a) => (
        <FilaAlerta key={a.id} alerta={a} onResuelto={onResuelto} />
      ))}
    </ul>
  );
}
