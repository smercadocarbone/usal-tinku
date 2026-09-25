/**
 * ADR-M3-04 (T08): grabación de SOLO audio de la clase para el resumen, en el navegador del
 * Tutor. Mezcla el micrófono propio y el audio remoto con Web Audio y graba Opus a 16 kbps
 * (180 min ≈ 21,6 MB, bajo el límite de 25 MB de la transcripción). Nunca toca video.
 * Solo se crea cuando el backend lo indica en el token (`grabarAudioResumen`).
 */
export class GrabadorAudioResumen {
  private readonly ctx: AudioContext;
  private readonly destino: MediaStreamAudioDestinationNode;
  private readonly recorder: MediaRecorder;
  private readonly partes: Blob[] = [];
  private readonly fuentes = new Map<string, MediaStreamAudioSourceNode>();

  private constructor() {
    this.ctx = new AudioContext();
    this.destino = this.ctx.createMediaStreamDestination();
    const tipo = ["audio/webm;codecs=opus", "audio/ogg;codecs=opus", "audio/webm"].find((t) =>
      MediaRecorder.isTypeSupported(t)
    );
    this.recorder = new MediaRecorder(this.destino.stream, {
      ...(tipo ? { mimeType: tipo } : {}),
      audioBitsPerSecond: 16_000,
    });
    this.recorder.ondataavailable = (e) => {
      if (e.data.size > 0) this.partes.push(e.data);
    };
    // Partes cada 10 s: si la pestaña se cuelga, lo ya grabado queda en memoria.
    this.recorder.start(10_000);
  }

  /** `null` si el navegador no puede grabar audio (sin MediaRecorder o Web Audio). */
  static crear(): GrabadorAudioResumen | null {
    if (typeof window === "undefined" || typeof MediaRecorder === "undefined" || typeof AudioContext === "undefined") {
      return null;
    }
    try {
      return new GrabadorAudioResumen();
    } catch {
      return null;
    }
  }

  agregarPista(id: string, pista: MediaStreamTrack) {
    if (pista.kind !== "audio") return;
    this.quitarPista(id);
    const fuente = this.ctx.createMediaStreamSource(new MediaStream([pista]));
    fuente.connect(this.destino);
    this.fuentes.set(id, fuente);
    void this.ctx.resume();
  }

  quitarPista(id: string) {
    this.fuentes.get(id)?.disconnect();
    this.fuentes.delete(id);
  }

  get grabando(): boolean {
    return this.recorder.state === "recording";
  }

  /** Corta la grabación y devuelve el archivo (o `null` si no se grabó nada). */
  detener(): Promise<Blob | null> {
    return new Promise((resolve) => {
      const terminar = () => {
        this.fuentes.forEach((f) => f.disconnect());
        this.fuentes.clear();
        void this.ctx.close();
        const blob = new Blob(this.partes, { type: this.recorder.mimeType || "audio/webm" });
        resolve(blob.size > 0 ? blob : null);
      };
      if (this.recorder.state === "inactive") {
        terminar();
        return;
      }
      this.recorder.onstop = terminar;
      this.recorder.stop();
    });
  }
}
