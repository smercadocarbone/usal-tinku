import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

/**
 * B14: el auto-guardado de /cuenta/precio manda `precioSesion` (camelCase) al
 * backend; antes mandaba `precio_sesion` y `ActualizarTarifaTutorRequest`
 * respondía 400 siempre — ningún tutor pudo fijar su tarifa desde la UI.
 */
test.describe("Configuración de Precio del tutor — contrato de tarifa", () => {
  test(
    "el auto-guardado manda precioSesion en camelCase, no precio_sesion",
    { tag: ["@e2e", "@CUENTA-PRECIO-B14-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/pagos/precio-referencia/Buenos Aires": jsonRoute(200, {
          provincia: "Buenos Aires",
          valorSugerido: 15000,
          version: 1,
          vigenteDesde: "2026-01-01T00:00:00Z",
        }),
        "PUT /api/pagos/tarifa": jsonRoute(200, {
          tutorId: "t-1",
          precioSesion: 10000,
          updatedAt: "2026-01-01T00:00:00Z",
        }),
      });
      await page.goto("/cuenta/precio");

      const input = page.getByLabel("Precio por sesión (ARS)");
      const [req] = await Promise.all([
        page.waitForRequest((r) => r.url().includes("/api/pagos/tarifa")),
        input.fill("10000"),
      ]);

      const body = req.postDataJSON();
      expect(body.precioSesion).toBe(10000);
      expect(body.precio_sesion).toBeUndefined();
    }
  );
});