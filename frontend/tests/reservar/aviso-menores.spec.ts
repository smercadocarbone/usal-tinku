import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

/**
 * T-TES-10: el aviso al Menor ("todavía no están habilitadas") depende del estado real de la flag
 * `tinku.menores.sesiones-habilitadas`, que el backend expone en GET /api/reservas/clases-con-menores.
 * Antes se mostraba siempre, aun con la flag encendida. Si la consulta falla, se trata como apagada
 * (fail-closed).
 */
const TUTOR_ID = "t-1";
const AVISO = "Las clases para menores todavía no están habilitadas.";

const PERFIL = {
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

const ESTADOS = [
  { caso: "flag apagada", ruta: jsonRoute(200, { habilitadas: false }), aviso: true },
  { caso: "flag encendida", ruta: jsonRoute(200, { habilitadas: true }), aviso: false },
  { caso: "la consulta falla (fail-closed)", ruta: jsonRoute(500, { error: "x" }), aviso: true },
];

test.describe("Aviso de clases con menores según la flag", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "MENOR" });
  });

  for (const { caso, ruta, aviso } of ESTADOS) {
    test(
      `pedir una clase (menor): ${caso}`,
      { tag: ["@e2e", "@reserva", "@seguridad-menor", "@RESERVAR-E2E-MENORES-FLAG"] },
      async ({ page }) => {
        await mockApi(page, {
          [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, PERFIL),
          [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, []),
          "GET /api/reservas/clases-con-menores": ruta,
        });

        const consulta = page.waitForResponse("**/api/reservas/clases-con-menores");
        consulta.catch(() => undefined); // en los casos con aviso no se espera
        await page.goto(`/reservar?tutor=${TUTOR_ID}`);
        await expect(page.getByRole("heading", { name: "Pedir una clase" })).toBeVisible();

        if (aviso) {
          await expect(page.getByText(AVISO)).toBeVisible();
          await expect(page.getByRole("button", { name: "Copiar este mensaje" })).toBeVisible();
        } else {
          // Esperar a que la consulta resuelva antes de afirmar la ausencia.
          await consulta;
          await expect(page.getByText(AVISO)).toHaveCount(0);
          await expect(page.getByText(/piloto/i)).toHaveCount(0);
        }
      }
    );

    test(
      `perfil del tutor (menor): ${caso}`,
      { tag: ["@e2e", "@seguridad-menor", "@TUTORES-E2E-MENORES-FLAG"] },
      async ({ page }) => {
        await mockApi(page, {
          [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, PERFIL),
          "GET /api/reservas/clases-con-menores": ruta,
        });

        const consulta = page.waitForResponse("**/api/reservas/clases-con-menores");
        consulta.catch(() => undefined); // en los casos con aviso no se espera
        await page.goto(`/tutores/${TUTOR_ID}`);
        await expect(page.getByRole("heading", { name: "Martín Gómez" })).toBeVisible();

        if (aviso) {
          await expect(page.getByText(AVISO)).toBeVisible();
        } else {
          await consulta;
          await expect(page.getByText(AVISO)).toHaveCount(0);
          await expect(page.getByText(/piloto/i)).toHaveCount(0);
        }
      }
    );
  }
});
