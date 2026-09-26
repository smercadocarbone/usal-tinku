import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSessionConPayload } from "../helpers";

/** Enmienda v2.5: pedido previo (FR-RES-027) y nota del Tutor al AR (FR-RES-026) en el detalle. */
const RESERVA_ID = "r-1";

function reserva(extra: Record<string, unknown> = {}) {
  return {
    id: RESERVA_ID,
    pagadorId: "u-ar",
    beneficiarioId: "u-menor",
    tutorId: "t-1",
    horario: new Date(Date.now() + 2 * 86400000).toISOString(),
    precio: 15000,
    estado: "confirmada",
    motivoCancelacion: null,
    tutorNombre: "Pablo",
    tutorApellido: "Sosa",
    beneficiarioNombre: "Sofía",
    beneficiarioApellido: "Pérez",
    beneficiarioMenor: true,
    ...extra,
  };
}

test.describe("Detalle de reserva — pedido previo y nota", () => {
  test(
    "quien pagó escribe el pedido con un archivo y lo manda al tutor",
    { tag: ["@e2e", "@PEDIDO-PREVIO-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-ar", cap_ar: true });
      let cuerpo = "";
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva()),
        [`GET /api/reservas/${RESERVA_ID}/pedido`]: async (route) => route.fulfill({ status: 204 }),
        [`GET /api/reservas/${RESERVA_ID}/nota`]: async (route) => route.fulfill({ status: 204 }),
        [`PUT /api/reservas/${RESERVA_ID}/pedido`]: async (route) => {
          cuerpo = route.request().postDataBuffer()?.toString("latin1") ?? "";
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              reservaId: RESERVA_ID,
              texto: "Divisiones de dos cifras",
              archivoNombre: "ejercicio.png",
              archivoTipo: "image/png",
              updatedAt: new Date().toISOString(),
              editable: true,
            }),
          });
        },
      });

      await page.goto(`/cuenta/reservas/${RESERVA_ID}`);
      await page.getByRole("button", { name: "Escribir el pedido" }).click();
      await page.getByLabel("Contale al tutor qué necesitás").fill("Divisiones de dos cifras");
      await page.locator('input[type="file"]').setInputFiles({
        name: "ejercicio.png",
        mimeType: "image/png",
        buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
      });
      await page.getByRole("button", { name: "Mandar al tutor" }).click();

      await expect(page.getByText("Le mandamos tu pedido al tutor")).toBeVisible();
      await expect(page.getByRole("button", { name: "ejercicio.png" })).toBeVisible();
      expect(cuerpo).toContain('name="texto"');
      expect(cuerpo).toContain('name="archivo"; filename="ejercicio.png"');
      // Clase con un menor sin terminar: el AR no ve todavía ninguna nota.
      await expect(page.getByRole("heading", { name: "Nota del tutor sobre la clase" })).toHaveCount(0);
    }
  );

  test(
    "el tutor lee el pedido y, terminada la clase, le deja la nota al adulto responsable",
    { tag: ["@e2e", "@seguridad-menor", "@NOTA-CLASE-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
      let notaEnviada: unknown = null;
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva({ estado: "finalizada", horario: "2026-01-01T15:00:00Z" })),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: async (route) => route.fulfill({ status: 404 }),
        [`GET /api/reservas/${RESERVA_ID}/pedido`]: jsonRoute(200, {
          reservaId: RESERVA_ID,
          texto: "Divisiones de dos cifras",
          archivoNombre: null,
          archivoTipo: null,
          updatedAt: "2026-01-01T12:00:00Z",
          editable: false,
        }),
        [`GET /api/reservas/${RESERVA_ID}/nota`]: async (route) => route.fulfill({ status: 204 }),
        [`PUT /api/reservas/${RESERVA_ID}/nota`]: async (route) => {
          notaEnviada = route.request().postDataJSON();
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
              reservaId: RESERVA_ID,
              texto: "Vimos divisiones. Le cuesta el resto; conviene practicar.",
              createdAt: new Date().toISOString(),
              updatedAt: new Date().toISOString(),
              editable: true,
            }),
          });
        },
      });

      await page.goto(`/cuenta/reservas/${RESERVA_ID}`);
      await expect(page.getByRole("heading", { name: "Qué quiere ver en la clase" })).toBeVisible();
      await expect(page.getByText("Divisiones de dos cifras")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar el pedido" })).toHaveCount(0);

      await page.getByLabel("Cómo le fue a Sofía").fill("Vimos divisiones. Le cuesta el resto; conviene practicar.");
      await page.getByRole("button", { name: "Mandar la nota" }).click();

      await expect(page.getByText("Le mandamos la nota a su adulto responsable")).toBeVisible();
      expect(notaEnviada).toEqual({ texto: "Vimos divisiones. Le cuesta el resto; conviene practicar." });
      await expect(page.getByRole("button", { name: "Corregir la nota" })).toBeVisible();
    }
  );

  test(
    "el menor no ve la nota ni puede escribir el pedido (Artículo II)",
    { tag: ["@e2e", "@seguridad-menor", "@NOTA-CLASE-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "MENOR", sub: "u-menor" });
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva({ estado: "finalizada", horario: "2026-01-01T15:00:00Z" })),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: async (route) => route.fulfill({ status: 404 }),
        [`GET /api/reservas/${RESERVA_ID}/pedido`]: async (route) => route.fulfill({ status: 404 }),
        [`GET /api/reservas/${RESERVA_ID}/nota`]: async (route) => route.fulfill({ status: 404 }),
      });

      await page.goto(`/cuenta/reservas/${RESERVA_ID}`);
      await expect(page.getByRole("heading", { name: /Clase/ })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Nota/ })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Escribir el pedido" })).toHaveCount(0);
    }
  );
});
