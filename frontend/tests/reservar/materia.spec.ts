import { test, expect } from "@playwright/test";
import { ReservarPage } from "./reservar-page";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

/** ADR-M3-07 §4bis: la materia de la clase se elige al reservar y viaja en el POST. */
const TUTOR_ID = "t-1";
const MATERIAS = [
  { trayectoId: "tr-quim", nivel: "universitario", carrera: "Medicina", materia: "Química Biológica", contenidoAnatomico: false },
  { trayectoId: "tr-anat", nivel: "universitario", carrera: "Medicina", materia: "Anatomía", contenidoAnatomico: true },
];

test.describe("Reserva — materia de la clase", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "con varias materias hay que elegir una; Anatomía avisa del modo salud y va en la reserva",
    { tag: ["@e2e", "@reserva", "@RESERVAR-MATERIA-001"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, {
          id: TUTOR_ID,
          nombre: "Lucía",
          apellido: "Paz",
          tipo: "TUTOR",
          materias: ["Anatomía", "Química Biológica"],
          nivel: "universitario",
          calificacionPromedio: null,
          cantidadCalificaciones: 0,
          precioHora: 8000,
        }),
        [`GET /api/tutores/${TUTOR_ID}/materias`]: jsonRoute(200, MATERIAS),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [
          {
            id: "f-1",
            tutorId: TUTOR_ID,
            diaSemana: null,
            fechaEspecifica: new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10),
            horaInicio: "10:00",
            horaFin: "12:00",
            activa: true,
          },
        ]),
        "POST /api/reservas": jsonRoute(201, { id: "r-1" }),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirHorario("10:00 a 11:00");

      const confirmar = page.getByRole("button", { name: "Confirmar y pagar" });
      await expect(confirmar).toBeDisabled();
      await page.getByLabel("Materia de la clase").selectOption("tr-anat");
      await expect(page.getByText(/la protección del aula se flexibiliza/)).toBeVisible();

      const pedido = page.waitForRequest((r) => r.url().endsWith("/api/reservas") && r.method() === "POST");
      await reservar.confirmar();
      expect((await pedido).postDataJSON()).toMatchObject({ tutorId: TUTOR_ID, trayectoId: "tr-anat" });
    }
  );
});
