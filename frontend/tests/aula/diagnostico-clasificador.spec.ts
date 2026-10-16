import { test, expect } from "@playwright/test";

/**
 * M3C-07 (T-M3-06g): página pública de diagnóstico del clasificador. Acá se prueba la página con un
 * modelo falso y pocos segundos; la medición real (60 s, CPU frenada) la hace
 * `scripts/medir-clasificador.mjs`.
 */

test.use({
  launchOptions: {
    ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
    args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"],
  },
});

test.describe("Diagnóstico del clasificador", () => {
  test("mide sin login, sin llamar a la API y sin perder tensores", { tag: ["@aula", "@KS-DIAG-01"] }, async ({ page }) => {
    const api: string[] = [];
    page.on("request", (r) => {
      if (new URL(r.url()).pathname.startsWith("/api")) api.push(r.url());
    });
    await page.addInitScript(() => {
      const clases = ["Drawing", "Hentai", "Neutral", "Porn", "Sexy"];
      (window as unknown as Record<string, unknown>).__tinkuModeloFalso = {
        backend: "falso",
        liberar() {},
        async clasificar() {
          await new Promise((r) => setTimeout(r, 20));
          return clases.map((className) => ({ className, probability: className === "Neutral" ? 0.99 : 0 }));
        },
      };
    });
    await page.goto("/diagnostico/clasificador?segundos=3");
    await expect(page).toHaveURL(/\/diagnostico\/clasificador/); // sin redirección a /login
    await page.getByRole("button", { name: "Empezar medición" }).click();
    const resultado = page.getByTestId("resultado");
    await expect(resultado).toBeVisible({ timeout: 20_000 });
    await expect(resultado).toContainText("falso");
    const r = await page.evaluate(
      () => (window as unknown as { __diagnostico: { resultado: Record<string, number> } }).__diagnostico.resultado,
    );
    expect(r.muestras).toBeGreaterThan(3);
    expect(r.tensoresFinal).toBe(r.tensoresInicio);
    await expect(page.getByRole("button", { name: "Copiar resultados" })).toBeVisible();
    expect(api).toEqual([]);
  });

  test("tiene noindex y no está en el sitemap", { tag: ["@aula", "@KS-DIAG-02"] }, async ({ page, request }) => {
    await page.goto("/diagnostico/clasificador");
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /noindex/);
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", /nofollow/);
    const sitemap = await (await request.get("/sitemap.xml")).text();
    expect(sitemap).not.toContain("diagnostico");
  });
});
