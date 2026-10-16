import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSession } from "../helpers";
import { RegistroPage } from "../registro/registro-page";
import { ultimosMeses } from "../../src/lib/cobros";

/**
 * Una fecha no cambia de día ni de hora según desde dónde se mire: todo se muestra y se compara
 * en hora argentina. El navegador está en Tokio y el reloj en 2026-10-16 01:30 UTC, que en
 * Argentina es el jueves 15/10 a las 22:30 (en Tokio ya es el 16/10 a las 10:30).
 */
const AHORA = new Date("2026-10-16T01:30:00Z");

test.describe("Zona horaria del navegador", () => {
  test.use({ timezoneId: "Asia/Tokyo" });

  test.beforeEach(async ({ page }) => {
    await page.clock.setFixedTime(AHORA);
  });

  test(
    "el panel admin muestra fecha y hora argentinas",
    { tag: ["@admin", "@ZONA-HORARIA-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
        "GET /api/admin/moderacion/denuncias": jsonRoute(200, [{
          id: "d-1", denunciadoId: "t-1", estado: "en_revision", motivo: "acoso", sesionId: null,
          descargoTexto: null, descargoVenceAt: null, slaResolucionVenceAt: "2026-10-23T03:00:00Z",
          prioridadAlta: false, resueltaAt: null, createdAt: AHORA.toISOString(), evidenciaUrl: null,
          denunciadoNombre: "Pablo", denunciadoApellido: "Sosa", denunciadoTipo: "TUTOR",
          descargoHistorial: null, conflicto: false,
        }]),
      });
      await page.goto("/admin/denuncias");
      await expect(page.getByText("Denunciado: Pablo Sosa")).toBeVisible();
      await expect(page.getByText(/15\/10,? 22:30/)).toBeVisible();
      await expect(page.getByText(/SLA vence 23\/10,? 00:00/)).toBeVisible();
      await expect(page.getByText(/16\/10,? 10:30/)).toHaveCount(0);
    }
  );

  test(
    "la fecha de nacimiento tiene como máximo el hoy de Argentina",
    { tag: ["@e2e", "@ZONA-HORARIA-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {});
      const registro = new RegistroPage(page);
      await registro.goto();
      await registro.elegirRolAdulto();
      const campo = page.getByLabel("Fecha de nacimiento");
      // En Tokio ya es 16/10, pero en Argentina todavía no: es una fecha futura.
      await campo.fill("16/10/2026");
      await campo.blur();
      await expect(page.getByText("Tiene que ser hasta el 15/10/2026")).toBeVisible();
    }
  );
});

test(
  "los meses de la exportación de cobros siguen el calendario argentino",
  { tag: ["@ZONA-HORARIA-E2E-003"] },
  () => {
    // 31/10 a las 22:00 en Argentina: en UTC (y en Europa) ya es noviembre.
    const meses = ultimosMeses(2, new Date("2026-11-01T01:00:00Z"));
    expect(meses.map((m) => m.valor)).toEqual(["2026-10", "2026-09"]);
    expect(meses[0]!.texto).toBe("Octubre de 2026");
  }
);
