import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSessionConPayload } from "../helpers";

/** Enmienda v2.5 — FR-RES-029..031: el Tutor propone otro horario y quien pagó responde. */
const RESERVA_ID = "r-1";
const EN_3_DIAS = new Date(Date.now() + 3 * 86400000);
const FECHA_3 = EN_3_DIAS.toISOString().slice(0, 10);

function reserva() {
  return {
    id: RESERVA_ID,
    pagadorId: "u-1",
    beneficiarioId: "u-1",
    tutorId: "t-1",
    horario: new Date(Date.now() + 2 * 86400000).toISOString(),
    duracionMinutos: 60,
    precio: 15000,
    estado: "confirmada",
    motivoCancelacion: null,
    tutorNombre: "Pablo",
    tutorApellido: "Sosa",
    beneficiarioNombre: "Lucas",
    beneficiarioApellido: "Díaz",
    puedeCancelar: true,
    cancelarReembolsaTotal: true,
  };
}

const PEDIDO = {
  id: "p-1",
  reservaId: RESERVA_ID,
  horarioOriginal: reserva().horario,
  horarioPropuesto: `${FECHA_3}T21:00:00Z`,
  motivo: "Me surgió un turno médico",
  estado: "pendiente",
  createdAt: new Date().toISOString(),
  venceAt: new Date(Date.now() + 2 * 86400000 - 3600000).toISOString(),
};

test.describe("Detalle de reserva — pedido de reprogramación del tutor", () => {
  test(
    "el tutor propone un horario libre con un motivo",
    { tag: ["@e2e", "@REPROGRAMACION-TUTOR-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
      let cuerpo: Record<string, unknown> | null = null;
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva()),
        [`GET /api/reservas/${RESERVA_ID}/pedido-reprogramacion`]: async (route) => route.fulfill({ status: 204 }),
        [`GET /api/reservas/${RESERVA_ID}/pedido`]: async (route) => route.fulfill({ status: 204 }),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: async (route) => route.fulfill({ status: 404 }),
        "GET /api/tutores/t-1/franjas": jsonRoute(200, [
          { id: "f-1", tutorId: "t-1", diaSemana: null, fechaEspecifica: FECHA_3, horaInicio: "18:00", horaFin: "20:00", activa: true },
        ]),
        "GET /api/tutores/t-1/horarios": jsonRoute(200, [
          { startTime: `${FECHA_3}T21:00:00Z`, isAvailable: true },
          { startTime: `${FECHA_3}T21:30:00Z`, isAvailable: false },
          { startTime: `${FECHA_3}T22:00:00Z`, isAvailable: true },
        ]),
        [`POST /api/reservas/${RESERVA_ID}/pedido-reprogramacion`]: async (route) => {
          cuerpo = route.request().postDataJSON() as Record<string, unknown>;
          await route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify({ ...PEDIDO, puedoResponder: false, puedoRetirar: true }),
          });
        },
      });

      await page.goto(`/cuenta/reservas/${RESERVA_ID}`);
      await expect(page.getByRole("button", { name: "Cambiar horario" })).toHaveCount(0);
      await page.getByRole("button", { name: "Proponer otro horario" }).click();
      await page.getByRole("dialog").getByRole("button").filter({ hasText: /\d/ }).first().click();
      await page.getByRole("button", { name: "18:00" }).click();
      await expect(page.getByRole("button", { name: "18:30" })).toHaveCount(0); // ocupado
      await page.getByLabel("Motivo (opcional)").fill("Me surgió un turno médico");
      await page.getByRole("button", { name: "Mandar la propuesta" }).click();

      expect(cuerpo).toEqual({ nuevoHorario: `${FECHA_3}T21:00:00Z`, motivo: "Me surgió un turno médico" });
      await expect(page.getByText("Propusiste otro horario")).toBeVisible();
      await expect(page.getByRole("button", { name: "Retirar el pedido" })).toBeVisible();
    }
  );

  test(
    "quien pagó ve la propuesta, puede aceptarla o cancelar con la devolución",
    { tag: ["@e2e", "@REPROGRAMACION-TUTOR-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1" });
      let aceptado = false;
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva()),
        [`GET /api/reservas/${RESERVA_ID}/pedido-reprogramacion`]: async (route) =>
          route.fulfill({
            status: aceptado ? 204 : 200,
            contentType: "application/json",
            body: aceptado ? "" : JSON.stringify({ ...PEDIDO, puedoResponder: true, puedoRetirar: false }),
          }),
        [`GET /api/reservas/${RESERVA_ID}/pedido`]: async (route) => route.fulfill({ status: 204 }),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: async (route) => route.fulfill({ status: 404 }),
        [`POST /api/reservas/${RESERVA_ID}/pedido-reprogramacion/aceptar`]: async (route) => {
          aceptado = true;
          await route.fulfill({ status: 204 });
        },
      });

      await page.goto(`/cuenta/reservas/${RESERVA_ID}`);
      await expect(page.getByText("El tutor te propone otro horario")).toBeVisible();
      await expect(page.getByText("Me surgió un turno médico")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar y recibir la devolución" })).toBeVisible();
      // Mientras hay una propuesta, no se ofrece además cambiar el horario por su cuenta.
      await expect(page.getByRole("button", { name: "Cambiar horario" })).toHaveCount(0);

      await page.getByRole("button", { name: "Aceptar el horario nuevo" }).click();
      await expect(page.getByText("Listo: la clase pasó al horario nuevo")).toBeVisible();
      await expect(page.getByText("El tutor te propone otro horario")).toHaveCount(0);
    }
  );
});
