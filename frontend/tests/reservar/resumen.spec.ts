import { test, expect } from "@playwright/test";
import { ReservarPage } from "./reservar-page";
import { jsonRoute, mockApi, setFakeSession } from "../helpers";

/** ADR-M5-06 — el resumen automático es un adicional por clase, y su precio crece con la duración. */
const TUTOR_ID = "t-1";
const EN_5_DIAS = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);
const PRECIO_POR_DURACION: Record<string, number> = { "30": 270, "60": 510, "90": 760, "120": 1000 };

const TUTOR = {
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
};
const FRANJA = { id: "f-1", tutorId: TUTOR_ID, diaSemana: null, fechaEspecifica: EN_5_DIAS, horaInicio: "10:00", horaFin: "12:00", activa: true };

test.describe("Resumen automático — reserva", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "el precio del resumen se pide para la duración elegida y se manda resumenContratado al reservar",
    { tag: ["@e2e", "@reserva", "@RESUMEN-E2E-001"] },
    async ({ page }) => {
      const duracionesPedidas: string[] = [];
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [FRANJA]),
        "GET /api/reservas/adicional-resumen": async (route) => {
          const d = new URL(route.request().url()).searchParams.get("duracionMinutos") ?? "";
          duracionesPedidas.push(d);
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({ disponible: true, precio: PRECIO_POR_DURACION[d] ?? 0 }),
          });
        },
        "POST /api/reservas": jsonRoute(201, { id: "r-1" }),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      // Por defecto, 1 h: el precio se pide para 60 min.
      await expect.poll(() => duracionesPedidas).toContain("60");
      // Al cambiar la duración, el precio se vuelve a pedir.
      await reservar.elegirDuracion("2 h");
      await expect.poll(() => duracionesPedidas).toContain("120");
      await reservar.elegirHorario("10:00 a 12:00");
      await expect(page.getByText(/\+\$\s*1\.000/)).toBeVisible();

      await page.getByRole("checkbox", { name: /Agregar resumen automático/ }).check();
      const pedido = page.waitForRequest((r) => r.url().endsWith("/api/reservas") && r.method() === "POST");
      await reservar.confirmar();
      expect((await pedido).postDataJSON()).toMatchObject({ duracionMinutos: 120, resumenContratado: true });
    }
  );

  test(
    "si el tutor no ofrece el resumen, no aparece la opción",
    { tag: ["@e2e", "@reserva", "@RESUMEN-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [FRANJA]),
        "GET /api/reservas/adicional-resumen": jsonRoute(200, { disponible: false, precio: 510 }),
      });
      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirHorario("10:00 a 11:00");
      await expect(page.getByRole("checkbox", { name: /Agregar resumen automático/ })).toHaveCount(0);
    }
  );
});
