import { test, expect } from "@playwright/test";
import { RecuperarPasswordPage, ResetearPasswordPage } from "./recuperar-password-page";
import { mockApi, jsonRoute } from "../helpers";

test.describe("Recuperar contraseña — solicitud", () => {
  test(
    "pedir el enlace muestra siempre el mismo mensaje de éxito",
    { tag: ["@critical", "@e2e", "@RECUPERAR-PASSWORD-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "POST /api/usuarios/recuperar-password": jsonRoute(204, {}),
      });

      const recuperar = new RecuperarPasswordPage(page);
      await recuperar.goto();

      await recuperar.campoDni.fill("12345678");
      await recuperar.botonEnviar.click();

      await expect(
        page.getByText("Si el DNI está registrado, vas a recibir un enlace de recuperación en breve.")
      ).toBeVisible();
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

      await expect(page.getByText("Este enlace no es válido.")).toBeVisible();
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
