import {
  DEMORA_OCULTAR_MS,
  ESPERA_ENTRE_REINTENTOS_MS,
  INTERVALO_CLASIFICACION_MS,
  REINTENTOS_REACTIVAR,
  SIN_RESPUESTA_MS,
  TAMANO_ENTRADA,
} from "./config";
import { cargarModelo, ClasificadorNoDisponible, type Modelo } from "./modelo";
import { ContadorDetecciones, puntaje } from "./regla";

/** Qué le avisa el cliente al backend sobre su clasificador (POST /clasificador, ADR-M3-07 §6). */
export type EventoClasificador = "NO_CARGA" | "CAIDA" | "REACTIVADO" | "CORTE_SIN_REACTIVAR";

export interface OpcionesClasificador {
  hayMenor: boolean;
  /** Detección confirmada. duenoId = participant.identity (UUID del usuario en Tinku). */
  alDetectar: (d: { trackId: string; duenoId: string }) => void;
  /** Una clasificación lleva más de DEMORA_OCULTAR_MS: con menor, la UI oculta el video remoto. */
  alDemorar: () => void;
  /** Terminó la clasificación demorada: la UI vuelve a mostrar (si no hay caída ni detección). */
  alNormalizar: () => void;
  /** El clasificador dejó de responder: si hay menor, la UI oculta el video remoto ya. */
  alCaer: () => void;
  /** Revivió: la UI vuelve a mostrar el video. */
  alReactivar: () => void;
  /** Se agotaron los reintentos: con menor la UI sale de la sala; entre adultos solo registra. */
  alFallarDefinitivo: () => void;
  /** Inyectable para tests; por defecto, cargarModelo. */
  cargar?: () => Promise<Modelo>;
}

interface Observado {
  duenoId: string;
  video: HTMLVideoElement;
  pausado: boolean;
}

const MAX_MUESTRAS = 200;

/**
 * Ciclo de clasificación del kill-switch (ADR-M3-07 §3–§6): un track remoto por turno, en rotación,
 * sin encolar inferencias; si el modelo falla o se cuelga, avisa a la UI y trata de revivirlo.
 * El frame nunca se guarda: el canvas se pisa en la muestra siguiente.
 */
export class ClasificadorKillswitch {
  private modelo: Modelo;
  private hayMenor: boolean;
  private readonly op: OpcionesClasificador;
  private readonly cargar: () => Promise<Modelo>;
  private readonly contador = new ContadorDetecciones();
  private readonly observados = new Map<string, Observado>();
  private readonly canvas: HTMLCanvasElement;
  private readonly ctx: CanvasRenderingContext2D | null;
  private readonly duraciones: number[] = [];
  private cursor = 0;
  private temporizador: ReturnType<typeof setTimeout> | undefined;
  private enCurso = false;
  private reactivando = false;
  private detenido = false;
  /** Cambia en cada caída: el resultado de una inferencia vieja no cuenta. */
  private epoca = 0;

  private constructor(modelo: Modelo, op: OpcionesClasificador) {
    this.modelo = modelo;
    this.op = op;
    this.hayMenor = op.hayMenor;
    this.cargar = op.cargar ?? (() => cargarModelo());
    this.canvas = document.createElement("canvas");
    this.canvas.width = this.canvas.height = TAMANO_ENTRADA;
    this.ctx = this.canvas.getContext("2d", { willReadFrequently: false });
    this.programar();
  }

  /** Carga el modelo. Rechaza con ClasificadorNoDisponible si no carga. */
  static async crear(op: OpcionesClasificador): Promise<ClasificadorKillswitch> {
    const cargar = op.cargar ?? (() => cargarModelo());
    let modelo: Modelo;
    try {
      modelo = await cargar();
    } catch (e) {
      throw e instanceof ClasificadorNoDisponible
        ? e
        : new ClasificadorNoDisponible(e instanceof Error ? e.message : "El clasificador no cargó");
    }
    return new ClasificadorKillswitch(modelo, op);
  }

  observar(trackId: string, duenoId: string, pista: MediaStreamTrack): void {
    this.dejarDeObservar(trackId);
    // Oculto pero dentro del documento: algunos navegadores (Safari) no reproducen un <video> suelto.
    const video = document.createElement("video");
    video.muted = true;
    video.playsInline = true;
    video.setAttribute("aria-hidden", "true");
    video.style.cssText = "position:fixed;width:1px;height:1px;opacity:0;pointer-events:none;left:0;top:0";
    video.srcObject = new MediaStream([pista]);
    document.body.appendChild(video);
    void video.play().catch(() => {});
    this.observados.set(trackId, { duenoId, video, pausado: false });
  }

  dejarDeObservar(trackId: string): void {
    const o = this.observados.get(trackId);
    if (!o) return;
    this.retirar(o);
    this.observados.delete(trackId);
    this.contador.quitar(trackId);
  }

  /** Después de un "No" en la rama adultos: vuelve a mirar ese track desde cero. */
  reanudar(trackId: string): void {
    const o = this.observados.get(trackId);
    if (o) o.pausado = false;
    this.contador.reiniciar(trackId);
  }

  /** Para de mirar un track con una detección en curso (no lo clasifica de nuevo hasta reanudar). */
  pausar(trackId: string): void {
    const o = this.observados.get(trackId);
    if (o) o.pausado = true;
  }

  /** Cambia el umbral si el token trae un hayMenor distinto. Reinicia los contadores. */
  actualizarHayMenor(hayMenor: boolean): void {
    if (hayMenor === this.hayMenor) return;
    this.hayMenor = hayMenor;
    this.contador.reiniciarTodo();
  }

  destruir(): void {
    this.detenido = true;
    clearTimeout(this.temporizador);
    for (const o of this.observados.values()) this.retirar(o);
    this.observados.clear();
    this.contador.reiniciarTodo();
    try {
      this.modelo.liberar();
    } catch {
      // ya liberado
    }
    // Las pistas NO se paran: son de LiveKit.
  }

  /** Para el diagnóstico (M3C-07): latencias de las últimas 200 inferencias. */
  estadisticas(): { muestras: number; p50: number; p95: number; max: number; backend: string } {
    const orden = [...this.duraciones].sort((a, b) => a - b);
    const en = (q: number) => (orden.length ? orden[Math.min(orden.length - 1, Math.floor(q * orden.length))] : 0);
    return {
      muestras: orden.length,
      p50: en(0.5),
      p95: en(0.95),
      max: orden.length ? orden[orden.length - 1] : 0,
      backend: this.modelo.backend ?? "desconocido",
    };
  }

  // ------------------------------------------------------------ ciclo

  private retirar(o: Observado) {
    o.video.pause();
    o.video.srcObject = null;
    o.video.remove();
  }

  private programar() {
    if (this.detenido) return;
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => void this.turno(), INTERVALO_CLASIFICACION_MS);
  }

  /** Siguiente track no pausado, rotando. */
  private siguiente(): [string, Observado] | null {
    const ids = [...this.observados.keys()];
    for (let i = 0; i < ids.length; i++) {
      const id = ids[(this.cursor + i) % ids.length];
      const o = this.observados.get(id)!;
      if (!o.pausado) {
        this.cursor = (this.cursor + i + 1) % ids.length;
        return [id, o];
      }
    }
    return null;
  }

  private async turno() {
    if (this.detenido || this.reactivando) return;
    const elegido = this.enCurso ? null : this.siguiente();
    // Sin track, sin frame todavía (cámara remota apagada) o inferencia anterior en curso: se saltea.
    if (!elegido || elegido[1].video.readyState < 2 || !elegido[1].video.videoWidth) {
      this.programar();
      return;
    }
    const [trackId, o] = elegido;
    const epoca = this.epoca;
    this.enCurso = true;
    let demorado = false;
    const aviso = setTimeout(() => {
      demorado = true;
      this.op.alDemorar();
    }, DEMORA_OCULTAR_MS);
    const caida = setTimeout(() => void this.caer(), SIN_RESPUESTA_MS);
    const inicio = performance.now();
    try {
      this.ctx?.drawImage(o.video, 0, 0, TAMANO_ENTRADA, TAMANO_ENTRADA);
      const pred = await this.modelo.clasificar(this.canvas);
      if (epoca !== this.epoca) return; // el modelo ya se dio por caído
      clearTimeout(aviso);
      clearTimeout(caida);
      this.enCurso = false;
      this.medir(performance.now() - inicio);
      if (demorado) this.op.alNormalizar();
      if (this.observados.get(trackId) === o && !o.pausado && this.contador.registrar(trackId, puntaje(pred, this.hayMenor))) {
        this.pausar(trackId);
        this.op.alDetectar({ trackId, duenoId: o.duenoId });
      }
      this.programar();
    } catch {
      if (epoca !== this.epoca) return;
      clearTimeout(aviso);
      clearTimeout(caida);
      void this.caer();
    }
  }

  private medir(ms: number) {
    this.duraciones.push(ms);
    if (this.duraciones.length > MAX_MUESTRAS) this.duraciones.shift();
  }

  // ------------------------------------------------------------ caída y reactivación (§6)

  private async caer() {
    if (this.detenido || this.reactivando) return;
    this.reactivando = true;
    this.epoca++;
    this.enCurso = false;
    clearTimeout(this.temporizador);
    this.op.alCaer();
    try {
      this.modelo.liberar();
    } catch {
      // el modelo roto puede no dejarse liberar
    }
    for (let intento = 0; intento < REINTENTOS_REACTIVAR; intento++) {
      await new Promise((r) => setTimeout(r, ESPERA_ENTRE_REINTENTOS_MS));
      if (this.detenido) return;
      try {
        const nuevo = await this.cargar();
        if (this.detenido) {
          nuevo.liberar();
          return;
        }
        this.modelo = nuevo;
        this.contador.reiniciarTodo();
        this.reactivando = false;
        this.op.alReactivar();
        this.programar();
        return;
      } catch {
        // próximo intento
      }
    }
    // Queda detenido; destruir() lo llama la UI.
    this.op.alFallarDefinitivo();
  }
}
