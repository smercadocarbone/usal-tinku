import { test, expect } from "@playwright/test";
import { rutaAdminPermitida } from "../../src/lib/navegacion";
import { inicioPorRol } from "../../src/lib/auth";
import { jsonRoute, mockApi, setFakeSession } from "../helpers";

/**
 * Chunk REM-F (docs/superpowers/specs/remediacion-pruebas/R-F-admin-moderacion.md): salud por rol
 * (T4-H5), cola de denuncias con evidencia y conflicto (T4-H2/H7), sanción sin preelegir (T4-H9),
 * secciones por rol (T4-H6), inicio del admin (T4-H10) y precios vacíos (T5-H4).
 */

const DENUNCIA = {
  id: "d-1",
  denunciadoId: "t-1",
  estado: "en_revision",
  motivo: "acoso",
  sesionId: null,
  descargoTexto: null,
  descargoVenceAt: null,
  slaResolucionVenceAt: null,
  prioridadAlta: false,
  resueltaAt: null,
  createdAt: "2026-10-14T12:00:00Z",
  evidenciaUrl: "https://ejemplo.com/captura.png",
  denunciadoNombre: "Pablo",
  denunciadoApellido: "Sosa",
  denunciadoTipo: "TUTOR",
  descargoHistorial: null,
  conflicto: false,
};

test.describe("Panel admin por rol — regla", () => {
  test("cada rol abre sus secciones; salud y tickets, los dos", { tag: ["@admin", "@ADMIN-F-001"] }, () => {
    const casos: [string, "moderacion_seguridad" | "soporte_financiero", boolean][] = [
      ["/admin/pagos", "soporte_financiero", true],
      ["/admin/pagos", "moderacion_seguridad", false],
      ["/admin/precios", "soporte_financiero", true],
      ["/admin/precios", "moderacion_seguridad", false],
      ["/admin/denuncias", "moderacion_seguridad", true],
      ["/admin/denuncias", "soporte_financiero", false],
      ["/admin/antecedentes", "soporte_financiero", false],
      ["/admin/alertas", "moderacion_seguridad", true],
      ["/admin/salud", "moderacion_seguridad", true],
      ["/admin/salud", "soporte_financiero", true],
      ["/admin/tickets", "moderacion_seguridad", true],
      ["/admin", "soporte_financiero", true],
    ];
    for (const [ruta, rol, esperado] of casos) expect(rutaAdminPermitida(ruta, rol), `${ruta} ${rol}`).toBe(esperado);
  });

  test("el admin entra al panel; el menor nunca", { tag: ["@admin", "@ADMIN-F-002"] }, () => {
    expect(inicioPorRol({ sub: "a", tipo: "ADULTO" }, "moderacion_seguridad")).toBe("/admin");
    expect(inicioPorRol({ sub: "a", tipo: "ADULTO" }, null)).toBe("/cuenta/reservas");
    expect(inicioPorRol({ sub: "m", tipo: "MENOR" }, "moderacion_seguridad")).toBe("/cuenta/reservas");
  });
});

test.describe("Panel admin — pantallas", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test("Moderación ve la salud aunque la pasarela le dé 403", { tag: ["@admin", "@ADMIN-F-003"] }, async ({ page }) => {
    let pidioPasarela = false;
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      "GET /api/admin/salud": jsonRoute(200, {
        isTestMode: false,
        hasSeedData: false,
        ...Object.fromEntries(
          ["ocrEngine", "mercadoPago", "liveKit", "iaMatching", "database"].map((k) => [
            k,
            { name: k, status: "operational", lastChecked: "2026-10-15T00:00:00Z" },
          ])
        ),
      }),
      "GET /api/admin/financiero/pasarela": async (route) => {
        pidioPasarela = true;
        await route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({ error: "No autorizado" }) });
      },
    });
    await page.goto("/admin/salud");
    await expect(page.getByRole("heading", { name: "Salud de Infraestructura" })).toBeVisible();
    await expect(page.getByText("No autorizado")).toHaveCount(0);
    await expect(page.getByText("Pasarela de pagos (MercadoPago)")).toHaveCount(0);
    expect(pidioPasarela).toBe(false);
  });

  test("Moderación en /admin/pagos ve el aviso y no pide nada financiero", { tag: ["@admin", "@ADMIN-F-004"] }, async ({ page }) => {
    const financiero: string[] = [];
    page.on("request", (r) => {
      if (r.url().includes("/api/admin/financiero/")) financiero.push(r.url());
    });
    await mockApi(page, { "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }) });
    await page.goto("/admin/pagos");
    await expect(page.getByText("Esta sección es de Soporte Financiero")).toBeVisible();
    expect(financiero).toEqual([]);
  });

  test("la cola muestra al denunciado y la evidencia con rel seguro", { tag: ["@admin", "@ADMIN-F-005"] }, async ({ page }) => {
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      "GET /api/admin/moderacion/denuncias": jsonRoute(200, [DENUNCIA]),
    });
    await page.goto("/admin/denuncias");
    await expect(page.getByText("Denunciado: Pablo Sosa")).toBeVisible();
    const enlace = page.getByRole("link", { name: "Ver evidencia" });
    await expect(enlace).toHaveAttribute("rel", "noopener noreferrer");
    await expect(enlace).toHaveAttribute("target", "_blank");
  });

  test("en un caso propio no se puede resolver", { tag: ["@admin", "@ADMIN-F-006"] }, async ({ page }) => {
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      "GET /api/admin/moderacion/denuncias": jsonRoute(200, [{ ...DENUNCIA, conflicto: true }]),
    });
    await page.goto("/admin/denuncias");
    await expect(page.getByRole("button", { name: "Resolver" })).toBeDisabled();
    await expect(page.getByText("Sos parte de este caso: lo tiene que resolver otro admin.")).toBeVisible();
  });

  test("fundada exige elegir la sanción y confirmar antes de resolver", { tag: ["@admin", "@ADMIN-F-007"] }, async ({ page }) => {
    let cuerpo: unknown = null;
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
      "GET /api/admin/moderacion/denuncias": jsonRoute(200, [DENUNCIA]),
      "POST /api/admin/moderacion/denuncias/d-1/resolver": async (route) => {
        cuerpo = route.request().postDataJSON();
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(DENUNCIA) });
      },
    });
    await page.goto("/admin/denuncias");
    await page.getByRole("button", { name: "Resolver" }).click();
    await page.getByRole("radio", { name: "Fundada", exact: true }).check();
    const confirmarBtn = page.getByRole("button", { name: "Confirmar resolución" });
    await expect(confirmarBtn).toBeDisabled();
    await page.getByLabel("Tipo de sanción").selectOption("advertencia");
    await confirmarBtn.click();
    await expect(page.getByText(/Resolver como fundada con advertencia para Pablo Sosa\? No se puede deshacer\./)).toBeVisible();
    expect(cuerpo).toBeNull();
    await page.getByRole("button", { name: "Sí, resolver" }).click();
    await expect.poll(() => cuerpo).toEqual({ resolucion: "fundada", tipoSancion: "advertencia" });
  });

  test("sin precios cargados, explica qué cargar", { tag: ["@admin", "@ADMIN-F-008"] }, async ({ page }) => {
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "soporte_financiero" }),
      "GET /api/admin/financiero/precios-regionales": jsonRoute(200, []),
      "GET /api/admin/financiero/pasarela": jsonRoute(200, { habilitada: true, bypassPermitido: false }),
    });
    await page.goto("/admin/precios");
    await expect(page.getByText(/Todavía no hay precios cargados/)).toBeVisible();
  });
});
