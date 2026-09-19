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
});
