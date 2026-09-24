import { test, expect } from "@playwright/test";
import { mockApi, setFakeSessionConPayload } from "../helpers";

/**
 * /cuenta/precio (TabPrecio):
 * - B11: sin referencia regional el backend responde 204 (estado vacío
 *   esperado) y la UI muestra el mensaje informativo, no loguea un error.
 */
test.describe("Configuración de Precio del tutor", () => {
  test(
    "sin referencia regional (204) se muestra el aviso, no un error (B11)",
    { tag: ["@e2e", "@CUENTA-PRECIO-B11-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/pagos/precio-referencia/Buenos Aires": async (route) =>
          route.fulfill({ status: 204 }),
      });

      await page.goto("/cuenta/precio");

      await expect(
        page.getByText("Todavía no tenemos referencia de precios para Buenos Aires.")
      ).toBeVisible();
    }
  );
});