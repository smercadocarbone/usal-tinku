import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

function ticket(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "t-1",
    usuarioId: "u-1",
    origenModulo: "M1.credencial_agotada",
    asunto: "No puedo cargar mi credencial",
    detalle: "Se me agotaron los intentos.",
    estado: "abierto",
    rolAsignado: "moderacion_seguridad",
    creadoEn: "2026-01-01T00:00:00Z",
    resueltoEn: null,
    ...overrides,
  };
}

test.describe("Panel de Administración — Tickets de soporte", () => {
  test(
    "cambiar el estado de un ticket lo actualiza (antes era de solo lectura)",
    { tag: ["@critical", "@e2e", "@ADMIN-TICKETS-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/admin/tickets": jsonRoute(200, [ticket()]),
        "PATCH /api/admin/tickets/t-1": jsonRoute(200, ticket({ estado: "en_proceso" })),
      });

      await page.goto("/admin");
      await page.getByRole("tab", { name: "Tickets de soporte" }).click();

      await expect(page.getByText("No puedo cargar mi credencial")).toBeVisible();

      const select = page.getByLabel("Cambiar estado");
      await expect(select).toHaveValue("abierto");
      await select.selectOption("en_proceso");

      await expect(page.getByText("En proceso").first()).toBeVisible();
    }
  );

  test(
    "un error al cambiar el estado se muestra sin perder el ticket de la lista",
    { tag: ["@e2e", "@ADMIN-TICKETS-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/admin/tickets": jsonRoute(200, [ticket()]),
        "PATCH /api/admin/tickets/t-1": jsonRoute(403, {
          error: "No autorizado para esta acción de administración.",
        }),
      });

      await page.goto("/admin");
      await page.getByRole("tab", { name: "Tickets de soporte" }).click();

      const select = page.getByLabel("Cambiar estado");
      await select.selectOption("cerrado");

      await expect(page.getByText("No autorizado para esta acción de administración.")).toBeVisible();
      await expect(page.getByText("No puedo cargar mi credencial")).toBeVisible();
    }
  );
});
