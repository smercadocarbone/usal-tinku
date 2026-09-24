import { test, expect } from "@playwright/test";
import { setFakeSessionConPayload } from "../helpers";

/**
 * B10 parte 1: en el shell de /cuenta solo la sección de la URL actual debe
 * quedar activa (`aria-current="page"`). El ítem raíz ("Perfil", href == base)
 * quedaba activo también en cualquier subruta (p. ej. /cuenta/horarios).
 */
test.describe("Cuenta — navegación del SettingsShell", () => {
  test(
    "solo la sección actual queda activa; el ítem raíz no se acopla a subrutas",
    { tag: ["@a11y", "@CUENTA-MENU-B10-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await page.goto("/cuenta/horarios");

      const nav = page.getByRole("navigation", { name: "Secciones" });
      const linkHorarios = nav.getByRole("link", { name: "Mis Horarios" });
      const linkPerfil = nav.getByRole("link", { name: "Perfil" });

      await expect(linkHorarios).toHaveAttribute("aria-current", "page");
      await expect(linkPerfil).not.toHaveAttribute("aria-current", "page");

      await linkPerfil.click();

      await expect(page).toHaveURL(/\/cuenta$/);
      await expect(linkPerfil).toHaveAttribute("aria-current", "page");
      await expect(linkHorarios).not.toHaveAttribute("aria-current", "page");
    }
  );
});