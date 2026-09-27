"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Download, Eraser, Pencil, Trash2, Undo2, X } from "lucide-react";
import { guardarArchivo } from "@/lib/api";
import {
  ASPECTO_PIZARRA,
  COLORES_PIZARRA,
  GROSOR_GOMA,
  GROSORES_PIZARRA,
  type MensajePizarra,
  type Trazo,
} from "@/lib/pizarra";

/** Ancho de referencia: el grosor escala con el tablero para verse igual en todas las pantallas. */
const ANCHO_BASE = 800;
/** Cada cuánto se mandan los puntos nuevos mientras se dibuja. */
const INTERVALO_ENVIO_MS = 50;

const NOMBRE_COLOR: Record<string, string> = {
  "#111827": "Negro",
  "#dc2626": "Rojo",
  "#2563eb": "Azul",
  "#16a34a": "Verde",
};

export interface PizarraProps {
  trazos: Trazo[];
  /** Cada acción propia (trazo, deshacer, borrar): la sala la aplica y se la manda al otro. */
  onMensaje: (m: MensajePizarra) => void;
  onCerrar: () => void;
}

function nuevoId(): string {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

/**
 * Pizarra compartida (FR-AULA-011, ADR-M3-06): lápiz de 4 colores y 3 grosores, goma, deshacer,
 * borrar todo y descargar como imagen (queda solo en este dispositivo). Tablero 4:3 con fondo
 * blanco; los trazos se guardan normalizados y se redibujan al cambiar el tamaño.
 */
export default function Pizarra({ trazos, onMensaje, onCerrar }: PizarraProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [color, setColor] = useState<string>(COLORES_PIZARRA[0]);
  const [grosor, setGrosor] = useState<number>(GROSORES_PIZARRA[1]);
  const [goma, setGoma] = useState(false);
  const [confirmarBorrar, setConfirmarBorrar] = useState(false);
  const [tamano, setTamano] = useState({ ancho: 0, alto: 0 });
  /** Trazo en curso: el estilo se fija al empezar, así todos sus segmentos viajan iguales. */
  const dibujando = useRef<{ id: string; color: string; grosor: number; goma: boolean; pendientes: number[] } | null>(null);
  const estiloRef = useRef({ color, grosor, goma });
  useEffect(() => {
    estiloRef.current = { color, grosor: goma ? GROSOR_GOMA : grosor, goma };
  }, [color, grosor, goma]);

  const contenedorRef = useRef<HTMLDivElement>(null);

  // El tablero es siempre 4:3 y entra entero en el espacio disponible; el canvas usa la densidad
  // real de la pantalla. Así un trazo normalizado cae en el mismo lugar en cualquier dispositivo.
  useEffect(() => {
    const contenedor = contenedorRef.current;
    const canvas = canvasRef.current;
    if (!contenedor || !canvas) return;
    const observador = new ResizeObserver(() => {
      const estilo = getComputedStyle(contenedor);
      const libreX = contenedor.clientWidth - parseFloat(estilo.paddingLeft) - parseFloat(estilo.paddingRight);
      const libreY = contenedor.clientHeight - parseFloat(estilo.paddingTop) - parseFloat(estilo.paddingBottom);
      const ancho = Math.floor(Math.max(0, Math.min(libreX, libreY * ASPECTO_PIZARRA)));
      const alto = ancho / ASPECTO_PIZARRA;
      canvas.style.width = `${ancho}px`;
      canvas.style.height = `${alto}px`;
      const dpr = window.devicePixelRatio || 1;
      canvas.width = Math.round(ancho * dpr);
      canvas.height = Math.round(alto * dpr);
      setTamano({ ancho: canvas.width, alto: canvas.height });
    });
    observador.observe(contenedor);
    return () => observador.disconnect();
  }, []);

  // Redibujo completo: los trazos son pocos y cada uno es una polilínea.
  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx || tamano.ancho === 0) return;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const escala = canvas.width / ANCHO_BASE;
    for (const t of trazos) {
      if (t.puntos.length < 2) continue;
      ctx.globalCompositeOperation = t.goma ? "destination-out" : "source-over";
      ctx.strokeStyle = t.color;
      ctx.fillStyle = t.color;
      ctx.lineWidth = t.grosor * escala;
      ctx.lineCap = "round";
      ctx.lineJoin = "round";
      const x0 = t.puntos[0] * canvas.width;
      const y0 = t.puntos[1] * canvas.height;
      if (t.puntos.length === 2) {
        ctx.beginPath();
        ctx.arc(x0, y0, ctx.lineWidth / 2, 0, Math.PI * 2);
        ctx.fill();
        continue;
      }
      ctx.beginPath();
      ctx.moveTo(x0, y0);
      for (let i = 2; i < t.puntos.length; i += 2) ctx.lineTo(t.puntos[i] * canvas.width, t.puntos[i + 1] * canvas.height);
      ctx.stroke();
    }
    ctx.globalCompositeOperation = "source-over";
  }, [trazos, tamano]);

  const enviarPendientes = useCallback(() => {
    const actual = dibujando.current;
    if (!actual || actual.pendientes.length === 0) return;
    onMensaje({ op: "segmento", id: actual.id, color: actual.color, grosor: actual.grosor, goma: actual.goma, puntos: actual.pendientes });
    actual.pendientes = [];
  }, [onMensaje]);

  useEffect(() => {
    const intervalo = window.setInterval(enviarPendientes, INTERVALO_ENVIO_MS);
    return () => window.clearInterval(intervalo);
  }, [enviarPendientes]);

  function punto(e: React.PointerEvent<HTMLCanvasElement>): [number, number] {
    const r = e.currentTarget.getBoundingClientRect();
    const x = Math.min(1, Math.max(0, (e.clientX - r.left) / r.width));
    const y = Math.min(1, Math.max(0, (e.clientY - r.top) / r.height));
    return [Math.round(x * 10000) / 10000, Math.round(y * 10000) / 10000];
  }

  function alBajar(e: React.PointerEvent<HTMLCanvasElement>) {
    e.currentTarget.setPointerCapture(e.pointerId);
    dibujando.current = { id: nuevoId(), ...estiloRef.current, pendientes: punto(e) };
    enviarPendientes();
  }

  function alMover(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!dibujando.current) return;
    dibujando.current.pendientes.push(...punto(e));
    if (dibujando.current.pendientes.length >= 200) enviarPendientes();
  }

  function alSoltar() {
    enviarPendientes();
    dibujando.current = null;
  }

  function deshacer() {
    const ultimo = [...trazos].reverse().find((t) => t.propio);
    if (ultimo) onMensaje({ op: "deshacer", id: ultimo.id });
  }

  function descargar() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const final = document.createElement("canvas");
    final.width = canvas.width;
    final.height = canvas.height;
    const ctx = final.getContext("2d");
    if (!ctx) return;
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, final.width, final.height);
    ctx.drawImage(canvas, 0, 0);
    final.toBlob((blob) => blob && guardarArchivo(blob, "pizarra-tinku.png"), "image/png");
  }

  const botonHerramienta =
    "flex h-11 min-w-11 cursor-pointer items-center justify-center gap-1.5 rounded-lg px-2 text-sm font-semibold text-gray-100 hover:bg-gray-700 aria-pressed:bg-gray-600";

  return (
    <section aria-label="Pizarra compartida" className="absolute inset-0 flex flex-col bg-gray-900">
      <div role="toolbar" aria-label="Herramientas de la pizarra" className="flex flex-wrap items-center gap-1 border-b border-gray-700 bg-gray-800 px-2 py-1.5">
        <button type="button" className={botonHerramienta} aria-pressed={!goma} onClick={() => setGoma(false)} aria-label="Lápiz">
          <Pencil className="size-4" aria-hidden />
        </button>
        <button type="button" className={botonHerramienta} aria-pressed={goma} onClick={() => setGoma(true)} aria-label="Goma">
          <Eraser className="size-4" aria-hidden />
        </button>
        <span className="mx-1 h-6 w-px bg-gray-700" aria-hidden />
        {COLORES_PIZARRA.map((c) => (
          <button
            key={c}
            type="button"
            aria-label={`Color ${NOMBRE_COLOR[c]}`}
            aria-pressed={!goma && color === c}
            onClick={() => {
              setColor(c);
              setGoma(false);
            }}
            className="flex size-11 cursor-pointer items-center justify-center rounded-lg hover:bg-gray-700 aria-pressed:bg-gray-600"
          >
            <span className="size-5 rounded-full border-2 border-white/70" style={{ backgroundColor: c }} />
          </button>
        ))}
        <span className="mx-1 h-6 w-px bg-gray-700" aria-hidden />
        {GROSORES_PIZARRA.map((g, i) => (
          <button
            key={g}
            type="button"
            aria-label={["Trazo fino", "Trazo medio", "Trazo grueso"][i]}
            aria-pressed={!goma && grosor === g}
            onClick={() => {
              setGrosor(g);
              setGoma(false);
            }}
            className="flex size-11 cursor-pointer items-center justify-center rounded-lg hover:bg-gray-700 aria-pressed:bg-gray-600"
          >
            <span className="rounded-full bg-gray-100" style={{ width: g + 2, height: g + 2 }} />
          </button>
        ))}
        <span className="mx-1 h-6 w-px bg-gray-700" aria-hidden />
        <button type="button" className={botonHerramienta} onClick={deshacer} aria-label="Deshacer mi último trazo">
          <Undo2 className="size-4" aria-hidden />
        </button>
        {confirmarBorrar ? (
          <span className="flex items-center gap-1 text-sm text-gray-100">
            ¿Borrar todo para los dos?
            <button
              type="button"
              className={`${botonHerramienta} text-red-300`}
              onClick={() => {
                onMensaje({ op: "borrar" });
                setConfirmarBorrar(false);
              }}
            >
              Sí, borrar
            </button>
            <button type="button" className={botonHerramienta} onClick={() => setConfirmarBorrar(false)}>
              No
            </button>
          </span>
        ) : (
          <button type="button" className={botonHerramienta} onClick={() => setConfirmarBorrar(true)} aria-label="Borrar todo">
            <Trash2 className="size-4" aria-hidden />
          </button>
        )}
        <button type="button" className={botonHerramienta} onClick={descargar} aria-label="Descargar como imagen">
          <Download className="size-4" aria-hidden />
        </button>
        <button type="button" className={`${botonHerramienta} ml-auto`} onClick={onCerrar}>
          <X className="size-4" aria-hidden /> Cerrar pizarra
        </button>
      </div>
      <div ref={contenedorRef} className="flex min-h-0 flex-1 items-center justify-center p-2">
        <canvas
          ref={canvasRef}
          aria-label="Tablero de la pizarra"
          className="touch-none rounded-md bg-white shadow"
          style={{ cursor: goma ? "cell" : "crosshair" }}
          onPointerDown={alBajar}
          onPointerMove={alMover}
          onPointerUp={alSoltar}
          onPointerCancel={alSoltar}
        />
      </div>
    </section>
  );
}
