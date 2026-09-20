import { test, expect } from "@playwright/test";
import { SeguridadPage } from "./seguridad-page";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

function denuncia(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: "d-1",
    denunciadoId: "u-1",
    estado: "en_revision",
    motivo: "acoso",
    sesionId: "s-1",
    descargoTexto: null,
    descargoVenceAt: "2026-01-05T00:00:00Z",
    slaResolucionVenceAt: null,
    prioridadAlta: false,
    resueltaAt: null,
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

test.describe("Cuenta — mis denuncias recibidas y alertas de seguridad", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
  });

  test(
    "sin casos, avisa en vez de mostrar listas vacías confusas",
    { tag: ["@e2e", "@SEGURIDAD-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/denuncias/recibidas": jsonRoute(200, []),
        "GET /api/alertas-seguridad/mias": jsonRoute(200, []),
      });

      const seguridad = new SeguridadPage(page);
      await seguridad.goto();

      await expect(page.getByText("No tenés denuncias recibidas.")).toBeVisible();
      await expect(page.getByText("No tenés alertas de seguridad.")).toBeVisible();
    }
  );

  test(
    "una denuncia recibida sin descargo ofrece el formulario, y enviarlo lo confirma",
    { tag: ["@critical", "@e2e", "@seguridad-menor", "@SEGURIDAD-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/denuncias/recibidas": jsonRoute(200, [denuncia()]),
        "GET /api/alertas-seguridad/mias": jsonRoute(200, []),
        "POST /api/denuncias/d-1/descargo": jsonRoute(200, denuncia({
          descargoTexto: "Mi versión de los hechos.",
        })),
      });

      const seguridad = new SeguridadPage(page);
      await seguridad.goto();

      await expect(page.getByText("Acoso")).toBeVisible();
      await expect(page.getByText(/Podés dar tu versión hasta/)).toBeVisible();

      await seguridad.campoDescargo.fill("Mi versión de los hechos.");
      await seguridad.botonEnviarDescargo.click();

      await expect(page.getByText("Tu descargo:")).toBeVisible();
      await expect(page.getByText("Mi versión de los hechos.")).toBeVisible();
      await expect(seguridad.botonEnviarDescargo).toHaveCount(0);
    }
  );

  test(
    "una denuncia con descargo ya enviado no vuelve a ofrecer el formulario",
    { tag: ["@e2e", "@SEGURIDAD-E2E-003"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/denuncias/recibidas": jsonRoute(200, [
          denuncia({ descargoTexto: "Ya expliqué mi versión." }),
        ]),
        "GET /api/alertas-seguridad/mias": jsonRoute(200, []),
      });

      const seguridad = new SeguridadPage(page);
      await seguridad.goto();

      await expect(page.getByText("Ya expliqué mi versión.")).toBeVisible();
      await expect(seguridad.botonEnviarDescargo).toHaveCount(0);
    }
  );

  test(
    "una alerta de seguridad automática ofrece dar la versión del tutor",
    { tag: ["@e2e", "@seguridad-menor", "@SEGURIDAD-E2E-004"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/denuncias/recibidas": jsonRoute(200, []),
        "GET /api/alertas-seguridad/mias": jsonRoute(200, [
          {
            id: "a-1",
            sesionId: "s-1",
            rama: "menor",
            detectadoId: "u-1",
            estado: "pendiente_revision",
            descargoTexto: null,
            descargoRecibidoAt: null,
            clipRetencionHasta: null,
            createdAt: "2026-01-01T00:00:00Z",
          },
        ]),
        "POST /api/alertas-seguridad/a-1/descargo": jsonRoute(200, {
          id: "a-1",
          sesionId: "s-1",
          rama: "menor",
          detectadoId: "u-1",
          estado: "pendiente_revision",
          descargoTexto: "No era lo que parecía.",
          descargoRecibidoAt: "2026-01-02T00:00:00Z",
          clipRetencionHasta: null,
          createdAt: "2026-01-01T00:00:00Z",
        }),
      });

      const seguridad = new SeguridadPage(page);
      await seguridad.goto();

      await expect(page.getByText("Contenido con un menor")).toBeVisible();

      await seguridad.campoDescargo.fill("No era lo que parecía.");
      await seguridad.botonEnviarDescargo.click();

      await expect(page.getByText("No era lo que parecía.")).toBeVisible();
    }
  );
});
