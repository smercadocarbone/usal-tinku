import { test, expect } from "@playwright/test";
import { setFakeSessionConPayload } from "../helpers";

/**
 * B10 / UX-01 §4: una sola sección queda activa (`aria-current="page"`), tanto en la
 * navegación principal por rol como en el menú de ajustes de /cuenta. El ítem raíz
 * ("Perfil") no se acopla a las subrutas. "Mi cuenta" no está en la barra de arriba de
 * escritorio (estaba repetido): se entra desde el menú del avatar.
 */
test.describe("Cuenta — navegación", () => {
  test(
    "solo la sección actual queda activa; el ítem raíz no se acopla a subrutas",
    { tag: ["@a11y", "@CUENTA-MENU-B10-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await page.goto("/cuenta/horarios");

      const nav = page.getByRole("navigation", { name: "Principal" });
      const linkAgenda = nav.getByRole("link", { name: "Mi agenda" });

      await expect(linkAgenda).toHaveAttribute("aria-current", "page");
      await expect(nav.getByRole("link", { name: "Mi cuenta" })).toHaveCount(0);

      await page.getByRole("button", { name: "Menú de tu cuenta" }).click();
      await page.getByRole("menuitem", { name: "Mi cuenta" }).click();

      await expect(page).toHaveURL(/\/cuenta$/);
      await expect(linkAgenda).not.toHaveAttribute("aria-current", "page");
      await expect(
        page.getByRole("navigation", { name: "Secciones" }).getByRole("link", { name: "Perfil" })
      ).toHaveAttribute("aria-current", "page");
    }
  );
});
