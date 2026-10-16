"use client";

import { useEffect, useRef, useState } from "react";
import { ClasificadorKillswitch } from "@/lib/killswitch/clasificador";
import { INTERVALO_CLASIFICACION_MS, MUESTRAS_PARA_CONFIRMAR } from "@/lib/killswitch/config";
import { cargarModelo } from "@/lib/killswitch/modelo";

const DURACION_S = 60;

type Backend = "webgl" | "wasm";

export interface ResultadoDiagnostico {
  fecha: string;
  navegador: string;
  nucleos: number | null;
  backendPedido: Backend | "automatico";
  backend: string;
  cargaMs: number;
  duracionS: number;
  muestras: number;
  p50: number;
  p95: number;
  max: number;
  salteados: number;
  tensoresInicio: number;
  tensoresFinal: number;
  /** Después de liberar el modelo: tendría que quedar en 0. */
  tensoresDespuesDeDestruir?: number;
  /** MUESTRAS_PARA_CONFIRMAR × max(INTERVALO_CLASIFICACION_MS, p95), con 1 y 2 tracks (ADR-M3-07 §3). */
  deteccionEstimadaMs: { unTrack: number; dosTracks: number };
  eventos: string[];
  error?: string;
}

type Estado =
  | { fase: "inicio" }
  | { fase: "cargando" }
  | { fase: "midiendo"; restante: number; backend: string; cargaMs: number; tensoresInicio: number }
  | { fase: "listo"; resultado: ResultadoDiagnostico }
  | { fase: "error"; mensaje: string };

function leerParametros(): { backend?: Backend; duracion: number } {
  const q = new URLSearchParams(window.location.search);
  const b = q.get("backend");
  const s = Number(q.get("segundos"));
  return {
    backend: b === "webgl" || b === "wasm" ? b : undefined,
    // `?segundos=` solo para pruebas rápidas; la medición del anexo usa los 60 s por defecto.
    duracion: Number.isFinite(s) && s > 0 && s <= 600 ? s : DURACION_S,
  };
}

/**
 * Mide el clasificador real (el mismo código del aula) sobre la cámara propia, con `hayMenor: true`.
 * Nada sale del navegador: los frames se pisan en un canvas y no hay llamadas a la API.
 */
export default function Diagnostico() {
  const [estado, setEstado] = useState<Estado>({ fase: "inicio" });
  const [copiado, setCopiado] = useState(false);
  const limpiar = useRef<() => void>(() => {});

  useEffect(() => () => limpiar.current(), []);

  async function empezar() {
    const { backend: pedido, duracion } = leerParametros();
    const eventos: string[] = [];
    const t0 = performance.now();
    const reg = (e: string) => eventos.push(`${Math.round(performance.now() - t0)} ms: ${e}`);
    let camara: MediaStream | null = null;
    let k: ClasificadorKillswitch | null = null;
    limpiar.current = () => {
      k?.destruir();
      camara?.getTracks().forEach((t) => t.stop());
    };
    try {
      setEstado({ fase: "cargando" });
      camara = await navigator.mediaDevices.getUserMedia({ video: true });
      const tf = await import("@tensorflow/tfjs");
      const inicioCarga = performance.now();
      k = await ClasificadorKillswitch.crear({
        hayMenor: true,
        cargar: () => cargarModelo(pedido ? { backend: pedido } : undefined),
        alDetectar: () => reg("detección confirmada"),
        alDemorar: () => reg("demora"),
        alNormalizar: () => reg("normalizado"),
        alCaer: () => reg("caída"),
        alReactivar: () => reg("reactivado"),
        alFallarDefinitivo: () => reg("falla definitiva"),
      });
      const cargaMs = Math.round(performance.now() - inicioCarga);
      const tensoresInicio = tf.memory().numTensors;
      const backend = k.estadisticas().backend;
      k.observar("propia", "propia", camara.getVideoTracks()[0]);

      for (let restante = duracion; restante > 0; restante--) {
        setEstado({ fase: "midiendo", restante, backend, cargaMs, tensoresInicio });
        await new Promise((r) => setTimeout(r, 1000));
      }

      const est = k.estadisticas();
      // Los tensores se cuentan con el ciclo quieto: una inferencia en vuelo tiene tensores intermedios.
      k.pausar("propia");
      await new Promise((r) => setTimeout(r, Math.max(1000, 2 * est.max)));
      const tensoresFinal = tf.memory().numTensors;
      const porTurno = Math.max(INTERVALO_CLASIFICACION_MS, est.p95);
      const resultado: ResultadoDiagnostico = {
        fecha: new Date().toISOString(),
        navegador: navigator.userAgent,
        nucleos: navigator.hardwareConcurrency ?? null,
        backendPedido: pedido ?? "automatico",
        backend: est.backend,
        cargaMs,
        duracionS: duracion,
        muestras: est.muestras,
        p50: Math.round(est.p50),
        p95: Math.round(est.p95),
        max: Math.round(est.max),
        salteados: est.salteados,
        tensoresInicio,
        tensoresFinal,
        deteccionEstimadaMs: {
          unTrack: Math.round(MUESTRAS_PARA_CONFIRMAR * porTurno),
          dosTracks: Math.round(MUESTRAS_PARA_CONFIRMAR * 2 * porTurno),
        },
        eventos,
      };
      limpiar.current();
      resultado.tensoresDespuesDeDestruir = tf.memory().numTensors;
      limpiar.current = () => {};
      setEstado({ fase: "listo", resultado });
    } catch (e) {
      limpiar.current();
      limpiar.current = () => {};
      setEstado({ fase: "error", mensaje: e instanceof Error ? `${e.name}: ${e.message}` : String(e) });
    }
  }

  useEffect(() => {
    // Para el script de medición (frontend/scripts/medir-clasificador.mjs).
    (window as unknown as { __diagnostico?: Estado }).__diagnostico = estado;
  }, [estado]);

  async function copiar(r: ResultadoDiagnostico) {
    try {
      await navigator.clipboard.writeText(JSON.stringify(r, null, 2));
      setCopiado(true);
    } catch {
      setCopiado(false);
    }
  }

  return (
    <main className="mx-auto max-w-xl p-4 text-sm">
      <h1 className="mb-2 text-lg font-semibold">Diagnóstico del clasificador</h1>
      <p className="mb-4 text-gray-600">
        Mide cuánto tarda la protección del aula en este dispositivo, usando tu cámara durante {DURACION_S} segundos. Las
        imágenes no salen del dispositivo: no se suben ni se guardan.
      </p>

      {estado.fase === "inicio" && (
        <button type="button" className="rounded bg-black px-4 py-2 text-white" onClick={() => void empezar()}>
          Empezar medición
        </button>
      )}
      {estado.fase === "cargando" && <p role="status">Cargando el clasificador…</p>}
      {estado.fase === "midiendo" && (
        <dl role="status" className="grid grid-cols-2 gap-1">
          <dt>Backend</dt>
          <dd>{estado.backend}</dd>
          <dt>Carga</dt>
          <dd>{estado.cargaMs} ms</dd>
          <dt>Tensores al inicio</dt>
          <dd>{estado.tensoresInicio}</dd>
          <dt>Faltan</dt>
          <dd>{estado.restante} s</dd>
        </dl>
      )}
      {estado.fase === "error" && (
        <p role="alert" className="text-red-700">
          No se pudo medir: {estado.mensaje}
        </p>
      )}
      {estado.fase === "listo" && (
        <section>
          <dl className="mb-4 grid grid-cols-2 gap-1" data-testid="resultado">
            <dt>Backend</dt>
            <dd>{estado.resultado.backend}</dd>
            <dt>Carga</dt>
            <dd>{estado.resultado.cargaMs} ms</dd>
            <dt>p50 / p95 / máx</dt>
            <dd>
              {estado.resultado.p50} / {estado.resultado.p95} / {estado.resultado.max} ms
            </dd>
            <dt>Muestras</dt>
            <dd>{estado.resultado.muestras}</dd>
            <dt>Tensores (inicio → final)</dt>
            <dd>
              {estado.resultado.tensoresInicio} → {estado.resultado.tensoresFinal}
            </dd>
            <dt>Turnos salteados</dt>
            <dd>{estado.resultado.salteados}</dd>
            <dt>Detección estimada (1 / 2 tracks)</dt>
            <dd>
              {estado.resultado.deteccionEstimadaMs.unTrack} / {estado.resultado.deteccionEstimadaMs.dosTracks} ms
            </dd>
          </dl>
          <button type="button" className="rounded bg-black px-4 py-2 text-white" onClick={() => void copiar(estado.resultado)}>
            Copiar resultados
          </button>
          {copiado && <span className="ml-2" role="status">Copiado</span>}
        </section>
      )}
    </main>
  );
}
