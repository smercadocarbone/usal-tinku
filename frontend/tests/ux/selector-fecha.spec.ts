import { test, expect } from "@playwright/test";
import { mockApi } from "../helpers";
import { RegistroPage } from "../registro/registro-page";

/**
 * Selector de fecha propio (reemplaza a `<input type="date">`): se puede escribir o elegir en el
 * calendario, se navega por mes y año, y respeta el máximo. Se prueba en la fecha de nacimiento
 * del registro, el caso más exigente (años lejanos).
 */
test.describe("Selector de fecha", () => {
  test(
    "se elige en el calendario con mes y año, se escribe en DD/MM/AAAA y respeta el máximo",
    { tag: ["@e2e", "@ux", "@a11y", "@SELECTOR-FECHA-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {});
      const registro = new RegistroPage(page);
      await registro.goto();
      await registro.elegirRolAdulto();

      const campo = page.getByLabel("Fecha de nacimiento");
      await page.getByRole("button", { name: "Abrir calendario" }).click();
      const calendario = page.getByRole("dialog", { name: "Elegí una fecha" });
      await expect(calendario).toBeVisible();
      await calendario.getByLabel("Año", { exact: true }).selectOption("1995");
      await calendario.getByLabel("Mes", { exact: true }).selectOption({ label: "abril" });
      await calendario.getByRole("button", { name: "lunes 10 de abril de 1995" }).click();
      await expect(calendario).toBeHidden();
      await expect(campo).toHaveValue("10/04/1995");

      // Escribiendo también anda, y con el teclado se abre y se cierra.
      await campo.fill("02/02/1985");
      await campo.blur();
      await expect(campo).toHaveValue("02/02/1985");
      await campo.focus();
      await campo.press("Alt+ArrowDown");
      await expect(calendario).toBeVisible();
      await page.keyboard.press("ArrowRight");
      await page.keyboard.press("Enter");
      await expect(campo).toHaveValue("03/02/1985");

      // Después de hoy no se puede (máximo): el día siguiente del calendario está deshabilitado.
      const manana = new Date(Date.now() + 86400000);
      const dd = String(manana.getDate()).padStart(2, "0");
      const mm = String(manana.getMonth() + 1).padStart(2, "0");
      await campo.fill(`${dd}/${mm}/${manana.getFullYear()}`);
      await expect(page.getByText(/Tiene que ser hasta el/)).toBeVisible();
    }
  );
});
