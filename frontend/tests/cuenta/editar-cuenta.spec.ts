import { test, expect } from "@playwright/test";
import { CuentaPage } from "./cuenta-page";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

function perfil(email: string) {
  return {
    id: "u-1",
    nombre: "Ana",
    apellido: "Gomez",
    tipo: "ADULTO",
    capacidadEstudiante: true,
    capacidadAdultoResponsable: false,
    email,
  };
}

test.describe("Cuenta — editar cuenta (email y contraseña)", () => {
  test(
    "actualiza el email y confirma",
    { tag: ["@critical", "@e2e", "@EDITAR-CUENTA-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO" });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil("viejo@tinku.test")),
        "PATCH /api/usuarios/me/email": jsonRoute(200, perfil("nuevo@tinku.test")),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoAcceso();

      await expect(cuenta.campoEmail).toHaveValue("viejo@tinku.test");
      await cuenta.campoEmail.fill("nuevo@tinku.test");
      await cuenta.botonGuardarEmail.click();

      await expect(page.getByText("Email actualizado.")).toBeVisible();
    }
  );

  test(
    "un email ya usado por otra cuenta se rechaza con el mensaje del backend",
    { tag: ["@e2e", "@EDITAR-CUENTA-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO" });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil("viejo@tinku.test")),
        "PATCH /api/usuarios/me/email": jsonRoute(409, {
          error: "Ya existe una cuenta registrada con ese email.",
        }),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoAcceso();

      await cuenta.campoEmail.fill("ocupado@tinku.test");
      await cuenta.botonGuardarEmail.click();

      await expect(page.getByText("Ya existe una cuenta registrada con ese email.")).toBeVisible();
    }
  );

  test(
    "cambia la contraseña y limpia el formulario",
    { tag: ["@critical", "@e2e", "@EDITAR-CUENTA-E2E-003"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO" });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil("ana@tinku.test")),
        "PATCH /api/usuarios/me/password": jsonRoute(204, {}),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoAcceso();

      await cuenta.campoPasswordActual.fill("actual12345");
      await cuenta.campoPasswordNueva.fill("nueva123456");
      await cuenta.botonCambiarPassword.click();

      await expect(page.getByText("Contraseña actualizada.")).toBeVisible();
      await expect(cuenta.campoPasswordActual).toHaveValue("");
      await expect(cuenta.campoPasswordNueva).toHaveValue("");
    }
  );

  test(
    "la contraseña actual incorrecta muestra el error sin desloguear",
    { tag: ["@e2e", "@EDITAR-CUENTA-E2E-004"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO" });
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, perfil("ana@tinku.test")),
        // 403, no 401 — un 401 dispara el logout global (manageSesion, lib/api.ts).
        "PATCH /api/usuarios/me/password": jsonRoute(403, {
          error: "La contraseña actual no es correcta.",
        }),
      });

      const cuenta = new CuentaPage(page);
      await cuenta.gotoAcceso();

      await cuenta.campoPasswordActual.fill("mala12345");
      await cuenta.campoPasswordNueva.fill("nueva123456");
      await cuenta.botonCambiarPassword.click();

      await expect(page.getByText("La contraseña actual no es correcta.")).toBeVisible();
      // Seguimos en /cuenta/acceso: un 401 hubiera limpiado la sesión y mandado a /login.
      await expect(page).toHaveURL(/\/cuenta\/acceso$/);
    }
  );
});
