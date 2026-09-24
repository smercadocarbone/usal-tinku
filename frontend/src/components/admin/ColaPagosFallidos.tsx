"use client";

import { useEffect, useState } from "react";
import { AlertTriangle } from "lucide-react";
import {
  getColaPagosFallidos,
  mensajeDeError,
  reembolsarParcial,
  reintentarLiberacion,
  type PagoFallido,
} from "@/lib/api";
import { formatearPrecio } from "@/lib/formatos";
import {
  Alerta,
  Boton,
  Campo,
  Cargando,
  EstadoVacio,
  Insignia,
  Tarjeta,
} from "@/components/ui";
import { ETIQUETA_ESTADO_PAGO } from "@/lib/etiquetas";

function formatFecha(iso: string | null): string {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("es-AR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function FilaPago({
  pago,
  onActualizado,
  onError,
}: {
  pago: PagoFallido;
  onActualizado: (p: PagoFallido) => void;
  onError: (msg: string) => void;
}) {
  const [procesando, setProcesando] = useState(false);
  const [mostrandoParcial, setMostrandoParcial] = useState(false);
  const [monto, setMonto] = useState("");

  async function reintentar() {
    setProcesando(true);
    try {
      onActualizado(await reintentarLiberacion(pago.id));
    } catch (err) {
      onError(mensajeDeError(err, "No se pudo reintentar la liberación."));
    } finally {
      setProcesando(false);
    }
  }

  async function confirmarParcial() {
    const valor = Number(monto);
    if (!valor || valor <= 0 || valor >= pago.montoBruto) {
      onError("El monto parcial debe ser mayor a 0 y menor al total cobrado.");
      return;
    }
    setProcesando(true);
    try {
      onActualizado(await reembolsarParcial(pago.id, valor));
      setMostrandoParcial(false);
      setMonto("");
    } catch (err) {
      onError(mensajeDeError(err, "No se pudo procesar el reembolso parcial."));
    } finally {
      setProcesando(false);
    }
  }

  return (
    <Tarjeta as="li" className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold text-slate-800">
            {formatearPrecio(pago.montoBruto)}
          </p>
          <p className="mt-1 text-xs text-slate-500">
            Reserva #{pago.reservaId.slice(0, 8)} · {pago.intentosLiberacion} reintentos
            automáticos agotados · Debía liberarse {formatFecha(pago.liberarAt)}
          </p>
        </div>
        <Insignia tono="peligro">{ETIQUETA_ESTADO_PAGO[pago.estado] ?? pago.estado}</Insignia>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <Boton
          tamano="sm"
          onClick={reintentar}
          cargando={procesando}
          textoCargando="Procesando…"
        >
          Reintentar liberación
        </Boton>
        <Boton
          variante="secundario"
          tamano="sm"
          onClick={() => setMostrandoParcial((v) => !v)}
        >
          {mostrandoParcial ? "Cancelar reembolso parcial" : "Reembolso parcial por disputa"}
        </Boton>
      </div>

      {mostrandoParcial && (
        <div className="mt-3 flex flex-wrap items-end gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4">
          <Campo
            id={`monto-${pago.id}`}
            etiqueta="Monto a reembolsar"
            type="number"
            min={0.01}
            step="0.01"
            max={pago.montoBruto}
            value={monto}
            onChange={(e) => setMonto(e.target.value)}
            className="w-40"
          />
          <Boton tamano="sm" onClick={confirmarParcial} disabled={procesando}>
            Confirmar reembolso
          </Boton>
        </div>
      )}
    </Tarjeta>
  );
}

export default function ColaPagosFallidos() {
  const [pagos, setPagos] = useState<PagoFallido[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [prohibido, setProhibido] = useState(false);

  function cargar() {
    setCargando(true);
    setError(null);
    setProhibido(false);
    getColaPagosFallidos()
      .then(setPagos)
      .catch((err) => {
        if (err && typeof err === "object" && "status" in err && err.status === 403) {
          setProhibido(true);
        } else {
          setError(mensajeDeError(err, "No se pudo cargar la cola de pagos fallidos."));
        }
      })
      .finally(() => setCargando(false));
  }

  useEffect(cargar, []);

  function onActualizado(actualizado: PagoFallido) {
    // Reintentar liberación puede sacarlo de "fallido"; reembolso parcial no
    // cambia el estado (el resto del escrow sigue su curso normal) — en
    // ambos casos, refrescamos desde el server para reflejar la realidad.
    cargar();
    void actualizado;
  }

  if (cargando) {
    return <Cargando>Cargando pagos fallidos…</Cargando>;
  }

  if (prohibido) {
    return (
      <EstadoVacio>
        No tenés permiso de Soporte Financiero para ver esta cola.
      </EstadoVacio>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {error && (
        <Alerta tono="error" className="flex items-start gap-2">
          <AlertTriangle size={16} className="mt-0.5 shrink-0" aria-hidden />
          <span>{error}</span>
        </Alerta>
      )}
      {!pagos || pagos.length === 0 ? (
        <EstadoVacio>No hay pagos que requieran intervención manual.</EstadoVacio>
      ) : (
        <ul className="flex flex-col gap-3">
          {pagos.map((p) => (
            <FilaPago key={p.id} pago={p} onActualizado={onActualizado} onError={setError} />
          ))}
        </ul>
      )}
    </div>
  );
}
