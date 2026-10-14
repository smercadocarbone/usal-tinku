import { CARGA_MAXIMA_MS, TAMANO_ENTRADA, URL_MODELO, URL_WASM } from "./config";
import type { Prediccion } from "./regla";

/** Único archivo que importa TF.js / NSFWJS (con import() dinámico: no pesa en las demás páginas). */
export interface Modelo {
  clasificar(fuente: HTMLCanvasElement): Promise<Prediccion[]>;
  liberar(): void;
  /** Backend de TF.js que quedó activo ("webgl" | "wasm"); informativo (M3C-07). */
  backend?: string;
}

export class ClasificadorNoDisponible extends Error {
  constructor(motivo: string) {
    super(motivo);
    this.name = "ClasificadorNoDisponible";
  }
}

/**
 * Carga TF.js y NSFWJS, hace una inferencia de calentamiento y resuelve. Todo bajo un tope de
 * `CARGA_MAXIMA_MS`; si vence o algo falla, rechaza con `ClasificadorNoDisponible`.
 * `opciones.backend` fuerza el backend (solo la página de diagnóstico; el aula no lo pasa).
 */
export async function cargarModelo(opciones?: { backend?: "webgl" | "wasm" }): Promise<Modelo> {
  // Solo existe fuera de producción: Next reemplaza NODE_ENV y elimina este bloque del build.
  if (process.env.NODE_ENV !== "production" && typeof window !== "undefined") {
    const falso = (window as unknown as { __tinkuModeloFalso?: Modelo }).__tinkuModeloFalso;
    if (falso) return falso;
  }

  let vencido = false;
  let liberarTardio: (() => void) | null = null;
  let temporizador: ReturnType<typeof setTimeout> | undefined;
  const tope = new Promise<never>((_, rechazar) => {
    temporizador = setTimeout(() => {
      vencido = true;
      rechazar(new ClasificadorNoDisponible(`El clasificador no cargó en ${CARGA_MAXIMA_MS} ms`));
    }, CARGA_MAXIMA_MS);
  });

  try {
    return await Promise.race([
      cargar(opciones?.backend).then((m) => {
        // Si llegó después del tope nadie lo va a usar: se libera para no dejar memoria colgada.
        if (vencido) liberarTardio = () => m.liberar();
        liberarTardio?.();
        return m;
      }),
      tope,
    ]);
  } catch (e) {
    throw e instanceof ClasificadorNoDisponible
      ? e
      : new ClasificadorNoDisponible(e instanceof Error ? e.message : "Error al cargar el clasificador");
  } finally {
    clearTimeout(temporizador);
  }
}

async function cargar(forzado?: "webgl" | "wasm"): Promise<Modelo> {
  const tf = await import("@tensorflow/tfjs");
  const wasm = await import("@tensorflow/tfjs-backend-wasm");
  const nsfwjs = await import("nsfwjs");

  const usarWasm = async () => {
    wasm.setWasmPaths(URL_WASM);
    return tf.setBackend("wasm");
  };
  if (forzado === "wasm") {
    await usarWasm();
  } else {
    let ok = false;
    try {
      ok = await tf.setBackend("webgl");
    } catch {
      ok = false;
    }
    if (!ok) {
      if (forzado === "webgl") throw new Error("WebGL no disponible");
      await usarWasm();
    }
  }
  await tf.ready();

  const m = await nsfwjs.load(URL_MODELO, { size: TAMANO_ENTRADA });
  const calentamiento = document.createElement("canvas");
  calentamiento.width = calentamiento.height = TAMANO_ENTRADA;
  calentamiento.getContext("2d")?.fillRect(0, 0, TAMANO_ENTRADA, TAMANO_ENTRADA);
  await m.classify(calentamiento);

  return {
    backend: tf.getBackend(),
    clasificar: async (fuente) => (await m.classify(fuente)) as Prediccion[],
    liberar: () => m.dispose(),
  };
}
