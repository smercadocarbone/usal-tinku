import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

/** R-C C-3 (H-AULA-3): el título de la pantalla de corte se lee sobre el fondo oscuro. */
test.use({ reducedMotion: "reduce" });

for (const motivo of ["no-disponible", "seguridad", "sin-proteccion"]) {
  test(`la pantalla de corte "${motivo}" no tiene problemas de contraste`, { tag: ["@aula", "@a11y", "@KS-055"] }, async ({ page }) => {
    await page.goto(`/dev/aviso?motivo=${motivo}`);
    await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
    const r = await new AxeBuilder({ page }).withRules(["color-contrast"]).analyze();
    expect(r.violations.map((v) => v.nodes.map((n) => n.target.join(" ")))).toEqual([]);
  });
}
