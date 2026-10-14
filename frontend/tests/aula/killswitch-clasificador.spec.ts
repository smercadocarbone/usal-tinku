import { test, expect, type Page } from "@playwright/test";
import { ContadorDetecciones, puntaje, type Prediccion } from "../../src/lib/killswitch/regla";

/**
 * M3C-03 (T-M3-06c, ADR-M3-07): regla de detección (pura, Node) y ciclo del clasificador en el
 * navegador. La página `/dev/killswitch` monta `ClasificadorKillswitch` sobre un track de canvas con
 * un modelo falso configurable; `window.__ks.eventos` registra lo que pasó y cuándo (reloj de la página).
 */

const p = (o: Partial<Record<Prediccion["className"], number>>): Prediccion[] =>
  (["Drawing", "Hentai", "Neutral", "Porn", "Sexy"] as const).map((className) => ({ className, probability: o[className] ?? 0 }));

test.describe("Kill-switch — regla de detección", () => {
  test("con menor suma Porn+Hentai+Sexy", { tag: ["@aula", "@KS-001"] }, () => {
    expect(puntaje(p({ Porn: 0.3, Hentai: 0.2, Sexy: 0.4, Neutral: 0.1 }), true)).toBeCloseTo(0.9, 6);
  });

  test("sin menor ignora Sexy", { tag: ["@aula", "@KS-002"] }, () => {
    expect(puntaje(p({ Sexy: 0.9 }), false)).toBe(0);
    expect(puntaje(p({ Sexy: 0.9 }), true)).toBeCloseTo(0.9, 6);
    expect(puntaje(p({ Porn: 0.5, Hentai: 0.4 }), false)).toBeCloseTo(0.9, 6);
  });

  test("dos sobre el umbral, una debajo y dos sobre: nunca confirma", { tag: ["@aula", "@KS-003"] }, () => {
    const c = new ContadorDetecciones();
    const r = [0.9, 0.9, 0.1, 0.9, 0.9].map((v) => c.registrar("A", v));
    expect(r).toEqual([false, false, false, false, false]);
  });

  test("tres consecutivas confirman una sola vez", { tag: ["@aula", "@KS-004"] }, () => {
    const c = new ContadorDetecciones();
    const r = [0.9, 0.9, 0.9, 0.9].map((v) => c.registrar("A", v));
    expect(r).toEqual([false, false, true, false]);
  });

  test("el contador es por track", { tag: ["@aula", "@KS-005"] }, () => {
    const c = new ContadorDetecciones();
    const r: boolean[] = [];
    for (let i = 0; i < 3; i++) {
      r.push(c.registrar("A", 0.9));
      r.push(c.registrar("B", 0.9));
    }
    expect(r).toEqual([false, false, false, false, true, true]);
  });

  test("reiniciar permite volver a confirmar", { tag: ["@aula", "@KS-006"] }, () => {
    const c = new ContadorDetecciones();
    for (let i = 0; i < 3; i++) c.registrar("A", 0.9);
    expect(c.registrar("A", 0.9)).toBe(false);
    c.reiniciar("A");
    const r = [0.9, 0.9, 0.9].map((v) => c.registrar("A", v));
    expect(r).toEqual([false, false, true]);
  });

  test("el umbral exacto 0,85 cuenta; 0,8499 no", { tag: ["@aula", "@KS-007"] }, () => {
    const a = new ContadorDetecciones();
    expect([0.85, 0.85, 0.85].map((v) => a.registrar("A", v))).toEqual([false, false, true]);
    const b = new ContadorDetecciones();
    expect([0.8499, 0.8499, 0.8499].map((v) => b.registrar("A", v))).toEqual([false, false, false]);
  });
});

type Carga = { ok: boolean; inferencia?: "porn" | "neutral" | "error" | "colgada" | "lenta" };
type Evento = { tipo: string; t: number };

async function abrir(page: Page) {
  await page.goto("/dev/killswitch");
  await page.waitForFunction(() => typeof (window as any).__ksIniciar === "function", null, { polling: 100 });
}

/** Instala el reloj falso, monta el clasificador y deja que el video oculto tenga su primer frame. */
async function iniciar(page: Page, cargas: Carga[], hayMenor = true) {
  await page.clock.install();
  const res = await page.evaluate((c) => (window as any).__ksIniciar({ hayMenor: c.hayMenor, cargas: c.cargas }), { hayMenor, cargas });
  await page.waitForTimeout(1500); // tiempo real: que el <video> reciba su primer frame
  return res as { ok: boolean; nombre?: string };
}

const eventos = (page: Page) => page.evaluate(() => (window as any).__ks.eventos as Evento[]);
const de = (ev: Evento[], tipo: string) => ev.filter((e) => e.tipo === tipo);

test.describe("Kill-switch — clasificador en el navegador", () => {
  test("una detección confirmada avisa una vez con el dueño correcto y deja de mirar el track", { tag: ["@aula", "@KS-010"] }, async ({ page }) => {
    await abrir(page);
    expect((await iniciar(page, [{ ok: true, inferencia: "porn" }])).ok).toBe(true);
    await page.clock.runFor(3000);
    await page.clock.runFor(5000);
    const ev = await eventos(page);
    const det = de(ev, "detectar");
    expect(det).toHaveLength(1);
    expect(await page.evaluate(() => (window as any).__ks.ultimaDeteccion)).toEqual({ trackId: "track-1", duenoId: "dueno-1" });
  });

  test("si el modelo no carga, crear() rechaza con ClasificadorNoDisponible", { tag: ["@aula", "@KS-011"] }, async ({ page }) => {
    await abrir(page);
    const res = await iniciar(page, [{ ok: false }]);
    expect(res).toEqual({ ok: false, nombre: "ClasificadorNoDisponible" });
  });

  test("error en una inferencia y el 2º intento carga: cae y se reactiva", { tag: ["@aula", "@KS-012"] }, async ({ page }) => {
    await abrir(page);
    await iniciar(page, [{ ok: true, inferencia: "error" }, { ok: true, inferencia: "neutral" }]);
    await page.clock.runFor(1000);
    await page.clock.runFor(6000);
    const ev = await eventos(page);
    expect(de(ev, "caer")).toHaveLength(1);
    expect(de(ev, "reactivar")).toHaveLength(1);
    expect(de(ev, "fallar")).toHaveLength(0);
    expect(de(ev, "caer")[0].t).toBeLessThan(de(ev, "reactivar")[0].t);
  });

  test("si no vuelve a cargar: 5 intentos cada ≈5 s y falla definitivo", { tag: ["@aula", "@KS-013"] }, async ({ page }) => {
    await abrir(page);
    await iniciar(page, [{ ok: true, inferencia: "error" }, { ok: false }]);
    await page.clock.runFor(1000);
    await page.clock.runFor(30_000);
    const ev = await eventos(page);
    expect(de(ev, "caer")).toHaveLength(1);
    const intentos = de(ev, "carga").slice(1); // la 1ª es la carga inicial
    expect(intentos).toHaveLength(5);
    for (let i = 1; i < intentos.length; i++) {
      expect(intentos[i].t - intentos[i - 1].t).toBeGreaterThanOrEqual(4900);
      expect(intentos[i].t - intentos[i - 1].t).toBeLessThanOrEqual(5500);
    }
    expect(de(ev, "fallar")).toHaveLength(1);
    expect(de(ev, "reactivar")).toHaveLength(0);
  });

  test("inferencia que nunca termina: demora a los 3 s y cae a los 10 s", { tag: ["@aula", "@KS-014"] }, async ({ page }) => {
    await abrir(page);
    await iniciar(page, [{ ok: true, inferencia: "colgada" }]);
    await page.clock.runFor(1000);
    await page.clock.runFor(12_000);
    const ev = await eventos(page);
    const demorar = de(ev, "demorar");
    const caer = de(ev, "caer");
    expect(demorar).toHaveLength(1);
    expect(caer).toHaveLength(1);
    expect(caer[0].t - demorar[0].t).toBeGreaterThanOrEqual(6800);
    expect(caer[0].t - demorar[0].t).toBeLessThanOrEqual(7300);
  });

  test("inferencia lenta (5 s): demora, se normaliza al terminar y no cae", { tag: ["@aula", "@KS-016"] }, async ({ page }) => {
    await abrir(page);
    await iniciar(page, [{ ok: true, inferencia: "lenta" }]);
    await page.clock.runFor(1000);
    await page.clock.runFor(7000);
    const ev = await eventos(page);
    // Con 8 s de ventana puede empezar una segunda inferencia lenta: importa el orden de la primera.
    expect(de(ev, "demorar").length).toBeGreaterThanOrEqual(1);
    expect(de(ev, "normalizar").length).toBeGreaterThanOrEqual(1);
    expect(de(ev, "demorar")[0].t).toBeLessThan(de(ev, "normalizar")[0].t);
    expect(de(ev, "caer")).toHaveLength(0);
  });

  test("modelo real: carga desde /modelos y clasifica un canvas liso sin pasar el umbral", { tag: ["@aula", "@KS-015"] }, async ({ page }) => {
    test.setTimeout(60_000);
    await abrir(page);
    const r = await page.evaluate(() => (window as any).__ksClasificarReal() as Promise<{ puntaje: number; backend: string }>);
    expect(r.puntaje).toBeLessThan(0.85);
  });
});
