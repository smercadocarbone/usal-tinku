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
            // Siempre dentro de los próximos 14 días (antes era una fecha fija que envejecía).
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
      // D6: bloques de 30 min — con 1 h (por defecto) se ofrecen 10:00, 10:30 y 11:00.
      await expect(page.getByRole("button", { name: /^10:30 a 11:30/ })).toBeVisible();
      await expect(page.getByRole("button", { name: /^11:30/ })).toHaveCount(0);
      await reservar.elegirDuracion("2 h");
      await reservar.elegirHorario("10:00 a 12:00");
      // Resumen antes de pagar: precio y duración a la vista (UX-04 §3); 5000/h × 2 h.
      await expect(page.getByText("2 h", { exact: true })).toBeVisible();
      await expect(page.getByRole("main").getByText(/10\.000/).first()).toBeVisible();
      const pedido = page.waitForRequest((r) => r.url().endsWith("/api/reservas") && r.method() === "POST");
      await reservar.confirmar();
      expect((await pedido).postDataJSON()).toMatchObject({ tutorId: TUTOR_ID, duracionMinutos: 120 });

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
      await expect(page.getByRole("button", { name: "Confirmar y pagar" })).toHaveCount(0);

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

  test(
    "si el horario se ocupa mientras decide (409 HORARIO_OCUPADO), vuelve al paso 1 con ese horario tachado",
    { tag: ["@e2e", "@reserva", "@RESERVAR-E2E-004"] },
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
            fechaEspecifica: new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10),
            horaInicio: "10:00",
            horaFin: "12:00",
            activa: true,
          },
        ]),
        "POST /api/reservas": jsonRoute(409, { error: "El horario ya está reservado.", codigo: "HORARIO_OCUPADO" }),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirHorario("10:30 a 11:30");
      await reservar.confirmar();

      await expect(page.getByText("Ese horario se acaba de ocupar. Elegí otro.")).toBeVisible();
      // Tachado (deshabilitado) u oculto: en ningún caso se puede volver a elegir.
      await expect(page.getByRole("button", { name: /^10:30 a 11:30/, disabled: false })).toHaveCount(0);
    }
  );
});
