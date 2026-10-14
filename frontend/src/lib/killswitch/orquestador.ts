import type { EventoClasificador } from "../api";
import { ESPERA_ENTRE_DISPAROS_MS, PLAZO_RAMA_ADULTOS_MS, REINTENTOS_DISPARO } from "./config";

/**
 * Decisiones del kill-switch en el aula (ADR-M3-07, US-6/US-6bis/US-7), sin LiveKit ni DOM: todo
 * lo que toca el mundo entra por `DepsOrquestador`. La RAMA la decide siempre el backend (el cliente
 * nunca la manda); acá solo se decide qué mostrar y cuándo salir.
 */
export type AvisoKillswitch = { tipo: "video-oculto" } | { tipo: "corte-seguridad" };

type Clip = { blob: Blob; duracionSegundos: number };

export interface DepsOrquestador {
  sesionId: string;
  /** Fuente: token o estado; ante la duda, true (AGENTS §3). */
  hayMenor: () => boolean;
  api: {
    disparar(detectadoId: string): Promise<{ estado: string }>;
    confirmar(vio: boolean): Promise<{ estado: string }>;
    subirEvidencia(clip: Blob, duracionSegundos: number): Promise<void>;
    reportar(evento: EventoClasificador): Promise<void>;
  };
  /** Congela el buffer del track. Puede ser asíncrono (esperar el último dato del grabador). */
  congelarClip(trackId: string): Promise<Clip | null> | Clip | null;
  sala: {
    ocultarTrack(trackId: string): void;
    mostrarTrack(trackId: string): void;
    ocultarTodoRemoto(): void;
    mostrarTodoRemoto(): void;
    enviarAviso(m: AvisoKillswitch): void;
    salir(): void;
  };
  clasificador: { pausar(trackId: string): void; reanudar(trackId: string): void; destruir(): void };
  ui: {
    preguntar(p: { trackId: string; detectadoId: string; venceEn: number }): void;
    cerrarPregunta(): void;
    cierre(motivo: "seguridad" | "sin-proteccion"): void;
    aviso(texto: string): void;
  };
  esperar: (ms: number) => Promise<void>;
  ahora: () => number;
}

export function crearOrquestador(deps: DepsOrquestador) {
  const { api, sala, ui, clasificador } = deps;
  let enDeteccion = false;
  let trackId: string | null = null;
  let clip: Promise<Clip | null> | null = null;
  let preguntaLocal = false;
  let caido = false;
  let cierreMostrado = false;
  let corteSeguridadRecibido = false;

  const cerrar = (motivo: "seguridad" | "sin-proteccion") => {
    cierreMostrado = true;
    clasificador.destruir();
    ui.cierre(motivo);
    sala.salir();
  };

  /** Nunca bloquea el cierre: el clip se pierde si falla (los reintentos ya están en la API). */
  const subirClip = async () => {
    try {
      const c = await clip;
      if (c) await api.subirEvidencia(c.blob, c.duracionSegundos);
    } catch {
      console.error("No se pudo subir el clip de evidencia");
    }
    clip = null;
  };

  const restaurar = () => {
    if (trackId) {
      sala.mostrarTrack(trackId);
      clasificador.reanudar(trackId);
    }
    clip = null; // el clip se descarta
    trackId = null;
    preguntaLocal = false;
    enDeteccion = false;
  };

  const disparar = async (duenoId: string): Promise<{ estado: string } | null> => {
    for (let intento = 1; intento <= REINTENTOS_DISPARO; intento++) {
      try {
        return await api.disparar(duenoId);
      } catch {
        if (intento < REINTENTOS_DISPARO) await deps.esperar(ESPERA_ENTRE_DISPAROS_MS);
      }
    }
    return null;
  };

  const preguntar = (duenoId: string) =>
    ui.preguntar({ trackId: trackId!, detectadoId: duenoId, venceEn: deps.ahora() + PLAZO_RAMA_ADULTOS_MS });

  return {
    async alDetectar(d: { trackId: string; duenoId: string }): Promise<void> {
      if (enDeteccion) return;
      enDeteccion = true;
      trackId = d.trackId;
      // Ocultar no espera al backend ni a ningún await.
      sala.ocultarTrack(d.trackId);
      clip = Promise.resolve(deps.congelarClip(d.trackId)).catch(() => null);
      if (deps.hayMenor()) sala.enviarAviso({ tipo: "corte-seguridad" }); // best-effort

      const res = await disparar(d.duenoId);
      if (res === null) {
        if (deps.hayMenor()) {
          // Sin Alerta no hay dónde subir el clip. Fail-closed: no se sigue viendo al otro.
          clip = null;
          cerrar("seguridad");
        } else {
          preguntaLocal = true;
          preguntar(d.duenoId);
        }
      } else if (res.estado === "en_curso" || res.estado === "no_iniciada") {
        sala.enviarAviso({ tipo: "video-oculto" });
        preguntar(d.duenoId);
      } else {
        if (res.estado === "finalizada") await subirClip();
        cerrar("seguridad");
      }
    },

    async responder(vio: boolean): Promise<void> {
      ui.cerrarPregunta();
      let local = preguntaLocal;
      if (!local) {
        try {
          const r = await api.confirmar(vio);
          if (vio && r.estado === "finalizada") {
            await subirClip();
            cerrar("seguridad");
            return;
          }
          restaurar();
          return;
        } catch (e) {
          // 422: vencida o ya respondida → restaurar. Otro error: no hay respuesta del backend,
          // se resuelve como una pregunta local (quien vio algo sale; quien no, sigue).
          if ((e as { status?: number }).status === 422) {
            restaurar();
            return;
          }
          local = true;
        }
      }
      if (local && vio) {
        clip = null;
        cerrar("seguridad");
        return;
      }
      restaurar();
    },

    /** El plazo ya lo resuelve el job del backend como "No": acá solo se restaura. */
    vencioPregunta(): void {
      ui.cerrarPregunta();
      restaurar();
    },

    alDemorar(): void {
      if (!deps.hayMenor()) return;
      sala.ocultarTodoRemoto();
      ui.aviso("Verificando la protección del aula…");
    },

    alNormalizar(): void {
      if (deps.hayMenor() && !caido) sala.mostrarTodoRemoto();
    },

    alCaer(): void {
      caido = true;
      void api.reportar("CAIDA").catch(() => {});
      if (deps.hayMenor()) {
        sala.ocultarTodoRemoto();
        ui.aviso("Reactivando la protección del aula…");
      }
    },

    alReactivar(): void {
      caido = false;
      void api.reportar("REACTIVADO").catch(() => {});
      if (deps.hayMenor()) sala.mostrarTodoRemoto();
    },

    alFallarDefinitivo(): void {
      void api.reportar("CORTE_SIN_REACTIVAR").catch(() => {});
      if (deps.hayMenor()) cerrar("sin-proteccion");
    },

    /** El aviso del otro participante nunca decide nada: el estado de verdad está en el backend. */
    alRecibirAviso(m: AvisoKillswitch): void {
      if (m.tipo === "video-oculto") ui.aviso("Tu video se ocultó un momento para el otro participante.");
      else corteSeguridadRecibido = true;
    },

    /** true si el orquestador ya mostró un cierre y la página no debe mostrar el suyo. */
    alDesconectar(): boolean {
      if (cierreMostrado) return true;
      if (corteSeguridadRecibido) {
        cierreMostrado = true;
        ui.cierre("seguridad");
        return true;
      }
      return false;
    },
  };
}
