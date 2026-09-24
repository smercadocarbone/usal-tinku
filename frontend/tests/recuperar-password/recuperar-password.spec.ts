import { test, expect } from "@playwright/test";
import { RecuperarPasswordPage, ResetearPasswordPage } from "./recuperar-password-page";
import { mockApi, jsonRoute } from "../helpers";

test.describe("Recuperar contraseña — solicitud", () => {
  test(
    "el flujo automático está deshabilitado y no promete un email que no sale (B9)",
    { tag: ["@critical", "@e2e", "@RECUPERAR-PASSWORD-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/recuperar-password": (_route) => {
          throw new Error("El flujo deshabilitado no debe llamar al backend");
        },
      });

      const recuperar = new RecuperarPasswordPage(page);
      await recuperar.goto();

      await expect(
        page.getByText("Todavía no mandamos emails de recuperación")
      ).toBeVisible();
      await expect(page.getByLabel("DNI", { exact: true })).toHaveCount(0);
      await expect(page.getByRole("button", { name: "Enviar enlace de recuperación" })).toHaveCount(0);
    }
  );
});

test.describe("Resetear contraseña — con el token del enlace", () => {
  test(
    "sin token en la URL, no ofrece el formulario",
    { tag: ["@e2e", "@RESETEAR-PASSWORD-E2E-001"] },
    async ({ page }) => {
      const resetear = new ResetearPasswordPage(page);
      await resetear.goto();

      await expect(page.getByText("Este enlace ya no es válido.")).toBeVisible();
      await expect(resetear.campoPasswordNueva).toHaveCount(0);
    }
  );

  test(
    "contraseñas que no coinciden se bloquean sin llamar al backend",
    { tag: ["@e2e", "@RESETEAR-PASSWORD-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {}); // cualquier POST acá sería un fallo del test

      const resetear = new ResetearPasswordPage(page);
      await resetear.goto("token-valido");

      await resetear.campoPasswordNueva.fill("passwordUno1");
      await resetear.campoConfirmacion.fill("passwordDos2");
      await resetear.botonGuardar.click();

      await expect(page.getByText("Las contraseñas no coinciden.")).toBeVisible();
    }
  );

  test(
    "con token válido, cambia la contraseña y confirma",
    { tag: ["@critical", "@e2e", "@RESETEAR-PASSWORD-E2E-003"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/resetear-password": jsonRoute(204, {}),
      });

      const resetear = new ResetearPasswordPage(page);
      await resetear.goto("token-valido");

      await resetear.campoPasswordNueva.fill("passwordNueva1");
      await resetear.campoConfirmacion.fill("passwordNueva1");
      await resetear.botonGuardar.click();

      await expect(page.getByText("Tu contraseña se actualizó.")).toBeVisible();
    }
  );

  test(
    "un token inválido o vencido muestra el mensaje del backend",
    { tag: ["@e2e", "@RESETEAR-PASSWORD-E2E-004"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/resetear-password": jsonRoute(422, {
          error: "El enlace no es válido o ya expiró. Solicitá uno nuevo.",
        }),
      });

      const resetear = new ResetearPasswordPage(page);
      await resetear.goto("token-vencido");

      await resetear.campoPasswordNueva.fill("passwordNueva1");
      await resetear.campoConfirmacion.fill("passwordNueva1");
      await resetear.botonGuardar.click();

      await expect(page.getByText("El enlace no es válido o ya expiró. Solicitá uno nuevo.")).toBeVisible();
    }
  );
});
