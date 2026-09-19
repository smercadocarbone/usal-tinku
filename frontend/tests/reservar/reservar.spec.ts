import { test, expect } from "@playwright/test";
import { ReservarPage } from "./reservar-page";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

const TUTOR_ID = "t-1";

test.describe("Reserva de una clase", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "un adulto elige una franja publicada y confirma, y termina en /pagar",
    { tag: ["@critical", "@e2e", "@reserva", "@RESERVAR-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, {
          id: TUTOR_ID,
          nombre: "Martín",
          apellido: "Gómez",
          tipo: "TUTOR",
          capacidadEstudiante: false,
          capacidadAdultoResponsable: false,
          materias: ["Matemática"],
          nivel: "secundario",
          calificacionPromedio: 4.8,
          cantidadCalificaciones: 12,
          precioHora: 5000,
        }),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [
          {
            id: "f-1",
            tutorId: TUTOR_ID,
            diaSemana: null,
            fechaEspecifica: "2026-10-05T00:00:00Z",
            horaInicio: "10:00",
            horaFin: "12:00",
            activa: true,
          },
        ]),
        "POST /api/reservas": jsonRoute(201, { id: "r-1" }),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirFranjaYHora("10:00 a 12:00", "10:00");
      await reservar.confirmar();

      await expect(page).toHaveURL(/\/pagar\?reserva=r-1/);
    }
  );

  test(
    "un Estudiante Menor no ve el formulario de reserva (Artículo II)",
    { tag: ["@critical", "@e2e", "@reserva", "@seguridad-menor", "@RESERVAR-E2E-002"] },
    async ({ page, context, baseURL }) => {
      // JWT real de un Menor: header.payload.signature con tipo=MENOR en el payload.
      const payload = Buffer.from(JSON.stringify({ tipo: "MENOR" })).toString("base64url");
      const tokenMenor = `header.${payload}.signature`;
      await context.addCookies([{ name: "tinku_jwt", value: tokenMenor, url: baseURL! }]);
      await context.addInitScript(
        (t) => window.localStorage.setItem("tinku_jwt", t),
        tokenMenor
      );

      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, {
          id: TUTOR_ID,
          nombre: "Martín",
          apellido: "Gómez",
          tipo: "TUTOR",
          capacidadEstudiante: false,
          capacidadAdultoResponsable: false,
          materias: [],
          nivel: "",
          calificacionPromedio: null,
          cantidadCalificaciones: 0,
        }),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, []),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);

      await expect(page.getByText("Tu Adulto Responsable debe reservar por vos.")).toBeVisible();
      await expect(page.getByRole("button", { name: "Reservar y pagar" })).toHaveCount(0);
    }
  );
});
