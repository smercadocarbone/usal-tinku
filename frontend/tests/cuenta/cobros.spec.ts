import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSessionConPayload } from "../helpers";

/** R3/R5: "Mis cobros" del Tutor — conexión de MercadoPago (ADR-M5-02) y cobros por clase. */
test.describe("Mis cobros del tutor", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
  });

  test(
    "sin MercadoPago conectado ve cómo conectarlo, y sus cobros con el neto y el estado",
    { tag: ["@e2e", "@pagos", "@COBROS-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/pagos/mp/estado": jsonRoute(200, { requerida: true, estado: null, conectadaAt: null }),
        "GET /api/pagos/mis-cobros": jsonRoute(200, {
          retenido: 10950,
          enRevision: 0,
          liberado: 21900,
          reembolsado: 0,
          cobros: [
            {
              reservaId: "r-1",
              horario: new Date(Date.now() - 3600000).toISOString(),
              alumnoNombre: "Lucas",
              alumnoApellido: "Díaz",
              precioSesion: 15000,
              comision: 4050,
              neto: 10950,
              estado: "retenido",
              liberaAt: null,
              simulado: false,
            },
          ],
        }),
      });

      await page.goto("/cuenta/cobros");

      await expect(page.getByRole("heading", { name: "Conectá tu MercadoPago" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Conectar MercadoPago" })).toBeVisible();
      await expect(page.getByRole("link", { name: "Clase con Lucas Díaz" })).toBeVisible();
      await expect(page.getByText("Por cobrar").last()).toBeVisible();
      await expect(page.getByRole("main").getByText(/10\.950/).first()).toBeVisible();
    }
  );

  test(
    "al volver de MercadoPago con la cuenta conectada lo confirma",
    { tag: ["@e2e", "@pagos", "@COBROS-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/pagos/mp/estado": jsonRoute(200, { requerida: true, estado: "CONECTADA", conectadaAt: new Date().toISOString() }),
        "GET /api/pagos/mis-cobros": jsonRoute(200, { retenido: 0, enRevision: 0, liberado: 0, reembolsado: 0, cobros: [] }),
      });

      await page.goto("/cuenta/cobros?mp=ok");

      await expect(page.getByText("¡Listo! Tu cuenta de MercadoPago quedó conectada.")).toBeVisible();
      await expect(page.getByRole("heading", { name: "MercadoPago conectado" })).toBeVisible();
      await expect(page.getByText("Todavía no tenés cobros")).toBeVisible();
    }
  );
});
