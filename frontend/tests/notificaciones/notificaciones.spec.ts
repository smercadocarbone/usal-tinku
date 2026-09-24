import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

test.describe("Avisos — bandeja in-app (FASE2-03)", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "la campana cuenta los no leídos y la bandeja muestra el aviso de kill-switch sin detalles de la clase",
    { tag: ["@critical", "@e2e", "@seguridad-menor", "@NOTIFICACIONES-E2E-001"] },
    async ({ page }) => {
      const leidas: string[] = [];
      await mockApi(page, {
        "GET /api/notificaciones/no-leidas": async (route) =>
          route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ cantidad: 2 - leidas.length }) }),
        "GET /api/notificaciones": jsonRoute(200, [
          {
            id: "n-1",
            tipo: "KILLSWITCH_MENOR",
            datos: { sesionId: "s-1", fecha: "2026-09-24T15:00:00Z" },
            creadaAt: "2026-09-24T15:00:00Z",
            leida: false,
          },
          {
            id: "n-2",
            tipo: "DENUNCIA_RECIBIDA",
            datos: { denunciaId: "d-1", descargoVenceAt: "2026-09-26T15:00:00Z" },
            creadaAt: "2026-09-24T14:00:00Z",
            leida: false,
          },
        ]),
        "POST /api/notificaciones/:id/leida": async (route) => {
          leidas.push(new URL(route.request().url()).pathname.split("/")[3]);
          await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
        },
      });

      await page.goto("/buscar");
      const campana = page.getByRole("link", { name: "Avisos, 2 sin leer" });
      await expect(campana).toBeVisible();
      await campana.click();

      await expect(page).toHaveURL(/\/cuenta\/notificaciones/);
      await expect(page.getByText("Cortamos una clase de tu hijo o hija por seguridad")).toBeVisible();
      await expect(page.getByText("Recibiste una denuncia")).toBeVisible();
      await expect(page.getByText(/Podés contar tu versión hasta el 26/)).toBeVisible();
      await expect(page.getByRole("link", { name: "Presentar mi descargo" })).toHaveAttribute("href", "/cuenta/seguridad");
      await expect.poll(() => leidas.sort()).toEqual(["n-1", "n-2"]);
      await expect(page.getByRole("link", { name: "Avisos", exact: true })).toBeVisible();
    }
  );

  test(
    "sin avisos, lo dice en vez de mostrar una lista vacía",
    { tag: ["@e2e", "@NOTIFICACIONES-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
        "GET /api/notificaciones": jsonRoute(200, []),
      });

      await page.goto("/cuenta/notificaciones");
      await expect(page.getByText("No tenés avisos")).toBeVisible();
    }
  );
});
