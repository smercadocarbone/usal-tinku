"use client";

import { useEffect, useState } from "react";
import {
  getColaReembolsosAdicional,
  mensajeDeError,
  reintentarReembolsoAdicional,
  resolverReembolsoAdicional,
  type ReembolsoAdicional,
} from "@/lib/api";
import { formatearPrecio } from "@/lib/formatos";
import { Alerta, Boton, Campo, Cargando, EstadoVacio, Insignia, Tarjeta } from "@/components/ui";

/**
 * R4 (BR-PAG-11): devoluciones del adicional de resumen que agotaron sus reintentos automáticos.
 * Soporte Financiero las reintenta o registra que las devolvió por fuera (panel de MercadoPago).
 */
function FilaReembolso({ r, onListo, onError }: { r: ReembolsoAdicional; onListo: () => void; onError: (m: string) => void }) {
  const [procesando, setProcesando] = useState(false);
  const [resolviendo, setResolviendo] = useState(false);
  const [nota, setNota] = useState("");

  async function correr(accion: () => Promise<unknown>, fallo: string) {
    setProcesando(true);
    try {
      await accion();
      onListo();
    } catch (err) {
      onError(mensajeDeError(err, fallo));
    } finally {
      setProcesando(false);
    }
  }

  return (
    <Tarjeta as="li" className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="text-sm font-semibold">{formatearPrecio(r.monto)} del resumen</p>
          <p className="mt-1 text-xs text-tinta-suave">
            Reserva #{r.reservaId.slice(0, 8)} · {r.intentos} intentos
            {r.ultimoError ? ` · último error: ${r.ultimoError}` : ""}
          </p>
        </div>
        <Insignia tono="peligro">Sin devolver</Insignia>
      </div>
      <div className="mt-3 flex flex-wrap gap-2">
        <Boton tamano="sm" cargando={procesando} textoCargando="Procesando…"
          onClick={() => void correr(() => reintentarReembolsoAdicional(r.id), "No se pudo reintentar.")}>
          Reintentar devolución
        </Boton>
        <Boton variante="secundario" tamano="sm" onClick={() => setResolviendo((v) => !v)}>
          {resolviendo ? "Cancelar" : "Lo devolví por fuera"}
        </Boton>
      </div>
      {resolviendo && (
        <div className="mt-3 flex flex-wrap items-end gap-3 rounded-lg border border-borde bg-fondo p-4">
          <Campo id={`nota-${r.id}`} etiqueta="Cómo lo devolviste (operación de MP, fecha)" value={nota}
            maxLength={300} onChange={(e) => setNota(e.target.value)} className="min-w-64 flex-1" />
          <Boton tamano="sm" disabled={procesando || nota.trim().length === 0}
            onClick={() => void correr(() => resolverReembolsoAdicional(r.id, nota.trim()), "No se pudo registrar.")}>
            Registrar
          </Boton>
        </div>
      )}
    </Tarjeta>
  );
}

export default function ColaReembolsosAdicional() {
  const [cola, setCola] = useState<ReembolsoAdicional[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  function cargar() {
    setError(null);
    getColaReembolsosAdicional()
      .then(setCola)
      .catch((err) => {
        setCola([]);
        setError(mensajeDeError(err, "No se pudo cargar la cola."));
      });
  }

  useEffect(cargar, []);

  if (cola === null) return <Cargando>Cargando devoluciones del resumen…</Cargando>;

  return (
    <div className="flex flex-col gap-3">
      {error && <Alerta tono="error">{error}</Alerta>}
      {cola.length === 0 ? (
        <EstadoVacio>No hay devoluciones del resumen pendientes.</EstadoVacio>
      ) : (
        <ul className="flex flex-col gap-3">
          {cola.map((r) => (
            <FilaReembolso key={r.id} r={r} onListo={cargar} onError={setError} />
          ))}
        </ul>
      )}
    </div>
  );
}
