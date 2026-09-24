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
    "un Estudiante Menor no ve el formulario de reserva y le queda una acción concreta (Artículo II)",
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

      // B5: el "pedile a tu adulto responsable" no es un callejón — copia un
      // mensaje listo para enviarle.
      await context.grantPermissions(
        ["clipboard-read", "clipboard-write"],
        { origin: baseURL! }
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

      const copiar = page.getByRole("button", { name: "Copiar este mensaje" });
      await expect(copiar).toBeVisible();
      await expect(page.getByRole("button", { name: "Reservar y pagar" })).toHaveCount(0);

      await copiar.click();
      await expect(page.getByText("Mensaje copiado")).toBeVisible();
      const copiado = await page.evaluate(() => navigator.clipboard.readText());
      expect(copiado).toContain("Martín Gómez");
      expect(copiado).toContain("¿Me la reservás?");
    }
  );

  test(
    "sin parámetro de tutor, redirige a la búsqueda (B9)",
    { tag: ["@e2e", "@reserva", "@RESERVAR-E2E-003"] },
    async ({ page }) => {
      await page.goto("/reservar");

      await expect(page).toHaveURL(/\/buscar/);
      await expect(page.getByRole("heading", { name: "Reservar una clase" })).toHaveCount(0);
    }
  );
});
