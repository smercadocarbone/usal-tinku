import { test, expect } from "@playwright/test";
import { CuentaPage } from "./cuenta-page";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

test.describe("Cuenta — baja de menor", () => {
  test(
    "un Adulto Responsable ve el listado real de menores para dar de baja",
    { tag: ["@critical", "@e2e", "@CUENTA-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", cap_ar: true });
      await mockApi(page, {
        "GET /api/solicitudes/pendientes": jsonRoute(200, []),
        "GET /api/usuarios/menores": jsonRoute(200, [
          { id: "m-1", nombre: "Sofía", apellido: "Pérez" },
        ]),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoMenores();

      await expect(cuenta.selectMenorBaja).toBeVisible();
      await expect(
        cuenta.selectMenorBaja.locator("option", { hasText: "Sofía Pérez" })
      ).toHaveCount(1);
      await expect(cuenta.botonDarDeBaja).toBeVisible();

      // Regresión: este cartel decía que el listado de menores no existía del
      // lado del backend — ya existe (GET /api/usuarios/menores), no debería
      // volver a aparecer.
      await expect(page.getByText("pendiente en backend")).toHaveCount(0);
    }
  );

  test(
    "sin menores a cargo, se avisa en vez de mostrar un selector vacío",
    { tag: ["@e2e", "@CUENTA-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", cap_ar: true });
      await mockApi(page, {
        "GET /api/solicitudes/pendientes": jsonRoute(200, []),
        "GET /api/usuarios/menores": jsonRoute(200, []),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoMenores();

      await expect(page.getByText("No tenés menores a cargo todavía.")).toBeVisible();
      await expect(cuenta.selectMenorBaja).toHaveCount(0);
    }
  );

  test(
    "una reserva futura del menor exige confirmar la baja explícitamente",
    { tag: ["@e2e", "@seguridad-menor", "@CUENTA-E2E-003"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", cap_ar: true });
      await mockApi(page, {
        "GET /api/solicitudes/pendientes": jsonRoute(200, []),
        "GET /api/usuarios/menores": jsonRoute(200, [
          { id: "m-1", nombre: "Sofía", apellido: "Pérez" },
        ]),
        "DELETE /api/usuarios/menores/m-1": jsonRoute(409, {
          error: "El menor tiene reservas futuras.",
        }),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoMenores();
      await cuenta.botonDarDeBaja.click();

      await expect(page.getByText("Este menor tiene reservas futuras.")).toBeVisible();
      await expect(page.getByRole("button", { name: "Confirmar baja" })).toBeVisible();
    }
  );
});
