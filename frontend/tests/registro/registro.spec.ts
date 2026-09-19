import { test, expect } from "@playwright/test";
import { RegistroPage } from "./registro-page";
import { mockApi, jsonRoute } from "../helpers";

test.describe("Registro de Usuario adulto", () => {
  test(
    "un adulto completa el wizard de 4 pasos y termina en login",
    { tag: ["@critical", "@e2e", "@registro", "@REGISTRO-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/verificar-dni": jsonRoute(204, undefined),
        "POST /api/usuarios/registro": jsonRoute(201, { id: "u-1" }),
      });

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
        fechaNacimiento: "2015-01-01",
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
