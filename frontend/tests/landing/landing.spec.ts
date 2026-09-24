import { test, expect } from "@playwright/test";

/**
 * B8 — la sección "Pensado para que las familias confíen" se veía como un
 * bloque vacío enorme: TarjetasSeguridad dejaba las tarjetas en opacity:0
 * hasta que un IntersectionObserver las marcaba visibles, y el contenido
 * nunca aparecía si el observador no se disparaba (ni con JS degradado ni
 * con SSR). El contenido ahora es visible por defecto; no depende de scroll
 * ni de ningún observer.
 */
test.describe("Landing — garantías de confianza", () => {
  test(
    "las tarjetas de seguridad se ven apenas carga la página, sin scroll ni IntersectionObserver",
    { tag: ["@e2e", "@LANDING-E2E-001"] },
    async ({ page }) => {
      await page.goto("/");

      await expect(
        page.getByRole("heading", { name: "Pensado para que las familias confíen" })
      ).toBeVisible();

      await expect(page.getByRole("heading", { name: "Pagos protegidos" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Aulas seguras" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Identidades verificadas" })).toBeVisible();

      await expect(
        page.getByText("El pago se procesa con MercadoPago", { exact: false })
      ).toBeVisible();
    }
  );
});