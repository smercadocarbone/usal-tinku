import { test, expect } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

/**
 * B4 — la agenda del tutor se pide con el UUID del Tutor, no con el DNI.
 *
 * El `sub` del JWT es hoy el DNI (FASE3-03 no está mergeada): mandarlo como
 * `tutorId` en GET /api/tutores/{dni}/franjas da 403 y la grilla queda vacía.
 * El id sale de GET /api/usuarios/me. Este test intercepta la llamada a
 * `/api/tutores/:id/franjas` y exige que `:id` sea el UUID devuelto por /me,
 * nunca el DNI del `sub`.
 */

const DNI_TUTOR = "30224455";

const PERFIL_TUTOR = {
  id: "u-abcfranjas",
  nombre: "Jorge",
  apellido: "Test",
  tipo: "TUTOR",
  capacidadEstudiante: false,
  capacidadAdultoResponsable: true,
  email: "jorge@test.com",
};

const FRANJAS = [
  {
    id: "f1",
    diaSemana: 1,
    fechaEspecifica: null,
    horaInicio: "09:00",
    horaFin: "11:00",
    activa: true,
  },
];

test.describe("Cuenta — Mis Horarios (agenda del tutor)", () => {
  test(
    "la agenda se pide con el id que devuelve /api/usuarios/me, no con el DNI del sub",
    { tag: ["@critical", "@e2e", "@cuenta", "@HORARIOS-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, {
        sub: DNI_TUTOR,
        tipo: "TUTOR",
        cap_ar: true,
      });

      let tutorIdUsado: string | null = null;
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, PERFIL_TUTOR),
        "GET /api/tutores/:id/franjas": async (route) => {
          const url = new URL(route.request().url());
          const m = url.pathname.match(/^\/api\/tutores\/([^/]+)\/franjas$/);
          tutorIdUsado = m ? decodeURIComponent(m[1]) : null;
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify(FRANJAS),
          });
        },
      });

      await page.goto("/cuenta/horarios");
      await page.waitForLoadState("networkidle");

      await expect(page.getByText("de 09:00 a 11:00")).toBeVisible();
      expect(tutorIdUsado).toBe(PERFIL_TUTOR.id);
      expect(tutorIdUsado).not.toBe(DNI_TUTOR);
    }
  );
});