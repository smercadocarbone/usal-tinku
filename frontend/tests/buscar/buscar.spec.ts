import { test, expect } from "@playwright/test";
import { BuscarPage } from "./buscar-page";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

test.describe("Búsqueda de tutores", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "buscar por texto libre muestra los resultados con precio y calificación",
    { tag: ["@critical", "@e2e", "@busqueda", "@BUSCAR-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "POST /api/busquedas": jsonRoute(200, [
          { tutorId: "t-1", score: 0.91, noAutorizado: false },
        ]),
        "GET /api/tutores/t-1": jsonRoute(200, {
          id: "t-1",
          nombre: "Martín",
          apellido: "Gómez",
          materias: ["Matemática"],
          calificacionPromedio: 4.8,
          cantidadCalificaciones: 12,
          precioSesion: 5000,
        }),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();
      await buscar.buscar("cómo dividir polinomios");

      await expect(buscar.tarjetaTutor("Martín Gómez")).toBeVisible();
      await expect(page.getByText("Ver perfil")).toBeVisible();
      await expect(
        page.getByText("Tutores recomendados para lo que necesitás")
      ).toBeVisible();
    }
  );

  test(
    "un resultado no autorizado ofrece solicitar autorización, sin exponer contacto directo",
    { tag: ["@e2e", "@busqueda", "@seguridad-menor", "@BUSCAR-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "POST /api/busquedas": jsonRoute(200, [
          { tutorId: "t-2", score: 0.7, noAutorizado: true },
        ]),
        "GET /api/tutores/t-2": jsonRoute(200, {
          id: "t-2",
          nombre: "Laura",
          apellido: "Díaz",
          materias: ["Física"],
          calificacionPromedio: null,
          cantidadCalificaciones: 0,
          precioSesion: null,
        }),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();
      await buscar.buscar("física");

      await expect(page.getByRole("button", { name: "Solicitar autorización" })).toBeVisible();
    }
  );
});
