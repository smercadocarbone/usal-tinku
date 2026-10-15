import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSession } from "../helpers";

/**
 * Chunk REM-E (docs/superpowers/specs/remediacion-pruebas/R-E-busqueda-perfil.md): estado vacío
 * (T1-M1), búsquedas guardadas con filtros y borrar (T1-6) y comentarios del perfil (T1-11).
 */
test.describe("Búsqueda — remediación", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test("sin resultados muestra el estado vacío", { tag: ["@buscar", "@BUSCAR-E-001"] }, async ({ page }) => {
    await mockApi(page, {
      "GET /api/catalogos": jsonRoute(200, []),
      "GET /api/busquedas/guardadas": jsonRoute(200, []),
      "POST /api/busquedas": jsonRoute(200, []),
    });
    await page.goto("/buscar");
    const campo = page.getByRole("searchbox").or(page.getByRole("textbox")).first();
    await campo.fill("clases de tango bailarín");
    await campo.press("Enter");
    await expect(page.getByText(/Todavía no encontramos tutores para enseñarte clases de tango bailarín/)).toBeVisible();
  });

  test("las guardadas muestran sus filtros y se pueden borrar", { tag: ["@buscar", "@BUSCAR-E-002"] }, async ({ page }) => {
    let borrada: string | null = null;
    await mockApi(page, {
      "GET /api/catalogos": jsonRoute(200, []),
      "GET /api/busquedas/guardadas": jsonRoute(200, [
        {
          id: "g-1",
          textoBusqueda: "ecuaciones",
          createdAt: "2026-10-14T12:00:00Z",
          filtros: { texto_busqueda: "ecuaciones", filtro_materia: "Matemática", precio_max_hora: 20000 },
        },
      ]),
      "DELETE /api/busquedas/guardadas/:id": async (route) => {
        borrada = new URL(route.request().url()).pathname.split("/").pop() ?? null;
        await route.fulfill({ status: 204 });
      },
    });
    await page.goto("/buscar");
    await expect(page.getByText("Matemática").first()).toBeVisible();
    await expect(page.getByText("Hasta $20000")).toBeVisible();
    await page.getByRole("button", { name: "Borrar la búsqueda «ecuaciones»" }).click();
    await expect.poll(() => borrada).toBe("g-1");
    await expect(page.getByRole("button", { name: "Borrar la búsqueda «ecuaciones»" })).toHaveCount(0);
  });

  test("el perfil muestra los comentarios como texto", { tag: ["@tutores", "@BUSCAR-E-003"] }, async ({ page }) => {
    await mockApi(page, {
      "GET /api/tutores/t-1": jsonRoute(200, {
        id: "t-1", nombre: "Martín", apellido: "Gómez", tipo: "TUTOR", materias: ["Matemática"], nivel: "secundario",
        calificacionPromedio: 4.8, cantidadCalificaciones: 12, precioHora: 5000,
      }),
      "GET /api/tutores/t-1/disponibilidad": jsonRoute(200, []),
      "GET /api/tutores/t-1/calificaciones": jsonRoute(200, [
        { estrellas: 5, comentario: "<script>window.__xss = 1</script>Explica muy claro", fecha: "2026-10-01" },
      ]),
    });
    await page.goto("/tutores/t-1");
    await expect(page.getByRole("heading", { name: "Lo que cuentan las familias" })).toBeVisible();
    await expect(page.getByText("<script>window.__xss = 1</script>Explica muy claro")).toBeVisible();
    expect(await page.evaluate(() => (window as unknown as { __xss?: number }).__xss)).toBeUndefined();
  });
});
