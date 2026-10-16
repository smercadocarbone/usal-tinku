import { test, expect } from "@playwright/test";
import { mockApi, setFakeSessionConPayload } from "../helpers";

/**
 * Auditoría de acceso: "Cerrar sesión" avisa al backend para que el token deje de valer
 * (POST /api/usuarios/logout) y recién después borra la sesión local. Si el backend no
 * responde, la sesión local se borra igual.
 */
test.describe("Cuenta — cerrar sesión", () => {
  for (const estado of [204, 500]) {
    test(`invalida el token en el servidor y sale (backend ${estado})`, async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      let autorizacionLogout: string | null = null;
      await mockApi(page, {
        "POST /api/usuarios/logout": async (route) => {
          autorizacionLogout = route.request().headers()["authorization"] ?? null;
          await route.fulfill({ status: estado, body: "" });
        },
      });
      await page.goto("/cuenta/horarios");

      await page.getByRole("button", { name: "Menú de tu cuenta" }).click();
      await page.getByRole("menuitem", { name: "Cerrar sesión" }).click();

      await expect(page).toHaveURL(/\/$/);
      expect(autorizacionLogout).toMatch(/^Bearer header\./);
      expect(await page.evaluate(() => window.localStorage.getItem("tinku_jwt"))).toBeNull();
    });
  }
});
