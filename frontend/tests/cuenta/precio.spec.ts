import { test, expect } from "@playwright/test";
import { mockApi, setFakeSessionConPayload } from "../helpers";

/**
 * /cuenta/precio (TabPrecio):
 * - B11: sin referencia regional el backend responde 204 (estado vacío esperado).
 * - UX-06 §4: en ese caso NO se muestra ningún bloque ("todavía no tenemos…"
 *   era ruido); tampoco hay provincia elegida por defecto.
 */
test.describe("Configuración de Precio del tutor", () => {
  test(
    "sin referencia regional (204) no se muestra ningún bloque ni error (B11)",
    { tag: ["@e2e", "@CUENTA-PRECIO-B11-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/pagos/tarifa": async (route) => route.fulfill({ status: 204 }),
        "GET /api/pagos/precio-referencia/Buenos Aires": async (route) => route.fulfill({ status: 204 }),
      });

      await page.goto("/cuenta/precio");
      await expect(page.getByLabel("Tu provincia (para ver el precio de referencia)")).toHaveValue("");
      await page.getByLabel("Tu provincia (para ver el precio de referencia)").selectOption("Buenos Aires");

      await expect(page.getByText("Precio de referencia en")).toHaveCount(0);
      await expect(page.getByText("No pudimos", { exact: false })).toHaveCount(0);
    }
  );

  test(
    "el precio actual se lee del backend, no de localStorage",
    { tag: ["@e2e", "@CUENTA-PRECIO-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/pagos/tarifa": async (route) =>
          route.fulfill({ status: 200, contentType: "application/json", body: '{"tutorId":"t-1","precioHora":12000}' }),
      });

      await page.goto("/cuenta/precio");
      await expect(page.getByLabel("Precio por hora (ARS)")).toHaveValue("12000");
      await expect(page.getByText("Así lo ven las familias")).toBeVisible();
    }
  );
});
