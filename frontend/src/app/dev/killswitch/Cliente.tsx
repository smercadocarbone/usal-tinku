"use client";

import { useEffect } from "react";
import { ClasificadorKillswitch } from "@/lib/killswitch/clasificador";
import { subirEvidencia } from "@/lib/api";
import { BufferEvidencia } from "@/lib/killswitch/bufferEvidencia";
import { cargarModelo, type Modelo } from "@/lib/killswitch/modelo";
import { puntaje, type ClaseNsfw, type Prediccion } from "@/lib/killswitch/regla";

type Carga = { ok: boolean; inferencia?: "porn" | "neutral" | "error" | "colgada" | "lenta" };
type Evento = { tipo: string; t: number };
type Estado = { eventos: Evento[]; ultimaDeteccion: { trackId: string; duenoId: string } | null };

const CLASES: ClaseNsfw[] = ["Drawing", "Hentai", "Neutral", "Porn", "Sexy"];
const pred = (o: Partial<Record<ClaseNsfw, number>>): Prediccion[] =>
  CLASES.map((className) => ({ className, probability: o[className] ?? 0 }));

function modeloFalso(inferencia: NonNullable<Carga["inferencia"]>): Modelo {
  return {
    backend: "falso",
    liberar() {},
    async clasificar() {
      switch (inferencia) {
        case "porn":
          return pred({ Porn: 0.99 });
        case "error":
          throw new Error("inferencia rota (prueba)");
        case "colgada":
          return new Promise<Prediccion[]>(() => {});
        case "lenta":
          await new Promise((r) => setTimeout(r, 5000));
          return pred({ Neutral: 0.99 });
        default:
          return pred({ Neutral: 0.99 });
      }
    },
  };
}

/** Un track de canvas que se repinta solo, como una cámara remota. */
function pistaDeCanvas(ancho = 64, alto = 64): MediaStreamTrack {
  const c = document.createElement("canvas");
  c.width = ancho;
  c.height = alto;
  const g = c.getContext("2d")!;
  let n = 0;
  const pintar = () => {
    g.fillStyle = `hsl(${(n += 7) % 360} 60% 50%)`;
    g.fillRect(0, 0, ancho, alto);
    for (let i = 0; i < 20; i++) {
      g.fillStyle = `hsl(${(n * 3 + i * 17) % 360} 80% 50%)`;
      g.fillRect(Math.random() * ancho, Math.random() * alto, 40, 40);
    }
  };
  pintar();
  setInterval(pintar, 100);
  return c.captureStream(15).getVideoTracks()[0];
}

/** Página de pruebas: Playwright la maneja por `window.__ksIniciar` y lee `window.__ks`. */
export default function Cliente() {
  useEffect(() => {
    const w = window as unknown as Record<string, unknown>;
    const estado: Estado = { eventos: [], ultimaDeteccion: null };
    w.__ks = estado;
    w.__ksIniciar = async (cfg: { hayMenor: boolean; cargas: Carga[] }) => {
      const t0 = Date.now();
      const reg = (tipo: string) => estado.eventos.push({ tipo, t: Date.now() - t0 });
      let n = 0;
      try {
        const k = await ClasificadorKillswitch.crear({
          hayMenor: cfg.hayMenor,
          cargar: async () => {
            const c = cfg.cargas[Math.min(n++, cfg.cargas.length - 1)];
            reg("carga");
            if (!c.ok) throw new Error("no carga (prueba)");
            return modeloFalso(c.inferencia ?? "neutral");
          },
          alDetectar: (d) => {
            estado.ultimaDeteccion = d;
            reg("detectar");
          },
          alDemorar: () => reg("demorar"),
          alNormalizar: () => reg("normalizar"),
          alCaer: () => reg("caer"),
          alReactivar: () => reg("reactivar"),
          alFallarDefinitivo: () => reg("fallar"),
        });
        k.observar("track-1", "dueno-1", pistaDeCanvas());
        w.__ksInstancia = k;
        return { ok: true };
      } catch (e) {
        return { ok: false, nombre: e instanceof Error ? e.name : "desconocido" };
      }
    };
    // M3C-04: buffer de evidencia y subida del clip.
    let buffer: BufferEvidencia | null = null;
    const privado = () => buffer as unknown as { partes: Blob[]; recorder: MediaRecorder };
    w.__ksBuffer = {
      iniciar: () => {
        buffer = BufferEvidencia.crear(pistaDeCanvas(640, 360));
        return buffer !== null;
      },
      crearConAudio: () => {
        const destino = new AudioContext().createMediaStreamDestination();
        return BufferEvidencia.crear(destino.stream.getAudioTracks()[0]);
      },
      congelar: async () => {
        const f = await buffer?.congelar();
        if (!f) return null;
        const inicio = Array.from(new Uint8Array(await f.blob.slice(0, 4).arrayBuffer()));
        return { duracionSegundos: f.duracionSegundos, size: f.blob.size, inicio, tipo: f.blob.type };
      },
      descartar: () => buffer?.descartar(),
      estadoInterno: () => ({ partes: privado().partes.length, recorder: privado().recorder.state }),
    };
    w.__ksSubir = async (sesionId: string, duracion: number) => {
      try {
        await subirEvidencia(sesionId, new Blob([new Uint8Array([0x1a, 0x45, 0xdf, 0xa3, 1, 2, 3])], { type: "video/webm" }), duracion);
        return { ok: true };
      } catch (e) {
        return { ok: false, status: (e as { status?: number }).status ?? 0 };
      }
    };
    /** KS-015: modelo real sobre un canvas de color liso. */
    w.__ksClasificarReal = async () => {
      const modelo = await cargarModelo();
      const c = document.createElement("canvas");
      c.width = c.height = 224;
      const g = c.getContext("2d")!;
      g.fillStyle = "#808080";
      g.fillRect(0, 0, 224, 224);
      const r = await modelo.clasificar(c);
      modelo.liberar();
      return { puntaje: puntaje(r, false), backend: modelo.backend ?? "?" };
    };
  }, []);

  return <main className="p-4 text-sm">Banco de pruebas del kill-switch (solo desarrollo).</main>;
}
