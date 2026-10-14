import { test, expect, type Page } from "@playwright/test";

/**
 * M3C-04 (T-M3-06d, ADR-M3-07 §5): buffer rotativo de 30 s solo video, en memoria, y subida del
 * clip. La página `/dev/killswitch` expone `window.__ksBuffer` y `window.__ksSubir`.
 */

async function abrir(page: Page) {
  await page.goto("/dev/killswitch");
  await page.waitForFunction(() => typeof (window as any).__ksBuffer === "object", null, { polling: 100, timeout: 8000 });
}

type Foto = { duracionSegundos: number; size: number; inicio: number[]; tipo: string } | null;

test.describe("Kill-switch — buffer de evidencia (45 s de grabación real)", () => {
  test.describe.configure({ mode: "serial" });
  let page: Page;
  let foto: Foto;

  test.beforeAll(async ({ browser }) => {
    test.setTimeout(120_000);
    page = await browser.newPage();
    await abrir(page);
    expect(await page.evaluate(() => (window as any).__ksBuffer.iniciar())).toBe(true);
    await page.waitForTimeout(45_000);
    foto = await page.evaluate(() => (window as any).__ksBuffer.congelar());
  });

  test.afterAll(async () => {
    await page.evaluate(() => (window as any).__ksBuffer.descartar());
    await page.close();
  });

  test("después de 45 s el clip dura ≤ 30 s y es WebM", { tag: ["@aula", "@KS-020"] }, () => {
    expect(foto).not.toBeNull();
    expect(foto!.duracionSegundos).toBeLessThanOrEqual(30);
    expect(foto!.duracionSegundos).toBeGreaterThanOrEqual(25);
    expect(foto!.inicio).toEqual([0x1a, 0x45, 0xdf, 0xa3]);
  });

  test("el clip congelado pesa ≤ 5 MB", { tag: ["@aula", "@KS-021"] }, () => {
    expect(foto!.size).toBeGreaterThan(0);
    expect(foto!.size).toBeLessThanOrEqual(5 * 1024 * 1024);
  });

  test("tras congelar no llegan más partes", { tag: ["@aula", "@KS-022"] }, async () => {
    const antes = await page.evaluate(() => (window as any).__ksBuffer.estadoInterno());
    await page.waitForTimeout(3000);
    const despues = await page.evaluate(() => (window as any).__ksBuffer.estadoInterno());
    expect(antes.recorder).toBe("inactive");
    expect(despues).toEqual(antes);
  });
});

test.describe("Kill-switch — buffer y subida", () => {
  test("descartar() dos veces no tira error", { tag: ["@aula", "@KS-023"] }, async ({ page }) => {
    await abrir(page);
    await page.evaluate(() => (window as any).__ksBuffer.iniciar());
    const r = await page.evaluate(() => {
      const b = (window as any).__ksBuffer;
      try {
        b.descartar();
        b.descartar();
        return "ok";
      } catch (e) {
        return String(e);
      }
    });
    expect(r).toBe("ok");
  });

  test("subirEvidencia manda multipart con 'clip', duracionSegundos en la query y sin rama", { tag: ["@aula", "@KS-024"] }, async ({ page }) => {
    await abrir(page);
    const pedidos: { url: string; tipo: string; cuerpo: string }[] = [];
    await page.route("**/api/sesiones/*/evidencia**", async (route) => {
      const req = route.request();
      pedidos.push({ url: req.url(), tipo: req.headers()["content-type"] ?? "", cuerpo: req.postDataBuffer()?.toString("latin1") ?? "" });
      await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
    });
    const r = await page.evaluate(() => (window as any).__ksSubir("sesion-1", 12));
    expect(r.ok).toBe(true);
    expect(pedidos).toHaveLength(1);
    expect(pedidos[0].url).toContain("/api/sesiones/sesion-1/evidencia?duracionSegundos=12");
    expect(pedidos[0].tipo).toContain("multipart/form-data");
    expect(pedidos[0].cuerpo).toContain('name="clip"');
    expect(pedidos[0].cuerpo.toLowerCase()).not.toContain("rama");
    expect(pedidos[0].url.toLowerCase()).not.toContain("rama");
  });

  test("ante un 503 reintenta hasta 3 intentos; ante un 422 no reintenta", { tag: ["@aula", "@KS-025"] }, async ({ page }) => {
    test.setTimeout(30_000);
    await abrir(page);
    let n = 0;
    await page.route("**/api/sesiones/*/evidencia**", async (route) => {
      n++;
      await route.fulfill({ status: 503, contentType: "application/json", body: '{"error":"caído"}' });
    });
    const r503 = await page.evaluate(() => (window as any).__ksSubir("sesion-1", 5));
    expect(r503.ok).toBe(false);
    expect(n).toBe(3);

    await page.unroute("**/api/sesiones/*/evidencia**");
    let m = 0;
    await page.route("**/api/sesiones/*/evidencia**", async (route) => {
      m++;
      await route.fulfill({ status: 422, contentType: "application/json", body: '{"error":"inválido"}' });
    });
    const r422 = await page.evaluate(() => (window as any).__ksSubir("sesion-1", 5));
    expect(r422.ok).toBe(false);
    expect(r422.status).toBe(422);
    expect(m).toBe(1);
  });

  test("crear() con una pista de audio devuelve null (el clip es solo video)", { tag: ["@aula", "@KS-026"] }, async ({ page }) => {
    await abrir(page);
    expect(await page.evaluate(() => (window as any).__ksBuffer.crearConAudio())).toBe(null);
  });
});
