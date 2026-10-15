import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSession } from "../helpers";

/**
 * ADR-M1-10: la cola de CAP muestra la verificación de la firma digital con el validador oficial,
 * y "Registrar revisión" (aprobar) solo se habilita con la firma válida.
 */
const BASE = {
  tutorId: "t-1",
  tutorApellido: "Paz",
  estado: "PENDIENTE",
  fechaEmision: "2026-10-01",
  venceAt: "2027-10-01",
  categoriaAntecedente: null,
  numeroIntento: 1,
  createdAt: "2026-10-15T12:00:00Z",
  firmaDetalle: null,
  firmaFirmante: null,
  firmaSha256: null,
  firmaVerificadaAt: null,
};

test.describe("Cola de CAP — firma digital", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test("sin firma válida no se puede aprobar; con firma válida sí", { tag: ["@admin", "@CAP-FIRMA-001"] }, async ({ page }) => {
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      "GET /api/admin/moderacion/antecedentes-penales": jsonRoute(200, [
        { ...BASE, id: "c-1", tutorNombre: "Ana", firmaEstado: "PENDIENTE" },
        {
          ...BASE,
          id: "c-2",
          tutorNombre: "Bruno",
          firmaEstado: "VALIDA",
          firmaFirmante: "JUAN PEREZ · Ministerio de Justicia · Registro Nacional de Reincidencia",
          firmaDetalle: "Firma digital válida.",
          firmaSha256: "a".repeat(64),
        },
      ]),
    });
    await page.goto("/admin/antecedentes");

    const ana = page.getByRole("listitem").filter({ hasText: "Ana Paz" });
    await expect(ana.getByText("Firma: en cola del validador")).toBeVisible();
    await expect(ana.getByRole("button", { name: "Registrar revisión" })).toBeDisabled();
    await expect(ana.getByRole("button", { name: "Rechazar (documento inválido)" })).toBeEnabled();

    const bruno = page.getByRole("listitem").filter({ hasText: "Bruno Paz" });
    await expect(bruno.getByText("Firma digital válida", { exact: true })).toBeVisible();
    await expect(bruno.getByText(/Registro Nacional de Reincidencia/)).toBeVisible();
    await expect(bruno.getByRole("button", { name: "Registrar revisión" })).toBeEnabled();
  });

  test("con error, Moderación puede volver a verificar", { tag: ["@admin", "@CAP-FIRMA-002"] }, async ({ page }) => {
    let pidio = false;
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      "GET /api/admin/moderacion/antecedentes-penales": jsonRoute(200, [
        { ...BASE, id: "c-3", tutorNombre: "Carla", firmaEstado: "ERROR", firmaDetalle: "No se pudo verificar la firma: sin respuesta" },
      ]),
      "POST /api/admin/moderacion/antecedentes-penales/c-3/verificar-firma": async (route) => {
        pidio = true;
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({ ...BASE, id: "c-3", tutorNombre: "Carla", firmaEstado: "PENDIENTE" }),
        });
      },
    });
    await page.goto("/admin/antecedentes");

    await expect(page.getByText("No se pudo verificar la firma", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Volver a verificar" }).click();
    await expect(page.getByText("Firma: en cola del validador")).toBeVisible();
    expect(pidio).toBe(true);
  });
});
