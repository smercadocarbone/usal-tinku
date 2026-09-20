import { test, expect } from "@playwright/test";
import { ReservaDetallePage } from "./reserva-detalle-page";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

const RESERVA_ID = "r-1";
const SESION_ID = "s-1";

function reserva(estado: string) {
  return {
    id: RESERVA_ID,
    pagadorId: "u-1",
    beneficiarioId: "u-1",
    tutorId: "t-1",
    horario: "2026-01-01T15:00:00Z",
    precio: 15000,
    estado,
    motivoCancelacion: null,
  };
}

function sesion(estado: string) {
  return {
    id: SESION_ID,
    reservaId: RESERVA_ID,
    estado,
    livekitRoomId: `sesion-${SESION_ID}`,
    inicioReal: null,
    finReal: null,
    duracionEfectivaSegundos: null,
  };
}

test.describe("Detalle de reserva — entrar a la clase y calificar", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "una reserva confirmada con sesión programada ofrece entrar a la clase",
    { tag: ["@critical", "@e2e", "@aula", "@RESERVA-DETALLE-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva("confirmada")),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: jsonRoute(200, sesion("no_iniciada")),
      });

      const detalle = new ReservaDetallePage(page);
      await detalle.goto(RESERVA_ID);

      await expect(detalle.botonEntrarClase).toBeVisible();
      await expect(detalle.botonEntrarClase).toHaveAttribute("href", `/aula/${SESION_ID}`);
    }
  );

  test(
    "una sesión finalizada ofrece calificar, y enviar la calificación confirma",
    { tag: ["@critical", "@e2e", "@RESERVA-DETALLE-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva("finalizada")),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: jsonRoute(200, sesion("finalizada")),
        [`POST /api/sesiones/${SESION_ID}/calificacion`]: jsonRoute(201, {
          id: "cal-1",
          sesionId: SESION_ID,
          direccion: "estudiante_a_tutor",
          estrellas: 5,
          comentario: null,
          editableHasta: "2026-01-03T00:00:00Z",
          createdAt: "2026-01-01T00:00:00Z",
        }),
      });

      const detalle = new ReservaDetallePage(page);
      await detalle.goto(RESERVA_ID);

      await expect(detalle.botonEntrarClase).toHaveCount(0);
      await detalle.estrella(5).click();
      await detalle.botonEnviarCalificacion.click();

      await expect(page.getByText("¡Gracias! Tu calificación quedó registrada.")).toBeVisible();
    }
  );

  test(
    "una sesión finalizada con resumen disponible lo muestra",
    { tag: ["@e2e", "@RESERVA-DETALLE-E2E-004"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva("finalizada")),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: jsonRoute(200, sesion("finalizada")),
        [`GET /api/sesiones/${SESION_ID}/resumen`]: jsonRoute(200, {
          disponible: true,
          resumenFinal: "Repasamos ecuaciones de primer grado.",
        }),
      });

      const detalle = new ReservaDetallePage(page);
      await detalle.goto(RESERVA_ID);

      await expect(page.getByText("Resumen de la clase")).toBeVisible();
      await expect(page.getByText("Repasamos ecuaciones de primer grado.")).toBeVisible();
    }
  );

  test(
    "sin resumen disponible, no se muestra ninguna tarjeta de resumen",
    { tag: ["@e2e", "@RESERVA-DETALLE-E2E-005"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva("finalizada")),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: jsonRoute(200, sesion("finalizada")),
        [`GET /api/sesiones/${SESION_ID}/resumen`]: jsonRoute(200, {
          disponible: false,
          resumenFinal: null,
        }),
      });

      const detalle = new ReservaDetallePage(page);
      await detalle.goto(RESERVA_ID);

      await expect(page.getByText("Resumen de la clase")).toHaveCount(0);
    }
  );

  test(
    "una reserva sin sesión programada todavía no ofrece ni entrar ni calificar",
    { tag: ["@e2e", "@RESERVA-DETALLE-E2E-003"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/reservas/${RESERVA_ID}`]: jsonRoute(200, reserva("pendiente_pago")),
        [`GET /api/sesiones/por-reserva/${RESERVA_ID}`]: jsonRoute(404, {
          error: "Sesión de Aprendizaje no encontrada.",
        }),
      });

      const detalle = new ReservaDetallePage(page);
      await detalle.goto(RESERVA_ID);

      await expect(page.getByRole("heading", { name: "Detalle de la reserva" })).toBeVisible();
      await expect(detalle.botonEntrarClase).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Enviar calificación" })).toHaveCount(0);
    }
  );
});
