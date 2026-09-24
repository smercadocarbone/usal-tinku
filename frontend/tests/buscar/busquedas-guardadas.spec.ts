import { test, expect } from "@playwright/test";
import { BuscarPage } from "./buscar-page";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

test.describe("Búsqueda de tutores — búsquedas guardadas (US-6)", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "guardar la búsqueda actual la agrega a la lista de guardadas",
    { tag: ["@critical", "@e2e", "@busqueda", "@BUSQUEDAS-GUARDADAS-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "GET /api/busquedas/guardadas": jsonRoute(200, []),
        "POST /api/busquedas": jsonRoute(200, [
          { tutorId: "t-1", score: 0.9, noAutorizado: false },
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
        "POST /api/busquedas/guardadas": jsonRoute(201, {
          id: "g-1",
          textoBusqueda: "cómo dividir polinomios",
          createdAt: "2026-01-01T00:00:00Z",
        }),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();
      await buscar.buscar("cómo dividir polinomios");

      const botonGuardar = page.getByRole("button", { name: "Guardar esta búsqueda" });
      await expect(botonGuardar).toBeVisible();
      await botonGuardar.click();

      // Confirmación no bloqueante + el botón queda en "Guardada" (no se guarda dos veces).
      await expect(page.getByText("Guardamos la búsqueda")).toBeVisible();
      await expect(page.getByRole("button", { name: "Guardada" })).toBeDisabled();
    }
  );

  test(
    "ejecutar una búsqueda guardada muestra resultados frescos",
    { tag: ["@critical", "@e2e", "@busqueda", "@BUSQUEDAS-GUARDADAS-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "GET /api/busquedas/guardadas": jsonRoute(200, [
          { id: "g-1", textoBusqueda: "física para secundario", createdAt: "2026-01-01T00:00:00Z" },
        ]),
        "POST /api/busquedas/guardadas/g-1/ejecutar": jsonRoute(200, [
          { tutorId: "t-2", score: 0.85, noAutorizado: false },
        ]),
        "GET /api/tutores/t-2": jsonRoute(200, {
          id: "t-2",
          nombre: "Laura",
          apellido: "Díaz",
          materias: ["Física"],
          calificacionPromedio: 4.5,
          cantidadCalificaciones: 8,
          precioSesion: 4500,
        }),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();

      await page.getByRole("button", { name: "física para secundario" }).click();

      await expect(buscar.tarjetaTutor("Laura Díaz")).toBeVisible();
    }
  );

  test(
    "sin ninguna búsqueda hecha todavía, no se ofrece guardar",
    { tag: ["@e2e", "@busqueda", "@BUSQUEDAS-GUARDADAS-E2E-003"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "GET /api/busquedas/guardadas": jsonRoute(200, []),
      });

      const buscar = new BuscarPage(page);
      await buscar.goto();

      await expect(page.getByRole("button", { name: "Guardar esta búsqueda" })).toHaveCount(0);
    }
  );
});
