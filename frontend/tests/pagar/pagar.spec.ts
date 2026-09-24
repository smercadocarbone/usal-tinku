import { test, expect } from "@playwright/test";
import { PagarPage } from "./pagar-page";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

const RESERVA_ID = "r-1";

test.describe("Pago de una reserva", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "con la pasarela en modo Bypass, confirma sin cobrar y sin salir de Tinku",
    { tag: ["@critical", "@e2e", "@pago", "@PAGAR-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, {
          id: RESERVA_ID,
          precio: 5000,
        }),
        "POST /api/pagos/preferencia": jsonRoute(200, {
          preferenciaId: "pref-1",
          initPoint: "",
          bypass: true,
        }),
      });

      const pagar = new PagarPage(page);
      await pagar.goto(RESERVA_ID);

      await expect(
        page.getByText("La pasarela de pagos está deshabilitada")
      ).toBeVisible();

      await pagar.botonConfirmarSimulado.click();

      await expect(page.getByText("Reserva confirmada en modo simulado")).toBeVisible();
      // Sigue en /pagar — el modo Bypass nunca navega a un sitio externo.
      await expect(page).toHaveURL(/\/pagar\?reserva=r-1/);
    }
  );

  test(
    "con la pasarela real, el precio se muestra y advierte la salida a MercadoPago",
    { tag: ["@e2e", "@pago", "@PAGAR-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, {
          id: RESERVA_ID,
          precio: 5000,
        }),
        "POST /api/pagos/preferencia": jsonRoute(200, {
          preferenciaId: "pref-1",
          initPoint: "https://mercadopago.example/checkout/pref-1",
          bypass: false,
        }),
      });

      const pagar = new PagarPage(page);
      await pagar.goto(RESERVA_ID);

      await expect(page.getByText("$5.000")).toBeVisible();
      await expect(pagar.botonMercadoPago).toBeVisible();
      await expect(
        page.getByText("Vas a salir de Tinku y continuar en el sitio de MercadoPago.")
      ).toBeVisible();
      // No se hace click: llevaría a un dominio externo real.
    }
  );

  test(
    "si falla la generación del pago, se puede avisar a soporte (M5.pago_fallido)",
    { tag: ["@e2e", "@pago", "@PAGAR-E2E-003"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, {
          id: RESERVA_ID,
          precio: 5000,
        }),
        "POST /api/pagos/preferencia": jsonRoute(422, {
          error: "No se pudo generar la preferencia de pago.",
        }),
        "POST /api/soporte/tickets": jsonRoute(201, {
          id: "ticket-1",
          usuarioId: "u-1",
          origenModulo: "M5.pago_fallido",
          asunto: `No se pudo generar el pago de la reserva ${RESERVA_ID}`,
          detalle: "detalle",
          estado: "abierto",
          rolAsignado: "soporte_financiero",
          creadoEn: "2026-01-01T00:00:00Z",
          resueltoEn: null,
        }),
      });

      const pagar = new PagarPage(page);
      await pagar.goto(RESERVA_ID);

      await expect(page.getByText("No se pudo generar la preferencia de pago.")).toBeVisible();
      await expect(pagar.botonReintentar).toBeVisible();
      await expect(pagar.botonContactarSoporte).toBeVisible();

      await pagar.botonContactarSoporte.click();
      // El detalle viene prellenado con el número de reserva y el error.
      await expect(pagar.campoDetalleSoporte).toHaveValue(new RegExp(RESERVA_ID));
      await pagar.botonEnviarSoporte.click();

      await expect(page.getByText("Le avisamos a soporte.")).toBeVisible();
    }
  );

  test(
    "sin parámetro de reserva, redirige a mis reservas (B9)",
    { tag: ["@e2e", "@pago", "@PAGAR-E2E-004"] },
    async ({ page }) => {
      const pagar = new PagarPage(page);
      await pagar.goto("");

      await expect(page).toHaveURL(/\/cuenta\/reservas/);
      await expect(page.getByText("Pago de la reserva")).toHaveCount(0);
    }
  );
});
