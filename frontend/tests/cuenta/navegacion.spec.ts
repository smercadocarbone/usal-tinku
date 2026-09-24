import { test, expect } from "@playwright/test";
import { setFakeSessionConPayload } from "../helpers";

/**
 * B10 / UX-01 §4: una sola sección queda activa (`aria-current="page"`), tanto en la
 * navegación principal por rol como en el menú de ajustes de /cuenta. El ítem raíz
 * ("Mi cuenta" / "Perfil") no se acopla a las subrutas.
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
      const linkCuenta = nav.getByRole("link", { name: "Mi cuenta" });

      await expect(linkAgenda).toHaveAttribute("aria-current", "page");
      await expect(linkCuenta).not.toHaveAttribute("aria-current", "page");

      await linkCuenta.click();

      await expect(page).toHaveURL(/\/cuenta$/);
      await expect(linkCuenta).toHaveAttribute("aria-current", "page");
      await expect(linkAgenda).not.toHaveAttribute("aria-current", "page");
      await expect(
        page.getByRole("navigation", { name: "Secciones" }).getByRole("link", { name: "Perfil" })
      ).toHaveAttribute("aria-current", "page");
    }
  );
});
