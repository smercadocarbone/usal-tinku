import { test, expect } from "@playwright/test";
import { CuentaPage } from "./cuenta-page";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

function credencial(estado: string) {
  return {
    id: "cred-1",
    tipoDocumento: "TITULO",
    estado,
    numeroIntento: 1,
    createdAt: "2026-01-01T00:00:00Z",
  };
}

test.describe("Cuenta — estado real de la credencial (Tutor)", () => {
  test(
    "sin credencial cargada todavía, ofrece el formulario para cargar una",
    { tag: ["@critical", "@e2e", "@CREDENCIAL-TUTOR-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/tutores/me/credencial": (route) => route.fulfill({ status: 204, body: "" }),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.goto();

      await expect(page.getByText("Todavía no cargaste tu credencial académica")).toBeVisible();
      await expect(cuenta.selectTipoCredencial).toBeVisible();
      await expect(cuenta.botonCargarCredencial).toBeVisible();

      // El cartel fijo de "en revisión" ya no debería aparecer sin haber cargado nada.
      await expect(page.getByText("Tus credenciales estan en revision")).toHaveCount(0);
    }
  );

  test(
    "con una credencial PENDIENTE, avisa que está en revisión y no ofrece el formulario",
    { tag: ["@e2e", "@CREDENCIAL-TUTOR-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/tutores/me/credencial": jsonRoute(200, credencial("PENDIENTE")),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.goto();

      await expect(page.getByText("Tu credencial está en revisión por el equipo de Tinku.")).toBeVisible();
      await expect(cuenta.botonCargarCredencial).toHaveCount(0);
    }
  );

  test(
    "con una credencial APROBADA, muestra el estado real de éxito",
    { tag: ["@e2e", "@CREDENCIAL-TUTOR-E2E-003"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/tutores/me/credencial": jsonRoute(200, credencial("APROBADO")),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.goto();

      await expect(page.getByText("Tu credencial académica fue aprobada.")).toBeVisible();
    }
  );

  test(
    "con una credencial RECHAZADA, avisa y permite volver a cargar",
    { tag: ["@e2e", "@CREDENCIAL-TUTOR-E2E-004"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/tutores/me/credencial": jsonRoute(200, credencial("RECHAZADO")),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.goto();

      await expect(page.getByText("Tu credencial fue rechazada. Podés volver a cargarla.")).toBeVisible();
      await expect(cuenta.botonCargarCredencial).toBeVisible();
    }
  );
});
