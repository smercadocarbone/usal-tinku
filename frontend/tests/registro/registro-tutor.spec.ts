import { test, expect, type Route } from "@playwright/test";
import { mockApi } from "../helpers";

const FOTO_FAKE = {
  name: "dni-frente.png",
  mimeType: "image/png",
  buffer: Buffer.from(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
    "base64"
  ),
};

test.describe("Registro de Tutor", () => {
  test(
    "el alta de tutor manda el email en el body (sin email el backend rechaza con 400)",
    { tag: ["@critical", "@e2e", "@REGISTRO-TUTOR-E2E-001"] },
    async ({ page }) => {
      let cuerpoEnviado: string | null = null;
      const capturarRegistro = async (route: Route) => {
        cuerpoEnviado = route.request().postData();
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({ id: "u-1" }),
        });
      };

      await mockApi(page, {
        "POST /api/tutores/verificar-dni": async (route) => {
          await route.fulfill({ status: 204 });
        },
        "POST /api/tutores/registro": capturarRegistro,
      });

      await page.goto("/registro/tutor");
      await page.getByLabel("Nombre").fill("Jorge");
      await page.getByLabel("Apellido").fill("Gómez");
      await page.getByLabel("DNI", { exact: true }).fill("30224455");
      await page.getByLabel("Fecha de nacimiento").fill("1985-06-10");
      await page.getByLabel("Email").fill("jorge.gomez@example.com");
      await page.getByRole("button", { name: "Continuar", exact: true }).click();
      await page.getByLabel("Foto de tu DNI (frente o dorso)").setInputFiles(FOTO_FAKE);
      await page.getByRole("button", { name: "Verificar", exact: true }).click();
      // Términos propios del tutor, distintos de los del alumno.
      await expect(page.getByRole("heading", { name: "Sos un tutor independiente" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Tu foto es obligatoria" })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Pagás al reservar, el tutor cobra después" })).toHaveCount(0);
      await page.getByLabel("Acepto los Términos y Condiciones").check();
      await page.getByRole("button", { name: "Continuar", exact: true }).click();
      await page.getByLabel("Contraseña", { exact: true }).fill("unaClaveSegura1");
      await page.getByLabel("Repetí la contraseña").fill("unaClaveSegura1");
      await page.getByRole("button", { name: "Crear cuenta", exact: true }).click();

      await expect(page.getByRole("heading", { name: "Cuenta de tutor creada" })).toBeVisible();

      expect(cuerpoEnviado).not.toBeNull();
      expect(cuerpoEnviado).toContain('"email"');
      expect(cuerpoEnviado).toContain("jorge.gomez@example.com");
    }
  );
});