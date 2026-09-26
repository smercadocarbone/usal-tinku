import { test, expect } from "@playwright/test";
import { RegistroPage } from "./registro-page";
import { mockApi, jsonRoute } from "../helpers";

test.describe("Registro de Usuario adulto", () => {
  test(
    "un adulto completa el wizard de 5 pasos y, si no se puede abrir la sesión sola, termina en login",
    { tag: ["@critical", "@e2e", "@registro", "@REGISTRO-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/verificar-dni": jsonRoute(204, undefined),
        "POST /api/usuarios/registro": jsonRoute(201, { id: "u-1" }),
      });

      // ADR-M3-05: el alta manda la aceptación de los Términos (el backend la exige).
      const alta = page.waitForRequest((r) => r.url().endsWith("/api/usuarios/registro"));
      const registro = new RegistroPage(page);
      await registro.goto();
      await registro.registrarAdultoCompleto({
        nombre: "Ana",
        apellido: "Pérez",
        dni: "30111222",
        fechaNacimiento: "1995-04-10",
        email: "ana.perez@example.com",
        password: "unaClaveSegura1",
      });

      await expect(page).toHaveURL(/\/login\?registrado=1/);
      expect((await alta).postDataBuffer()?.toString()).toContain('"aceptaTerminos":true');
    }
  );

  test(
    "con la sesión abierta sola, un adulto con hijos a cargo termina en la bienvenida con el paso de sumar a su hijo",
    { tag: ["@e2e", "@registro", "@REGISTRO-E2E-003"] },
    async ({ page }) => {
      let cuerpo: string | null = null;
      await mockApi(page, {
        "POST /api/usuarios/verificar-dni": jsonRoute(204, undefined),
        "POST /api/usuarios/registro": async (route) => {
          cuerpo = route.request().postData();
          await route.fulfill({ status: 201, contentType: "application/json", body: '{"id":"u-1"}' });
        },
        "POST /api/usuarios/login": jsonRoute(200, { token: "h.eyJ0aXBvIjoiQURVTFRPIn0.s", tipo: "ADULTO", expiresInMinutes: 60 }),
      });

      const registro = new RegistroPage(page);
      await registro.goto();
      await page.getByRole("radio", { name: /Tengo un hijo o hija a cargo/ }).click();
      await registro.botonContinuar.click();
      await registro.completarDatosPersonales({
        nombre: "Carla", apellido: "Ruiz", dni: "30111333", fechaNacimiento: "1985-02-02",
        email: "carla@example.com", password: "",
      });
      await registro.verificarIdentidad();
      await registro.aceptarCondiciones();
      await registro.crearAcceso("unaClaveSegura1");

      await expect(page.getByRole("heading", { name: /Bienvenido\/a, Carla/ })).toBeVisible();
      await expect(page.getByRole("link", { name: "Sumar a mi hijo o hija" })).toBeVisible();
      // "Tengo un hijo o hija a cargo" = solo Adulto Responsable.
      expect(cuerpo).toContain('"capacidadAdultoResponsable":true');
      expect(cuerpo).toContain('"capacidadEstudiante":false');
    }
  );

  test(
    "una fecha de nacimiento de menor se frena antes de subir el DNI",
    { tag: ["@e2e", "@registro", "@REGISTRO-E2E-004"] },
    async ({ page }) => {
      await mockApi(page, {});
      const registro = new RegistroPage(page);
      await registro.goto();
      await registro.elegirRolAdulto();
      await page.getByLabel("Fecha de nacimiento").fill("2015-01-01");
      await expect(page.getByText("Tenés que ser mayor de 18.", { exact: false })).toBeVisible();
      await expect(registro.botonContinuar).toBeDisabled();
    }
  );

  test(
    "el DNI verificado como menor de edad no crea ninguna cuenta (Artículo II)",
    { tag: ["@critical", "@e2e", "@registro", "@REGISTRO-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/verificar-dni": jsonRoute(403, {
          error: "Sos menor de edad. Un Adulto Responsable debe crear tu perfil.",
        }),
      });

      const registro = new RegistroPage(page);
      await registro.goto();
      await registro.elegirRolAdulto();
      await registro.completarDatosPersonales({
        nombre: "Juan",
        apellido: "Gómez",
        dni: "50111222",
        // El OCR del backend es el que manda: la fecha declarada puede ser adulta y
        // el DNI igual decir que es menor (403).
        fechaNacimiento: "1990-01-01",
        email: "",
        password: "",
      });
      await registro.verificarIdentidad();

      await expect(page.getByText("Sos menor de edad.")).toBeVisible();
      // Se queda en /registro — no hay redirect a /login, no se creó cuenta.
      await expect(page).toHaveURL(/\/registro$/);
    }
  );
});
