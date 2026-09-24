import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockApi, jsonRoute, setFakeSession } from "../helpers";

/**
 * Barrera de accesibilidad automática.
 *
 * No reemplaza una revisión manual (axe detecta ~30-40% de los problemas
 * reales: no ve si el orden de foco tiene sentido ni si un texto alternativo
 * dice algo útil), pero sí frena las regresiones obvias — contraste, labels
 * faltantes, roles mal usados, jerarquía de encabezados rota.
 *
 * El umbral es `serious`/`critical`: son los que efectivamente dejan a alguien
 * afuera. Hay menores de edad y adultos de distintas edades usando esto.
 */
const NIVELES_BLOQUEANTES = ["serious", "critical"];

/**
 * Se corre con `prefers-reduced-motion: reduce` por DOS motivos, no uno:
 *
 * 1. Determinismo: sin esto, axe mide los elementos del hero a mitad del
 *    `fade-in` y reporta contrastes falsos (llegó a medir #f2f5f8 sobre
 *    blanco para un texto que termina siendo slate-500).
 * 2. Es el escenario que importa: quien activa "reducir movimiento" tiene que
 *    ver la página igual de legible. Si un elemento solo alcanza su color
 *    final por una animación, ahí se nota.
 */
test.use({ reducedMotion: "reduce" });

async function violacionesGraves(page: import("@playwright/test").Page) {
  const resultado = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
    .analyze();

  return resultado.violations
    .filter((v) => NIVELES_BLOQUEANTES.includes(v.impact ?? ""))
    .map((v) => ({
      id: v.id,
      impact: v.impact,
      nodos: v.nodes.map((n) => n.target.join(" ")),
    }));
}

test.describe("Accesibilidad", () => {
  test(
    "la landing no tiene violaciones graves",
    { tag: ["@a11y", "@critical", "@A11Y-001"] },
    async ({ page }) => {
      await page.goto("/");
      expect(await violacionesGraves(page)).toEqual([]);
    }
  );

  test(
    "el login no tiene violaciones graves",
    { tag: ["@a11y", "@critical", "@A11Y-002"] },
    async ({ page }) => {
      await page.goto("/login");
      expect(await violacionesGraves(page)).toEqual([]);
    }
  );

  test(
    "el registro no tiene violaciones graves en ningún paso del wizard",
    { tag: ["@a11y", "@critical", "@A11Y-003"] },
    async ({ page }) => {
      // Recorre los 4 pasos: los pasos de más adelante son justamente donde se
      // escondían fallos de contraste (texto de ayuda, separador "o", números
      // de paso) que no se veían quedándose en la primera pantalla.
      await mockApi(page, {
        "POST /api/usuarios/verificar-dni": jsonRoute(204, undefined),
      });

      await page.goto("/registro");
      expect(await violacionesGraves(page)).toEqual([]);

      await page.getByRole("radio", { name: /Voy a tomar clases/ }).click();
      await page.getByRole("button", { name: "Continuar", exact: true }).click();
      expect(await violacionesGraves(page)).toEqual([]);

      await page.getByLabel("Nombre").fill("Ana");
      await page.getByLabel("Apellido").fill("Pérez");
      await page.getByLabel("DNI").fill("30111222");
      await page.getByLabel("Fecha de nacimiento").fill("1995-04-10");
      await page.getByLabel("Email").fill("ana@example.com");
      await page.getByRole("button", { name: "Continuar", exact: true }).click();
      expect(await violacionesGraves(page)).toEqual([]);

      await page.getByLabel("Foto de tu DNI (frente)").setInputFiles({
        name: "dni.png",
        mimeType: "image/png",
        buffer: Buffer.from(
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
          "base64"
        ),
      });
      await page.getByRole("button", { name: "Verificar", exact: true }).click();
      await expect(page.getByLabel("Repetí la contraseña")).toBeVisible();
      expect(await violacionesGraves(page)).toEqual([]);
    }
  );

  test(
    "la búsqueda con resultados no tiene violaciones graves",
    { tag: ["@a11y", "@A11Y-004"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
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

      await page.goto("/buscar");
      await page.getByLabel("Buscar tutores").fill("dividir polinomios");
      await page.getByRole("button", { name: "Buscar", exact: true }).click();
      await expect(page.getByRole("heading", { name: "Martín Gómez" })).toBeVisible();

      expect(await violacionesGraves(page)).toEqual([]);
    }
  );
});
