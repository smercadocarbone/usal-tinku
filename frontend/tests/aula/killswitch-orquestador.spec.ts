import { test, expect } from "@playwright/test";
import { crearOrquestador, type AvisoKillswitch, type DepsOrquestador } from "../../src/lib/killswitch/orquestador";

/**
 * M3C-05 (T-M3-06e, ADR-M3-07): decisiones del kill-switch en el aula, puras, con dependencias
 * falsas que registran cada llamada en orden. `esperar` resuelve al instante.
 */

type Ajustes = {
  hayMenor?: boolean;
  disparar?: () => Promise<{ estado: string }>;
  confirmar?: (vio: boolean) => Promise<{ estado: string }>;
  clip?: { blob: Blob; duracionSegundos: number } | null;
};

function montar(a: Ajustes = {}) {
  const log: string[] = [];
  const payloads: unknown[] = [];
  let hayMenor = a.hayMenor ?? true;
  const deps: DepsOrquestador = {
    sesionId: "s1",
    hayMenor: () => hayMenor,
    api: {
      disparar: async (id) => {
        log.push(`disparar:${id}`);
        payloads.push({ detectadoId: id });
        return (a.disparar ?? (async () => ({ estado: "finalizada" })))();
      },
      confirmar: async (vio) => {
        log.push(`confirmar:${vio}`);
        return (a.confirmar ?? (async () => ({ estado: vio ? "finalizada" : "en_curso" })))(vio);
      },
      subirEvidencia: async (_c, d) => void log.push(`subirEvidencia:${d}`),
      reportar: async (e) => void log.push(`reportar:${e}`),
    },
    congelarClip: (t) => {
      log.push(`congelarClip:${t}`);
      return a.clip === undefined ? { blob: new Blob(["x"]), duracionSegundos: 12 } : a.clip;
    },
    sala: {
      ocultarTrack: (t) => void log.push(`ocultarTrack:${t}`),
      mostrarTrack: (t) => void log.push(`mostrarTrack:${t}`),
      ocultarTodoRemoto: () => void log.push("ocultarTodoRemoto"),
      mostrarTodoRemoto: () => void log.push("mostrarTodoRemoto"),
      enviarAviso: (m: AvisoKillswitch) => void log.push(`aviso:${m.tipo}`),
      salir: () => void log.push("salir"),
    },
    clasificador: {
      pausar: (t) => void log.push(`pausar:${t}`),
      reanudar: (t) => void log.push(`reanudar:${t}`),
      destruir: () => void log.push("destruir"),
    },
    ui: {
      preguntar: (p) => void log.push(`preguntar:${p.trackId}:${p.detectadoId}`),
      cerrarPregunta: () => void log.push("cerrarPregunta"),
      cierre: (m) => void log.push(`cierre:${m}`),
      aviso: (t) => void log.push(`ui.aviso:${t}`),
    },
    esperar: async () => {},
    ahora: () => 1_000,
  };
  return { orq: crearOrquestador(deps), log, payloads, setMenor: (v: boolean) => (hayMenor = v) };
}

const det = { trackId: "t1", duenoId: "d1" };
const falla = (status?: number) => async (): Promise<{ estado: string }> => {
  throw Object.assign(new Error("falla"), status ? { status } : {});
};
const cuenta = (log: string[], pref: string) => log.filter((l) => l.startsWith(pref)).length;

test.describe("Kill-switch — orquestador", () => {
  test("rama menor: oculta antes de disparar, sube el clip, cierra y sale; nunca pregunta", { tag: ["@aula", "@KS-030"] }, async () => {
    const { orq, log } = montar();
    await orq.alDetectar(det);
    expect(log[0]).toBe("ocultarTrack:t1");
    expect(log.indexOf("ocultarTrack:t1")).toBeLessThan(log.indexOf("disparar:d1"));
    expect(log).toContain("aviso:corte-seguridad");
    const i = (s: string) => log.indexOf(s);
    expect(i("subirEvidencia:12")).toBeGreaterThan(i("disparar:d1"));
    expect(i("cierre:seguridad")).toBeGreaterThan(i("subirEvidencia:12"));
    expect(i("salir")).toBeGreaterThan(i("cierre:seguridad"));
    expect(log).toContain("destruir");
    expect(cuenta(log, "preguntar")).toBe(0);
  });

  test("rama menor con el disparo caído: 3 intentos, cierra y sale sin subir clip", { tag: ["@aula", "@KS-031"] }, async () => {
    const { orq, log } = montar({ disparar: falla() });
    await orq.alDetectar(det);
    expect(cuenta(log, "disparar")).toBe(3);
    expect(log).toContain("cierre:seguridad");
    expect(log).toContain("salir");
    expect(cuenta(log, "subirEvidencia")).toBe(0);
  });

  test("rama adultos + No: pregunta, confirma(false), restaura", { tag: ["@aula", "@KS-032"] }, async () => {
    const { orq, log } = montar({ hayMenor: false, disparar: async () => ({ estado: "en_curso" }) });
    await orq.alDetectar(det);
    expect(log).toContain("aviso:video-oculto");
    expect(log).toContain("preguntar:t1:d1");
    await orq.responder(false);
    expect(log).toContain("confirmar:false");
    expect(log).toContain("mostrarTrack:t1");
    expect(log).toContain("reanudar:t1");
    expect(cuenta(log, "subirEvidencia")).toBe(0);
    expect(cuenta(log, "salir")).toBe(0);
  });

  test("rama adultos + Sí: confirma(true), sube el clip, cierra y sale", { tag: ["@aula", "@KS-033"] }, async () => {
    const { orq, log } = montar({ hayMenor: false, disparar: async () => ({ estado: "en_curso" }) });
    await orq.alDetectar(det);
    await orq.responder(true);
    expect(log).toContain("confirmar:true");
    expect(log.indexOf("subirEvidencia:12")).toBeGreaterThan(log.indexOf("confirmar:true"));
    expect(log).toContain("cierre:seguridad");
    expect(log).toContain("salir");
  });

  test("rama adultos: si vence la pregunta restaura sin llamar a confirmar", { tag: ["@aula", "@KS-034"] }, async () => {
    const { orq, log } = montar({ hayMenor: false, disparar: async () => ({ estado: "en_curso" }) });
    await orq.alDetectar(det);
    orq.vencioPregunta();
    expect(log).toContain("cerrarPregunta");
    expect(log).toContain("mostrarTrack:t1");
    expect(log).toContain("reanudar:t1");
    expect(cuenta(log, "confirmar")).toBe(0);
  });

  test("rama adultos: confirmar da 422 → restaura", { tag: ["@aula", "@KS-035"] }, async () => {
    const { orq, log } = montar({ hayMenor: false, disparar: async () => ({ estado: "en_curso" }), confirmar: falla(422) });
    await orq.alDetectar(det);
    await orq.responder(true);
    expect(log).toContain("mostrarTrack:t1");
    expect(cuenta(log, "salir")).toBe(0);
  });

  test("adultos con el disparo caído: pregunta local; Sí sale y nunca llama a confirmar", { tag: ["@aula", "@KS-036"] }, async () => {
    const { orq, log } = montar({ hayMenor: false, disparar: falla() });
    await orq.alDetectar(det);
    expect(cuenta(log, "disparar")).toBe(3);
    expect(log).toContain("preguntar:t1:d1");
    await orq.responder(true);
    expect(log).toContain("salir");
    expect(cuenta(log, "confirmar")).toBe(0);
  });

  test("dos detecciones seguidas: un solo disparo", { tag: ["@aula", "@KS-037"] }, async () => {
    const { orq, log } = montar({ hayMenor: false, disparar: async () => ({ estado: "en_curso" }) });
    await Promise.all([orq.alDetectar(det), orq.alDetectar({ trackId: "t2", duenoId: "d1" })]);
    expect(cuenta(log, "disparar")).toBe(1);
  });

  test("alCaer con menor: reporta y oculta todo el remoto", { tag: ["@aula", "@KS-038"] }, () => {
    const { orq, log } = montar();
    orq.alCaer();
    expect(log).toContain("reportar:CAIDA");
    expect(log).toContain("ocultarTodoRemoto");
  });

  test("alCaer sin menor: solo reporta", { tag: ["@aula", "@KS-039"] }, () => {
    const { orq, log } = montar({ hayMenor: false });
    orq.alCaer();
    expect(log).toEqual(["reportar:CAIDA"]);
  });

  test("alFallarDefinitivo con menor: cierre sin-proteccion y salir", { tag: ["@aula", "@KS-040"] }, () => {
    const { orq, log } = montar();
    orq.alFallarDefinitivo();
    expect(log).toContain("reportar:CORTE_SIN_REACTIVAR");
    expect(log).toContain("cierre:sin-proteccion");
    expect(log).toContain("salir");
  });

  test("alFallarDefinitivo sin menor: solo reporta", { tag: ["@aula", "@KS-041"] }, () => {
    const { orq, log } = montar({ hayMenor: false });
    orq.alFallarDefinitivo();
    expect(log).toEqual(["reportar:CORTE_SIN_REACTIVAR"]);
  });

  test("alReactivar con menor: vuelve a mostrar", { tag: ["@aula", "@KS-042"] }, () => {
    const { orq, log } = montar();
    orq.alReactivar();
    expect(log).toContain("reportar:REACTIVADO");
    expect(log).toContain("mostrarTodoRemoto");
  });

  test("aviso corte-seguridad y después desconexión: cierre y true", { tag: ["@aula", "@KS-043"] }, () => {
    const { orq, log } = montar();
    orq.alRecibirAviso({ tipo: "corte-seguridad" });
    expect(orq.alDesconectar()).toBe(true);
    expect(log).toContain("cierre:seguridad");
  });

  test("nada de lo que se manda al disparar lleva rama", { tag: ["@aula", "@KS-044"] }, async () => {
    const { orq, payloads } = montar();
    await orq.alDetectar(det);
    expect(JSON.stringify(payloads).toLowerCase()).not.toContain("rama");
  });

  test("alDemorar con menor oculta y alNormalizar muestra; sin reportar", { tag: ["@aula", "@KS-045"] }, () => {
    const { orq, log } = montar();
    orq.alDemorar();
    orq.alNormalizar();
    expect(log.filter((l) => !l.startsWith("ui.aviso"))).toEqual(["ocultarTodoRemoto", "mostrarTodoRemoto"]);
  });

  test("alDemorar sin menor no hace nada", { tag: ["@aula", "@KS-046"] }, () => {
    const { orq, log } = montar({ hayMenor: false });
    orq.alDemorar();
    expect(log).toEqual([]);
  });

  test("demora, caída y normalización: sigue oculto porque la caída manda", { tag: ["@aula", "@KS-047"] }, () => {
    const { orq, log } = montar();
    orq.alDemorar();
    orq.alCaer();
    orq.alNormalizar();
    expect(cuenta(log, "mostrarTodoRemoto")).toBe(0);
  });

  test("sin aviso de corte, alDesconectar devuelve false", { tag: ["@aula", "@KS-048"] }, () => {
    const { orq, log } = montar();
    expect(orq.alDesconectar()).toBe(false);
    expect(log).toEqual([]);
  });
});
