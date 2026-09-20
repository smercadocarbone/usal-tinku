import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

function perfil(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "u-1",
    nombre: "Ana",
    apellido: "Gomez",
    tipo: "ADULTO",
    capacidadEstudiante: true,
    capacidadAdultoResponsable: false,
    email: "ana@tinku.test",
    ...overrides,
  };
}

test.describe("Cuenta — capacidades (Estudiante / Adulto Responsable)", () => {
  test(
    "un Adulto activa la capacidad de Adulto Responsable",
    { tag: ["@critical", "@e2e", "@CAPACIDADES-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO" });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil()),
        "GET /api/usuarios/menores": jsonRoute(200, []),
        "GET /api/solicitudes/pendientes": jsonRoute(200, []),
        "PATCH /api/usuarios/me/capacidades": jsonRoute(200, perfil({ capacidadAdultoResponsable: true })),
      });

      await page.goto("/cuenta");
      await page.waitForLoadState("networkidle");

      const checkboxAr = page.getByLabel("Adulto Responsable", { exact: true });
      await expect(checkboxAr).not.toBeChecked();
      await checkboxAr.check();
      await page.getByRole("button", { name: "Guardar capacidades" }).click();

      await expect(page.getByText("Capacidades actualizadas.")).toBeVisible();
    }
  );

  test(
    "no se puede desactivar Adulto Responsable con menores a cargo",
    { tag: ["@e2e", "@seguridad-menor", "@CAPACIDADES-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", cap_ar: true });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil({ capacidadAdultoResponsable: true })),
        "GET /api/usuarios/menores": jsonRoute(200, [{ id: "m-1", nombre: "Sofía", apellido: "Pérez" }]),
        "GET /api/solicitudes/pendientes": jsonRoute(200, []),
        "PATCH /api/usuarios/me/capacidades": jsonRoute(409, {
          error: "No se puede desactivar Adulto Responsable con menores a cargo.",
        }),
      });

      await page.goto("/cuenta");
      await page.waitForLoadState("networkidle");

      const checkboxAr = page.getByLabel("Adulto Responsable", { exact: true });
      await checkboxAr.uncheck();
      await page.getByRole("button", { name: "Guardar capacidades" }).click();

      await expect(page.getByText("No se puede desactivar Adulto Responsable con menores a cargo.")).toBeVisible();
    }
  );

  test(
    "un Tutor no ve el control de capacidades",
    { tag: ["@e2e", "@CAPACIDADES-E2E-003"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil({ tipo: "TUTOR", capacidadEstudiante: false })),
        "GET /api/tutores/me/credencial": (route) => route.fulfill({ status: 204, body: "" }),
      });

      await page.goto("/cuenta");
      await page.waitForLoadState("networkidle");

      await expect(page.getByText("Capacidades")).toHaveCount(0);
    }
  );
});
