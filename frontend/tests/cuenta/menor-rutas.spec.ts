import { test, expect } from "@playwright/test";
import type { PayloadSesion } from "../../src/lib/auth";
import { rutaCuentaPermitida } from "../../src/lib/navegacion";
import { jsonRoute, mockApi, setFakeSessionConPayload } from "../helpers";

/**
 * T3-03, T5-1 y T7-M3 (pruebas en producción, 2026-10-14; M3C-10 H-4): cada perfil abre solo las
 * pantallas que le corresponden, y quien no es admin no queda colgado en "Cargando colas…".
 */
const menor: PayloadSesion = { sub: "m-1", tipo: "MENOR", cap_est: true, exp: 4102444800 };
const adulto: PayloadSesion = { sub: "a-1", tipo: "ADULTO", cap_est: true, cap_ar: true, exp: 4102444800 };
const tutor: PayloadSesion = { sub: "t-1", tipo: "TUTOR", exp: 4102444800 };

test.describe("Rutas de Mi cuenta por perfil — regla", () => {
  test("el menor solo ve su perfil, su acceso, sus clases y sus avisos", { tag: ["@cuenta", "@RUTAS-001"] }, () => {
    for (const r of ["/cuenta", "/cuenta/perfil", "/cuenta/acceso", "/cuenta/reservas", "/cuenta/reservas/abc", "/cuenta/notificaciones"]) {
      expect(rutaCuentaPermitida(r, menor), r).toBe(true);
    }
    for (const r of ["/cuenta/menores", "/cuenta/horarios", "/cuenta/cobros", "/cuenta/clases-con-menores", "/cuenta/precio", "/cuenta/seguridad"]) {
      expect(rutaCuentaPermitida(r, menor), r).toBe(false);
    }
  });

  test("un adulto no abre pantallas de Tutor; un Tutor sí", { tag: ["@cuenta", "@RUTAS-002"] }, () => {
    for (const r of ["/cuenta/clases-con-menores", "/cuenta/horarios", "/cuenta/cobros", "/cuenta/precio"]) {
      expect(rutaCuentaPermitida(r, adulto), r).toBe(false);
      expect(rutaCuentaPermitida(r, tutor), r).toBe(true);
    }
    expect(rutaCuentaPermitida("/cuenta/menores", adulto)).toBe(true);
    expect(rutaCuentaPermitida("/cuenta/reservas", adulto)).toBe(true);
  });
});

test.describe("Rutas de Mi cuenta por perfil — pantalla", () => {
  test("el menor que abre /cuenta/menores vuelve a /cuenta", { tag: ["@cuenta", "@RUTAS-003"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { ...menor });
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, { id: "m-1", nombre: "Mía", tipo: "MENOR" }),
      "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
    });
    await page.goto("/cuenta/menores");
    await expect(page).toHaveURL(/\/cuenta$/);
  });

  test("un adulto que abre /cuenta/clases-con-menores vuelve a /cuenta y no pide estado-perfil", { tag: ["@cuenta", "@RUTAS-004"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { ...adulto });
    const pedidos: string[] = [];
    page.on("request", (r) => pedidos.push(r.url()));
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, { id: "a-1", nombre: "Hernán", tipo: "ADULTO" }),
      "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
      "GET /api/denuncias/recibidas": jsonRoute(200, []),
      "GET /api/alertas-seguridad/mias": jsonRoute(200, []),
    });
    await page.goto("/cuenta/clases-con-menores");
    await expect(page).toHaveURL(/\/cuenta$/);
    expect(pedidos.some((u) => u.includes("/api/tutores/me/estado-perfil"))).toBe(false);
  });

  test("quien no es admin ve un aviso en /admin, no 'Cargando colas…'", { tag: ["@admin", "@RUTAS-005"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { ...adulto });
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, { id: "a-1", nombre: "Hernán", tipo: "ADULTO" }),
      "GET /api/admin/yo": jsonRoute(403, { error: "No autorizado para esta acción de administración." }),
      "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
    });
    await page.goto("/admin");
    await expect(page.getByText("Esta sección es solo para el equipo de Tinku")).toBeVisible();
    await expect(page.getByText("Cargando colas")).toHaveCount(0);
  });
});
