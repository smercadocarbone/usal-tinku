import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

/**
 * Cubre la navegación del `SettingsShell` en su hábitat real: la sección
 * activa se resalta (`aria-current="page"`) según la URL, no según un
 * estado de tabs en memoria — así el botón atrás/adelante del navegador
 * funciona nativo. Reemplaza al viejo test de `Tabs` (`role="tablist"`),
 * que ya no aplica: `/admin` navega por rutas reales, no por tabs en
 * memoria.
 */
test.describe("Panel de Administración — navegación del SettingsShell", () => {
  test(
    "la sección activa se resalta según la URL y cambia al navegar",
    { tag: ["@a11y", "@TABS-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      });
      await page.goto("/admin/alertas");

      const nav = page.getByRole("navigation", { name: "Secciones" });
      const linkAlertas = nav.getByRole("link", { name: "Alertas de Seguridad" });
      const linkDenuncias = nav.getByRole("link", { name: "Denuncias" });

      await expect(linkAlertas).toHaveAttribute("aria-current", "page");
      await expect(linkDenuncias).not.toHaveAttribute("aria-current", "page");

      await linkDenuncias.click();

      await expect(page).toHaveURL(/\/admin\/denuncias$/);
      await expect(linkDenuncias).toHaveAttribute("aria-current", "page");
      await expect(linkAlertas).not.toHaveAttribute("aria-current", "page");
    }
  );

  test(
    "el menú oculta las secciones que el rol no puede usar (B10)",
    { tag: ["@e2e", "@ADMIN-MENU-B10-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);

      // Moderación: ve Seguridad + Soporte, no Financiero.
      await mockApi(page, {
        "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      });
      await page.goto("/admin/denuncias");
      const nav = page.getByRole("navigation", { name: "Secciones" });
      await expect(nav.getByRole("link", { name: "Denuncias" })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Tickets de soporte" })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Pagos fallidos" })).toHaveCount(0);

      // Soporte financiero: ve Financiero + Soporte, no Seguridad.
      await mockApi(page, {
        "GET /api/admin/yo": jsonRoute(200, { rol: "soporte_financiero" }),
      });
      await page.goto("/admin/pagos");
      await expect(nav.getByRole("link", { name: "Pagos fallidos" })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Tickets de soporte" })).toBeVisible();
      await expect(nav.getByRole("link", { name: "Denuncias" })).toHaveCount(0);
    }
  );
});
