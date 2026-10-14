import { BUFFER_SEGUNDOS, CLIP_BITS_POR_SEGUNDO, CLIP_MAX_BYTES } from "./config";

const TIPOS = ["video/webm;codecs=vp8", "video/webm;codecs=vp9", "video/webm", "video/mp4"];

/**
 * ADR-M3-07 §5 / Artículo V: buffer rotativo de los últimos 30 s del video remoto, SOLO video,
 * SOLO en memoria. Se descarta si no hay disparo (FR-AULA-004, BR-KS-01); no usa ningún almacenamiento
 * del navegador (ni bases locales, ni storage, ni caché).
 *
 * Se guarda la primera parte (trae el encabezado del contenedor) y las últimas 29. Al sacar partes
 * del medio, el clip puede arrancar sin keyframe y los primeros cuadros verse mal (spike B de
 * ADR-M3-01): se acepta, lo importante es que quien revisa vea el final.
 */
export class BufferEvidencia {
  private cabecera: Blob | null = null;
  private partes: Blob[] = [];
  private congelado = false;
  private resultado: { blob: Blob; duracionSegundos: number } | null = null;
  private readonly recorder: MediaRecorder;

  private constructor(pista: MediaStreamTrack, private readonly mimeType: string) {
    this.recorder = new MediaRecorder(new MediaStream([pista]), {
      mimeType,
      videoBitsPerSecond: CLIP_BITS_POR_SEGUNDO,
    });
    this.recorder.ondataavailable = (e) => {
      if (this.congelado || e.data.size === 0) return;
      if (!this.cabecera) {
        this.cabecera = e.data;
        return;
      }
      this.partes.push(e.data);
      while (this.partes.length > BUFFER_SEGUNDOS - 1) this.partes.shift();
    };
    this.recorder.start(1000);
  }

  /** null si la pista no es de video, no hay MediaRecorder o ningún tipo de video está soportado. */
  static crear(pista: MediaStreamTrack): BufferEvidencia | null {
    if (pista.kind !== "video" || typeof MediaRecorder === "undefined") return null;
    const tipo = TIPOS.find((t) => MediaRecorder.isTypeSupported(t));
    if (!tipo) return null;
    try {
      return new BufferEvidencia(pista, tipo);
    } catch {
      return null;
    }
  }

  /** Foto del buffer al momento de la detección. null si todavía no hay partes. Después no graba más. */
  async congelar(): Promise<{ blob: Blob; duracionSegundos: number } | null> {
    if (this.resultado || this.congelado) return this.resultado;
    if (this.recorder.state !== "inactive") {
      await new Promise<void>((resolver) => {
        this.recorder.addEventListener("stop", () => resolver(), { once: true });
        this.recorder.stop(); // el último dataavailable llega antes del stop
      });
    }
    this.congelado = true;
    if (!this.cabecera) return null;
    const partes = [...this.partes];
    let bytes = this.cabecera.size + partes.reduce((n, p) => n + p.size, 0);
    while (bytes > CLIP_MAX_BYTES && partes.length > 0) bytes -= partes.shift()!.size;
    this.resultado = {
      blob: new Blob([this.cabecera, ...partes], { type: this.mimeType }),
      duracionSegundos: Math.min(BUFFER_SEGUNDOS, 1 + partes.length),
    };
    return this.resultado;
  }

  /** Libera todo. Idempotente. No para la pista: es de LiveKit. */
  descartar(): void {
    this.congelado = true;
    if (this.recorder.state !== "inactive") {
      try {
        this.recorder.stop();
      } catch {
        // ya detenido
      }
    }
    this.recorder.ondataavailable = null;
    this.cabecera = null;
    this.partes = [];
    this.resultado = null;
  }
}
