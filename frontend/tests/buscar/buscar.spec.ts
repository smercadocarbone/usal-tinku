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
          precioHora: 5000,
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
          precioHora: null,
        }),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();
      await buscar.buscar("física");

      await expect(page.getByRole("button", { name: "Solicitar autorización" })).toBeVisible();
    }
  );

  test(
    "FR-MATCH-013/014: cada tarjeta muestra el próximo horario y el precio máximo se manda como filtro",
    { tag: ["@e2e", "@busqueda", "@BUSCAR-PRECIO-E2E-001"] },
    async ({ page }) => {
      const cuerpos: Record<string, unknown>[] = [];
      const proximo = "2026-10-01T21:00:00Z";
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "POST /api/busquedas": async (route) => {
          cuerpos.push(route.request().postDataJSON() as Record<string, unknown>);
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
              { tutorId: "t-1", score: 0.9, noAutorizado: false, precioHora: 5000, proximoHorario: proximo },
              { tutorId: "t-2", score: 0.8, noAutorizado: false, precioHora: 6000, proximoHorario: null },
            ]),
          });
        },
        "GET /api/tutores/t-1": jsonRoute(200, { id: "t-1", nombre: "Martín", apellido: "Gómez", materias: [], precioHora: 5000 }),
        "GET /api/tutores/t-2": jsonRoute(200, { id: "t-2", nombre: "Laura", apellido: "Díaz", materias: [], precioHora: 6000 }),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();
      await buscar.buscar("fracciones");

      const tarjeta = (nombre: string) => page.getByRole("article").filter({ has: buscar.tarjetaTutor(nombre) });
      await expect(tarjeta("Martín Gómez")).toContainText("Próximo horario: jue 1 oct · 18:00");
      await expect(tarjeta("Laura Díaz")).toContainText("Sin horarios en las próximas 2 semanas");

      const campo = page.getByLabel("Precio máximo por hora");
      await campo.fill("5500");
      await campo.press("Enter");
      await expect.poll(() => cuerpos.length).toBe(2);
      expect(cuerpos[1].precio_max_hora).toBe(5500);
      await expect(page.getByRole("button", { name: "Quitar filtro de precio máximo" })).toBeVisible();
    }
  );
});
