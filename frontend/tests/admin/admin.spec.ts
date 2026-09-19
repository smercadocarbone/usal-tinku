import { test, expect } from "@playwright/test";
import { setFakeSession } from "../helpers";

/**
 * Cubre el componente `Tabs` de la librería en su hábitat real: navegación
 * por teclado con foco móvil (un solo tab-stop para todo el grupo, flechas
 * para moverse) — el patrón WAI-ARIA que el toggle anterior (`aria-pressed`
 * suelto) no tenía. No depende del rol de Admin: alcanza con que el tablist
 * exista y reaccione, sea cual sea el contenido de cada cola.
 */
test.describe("Panel de Administración — Tabs", () => {
  test(
    "las flechas mueven la selección y el foco entre pestañas",
    { tag: ["@a11y", "@TABS-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await page.goto("/admin");

      const tablist = page.getByRole("tablist", {
        name: "Secciones del panel de administración",
      });
      const primeraTab = tablist.getByRole("tab").first();
      const segundaTab = tablist.getByRole("tab").nth(1);

      await expect(primeraTab).toHaveAttribute("aria-selected", "true");
      await expect(primeraTab).toHaveAttribute("tabindex", "0");
      await expect(segundaTab).toHaveAttribute("tabindex", "-1");

      await primeraTab.focus();
      await page.keyboard.press("ArrowRight");

      await expect(segundaTab).toHaveAttribute("aria-selected", "true");
      await expect(segundaTab).toBeFocused();
      await expect(primeraTab).toHaveAttribute("tabindex", "-1");
    }
  );
});
