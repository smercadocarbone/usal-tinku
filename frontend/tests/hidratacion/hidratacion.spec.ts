import { test, expect, type Page, type Route } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";

type Handlers = Record<string, (route: Route) => void | Promise<void>>;

/**
 * B3 — regresión de hidratación.
 *
 * Antes del fix, cada página de /cuenta (y /resetear-password) leía
 * localStorage/window.location en el render: el servidor pintaba sin sesión y
 * el primer render del cliente con sesión → React lanzaba
 * "Hydration failed because the server rendered HTML didn't match".
 *
 * Esta spec visita las rutas reportadas y exige CERO pageerror y CERO
 * mensajes de consola de hidratación. Son regresiones puras del frontend: solo
 * pagan con que el markup hidrate sin errores.
 */

const SESION_TUTOR_AR = {
  sub: "12345678",
  tipo: "TUTOR",
  cap_est: true,
  cap_ar: true,
  exp: 9999999999,
};

const PERFIL_TUTOR = {
  id: "u-1",
  nombre: "Test",
  apellido: "Tutor",
  tipo: "TUTOR",
  capacidadEstudiante: true,
  capacidadAdultoResponsable: true,
  email: "tutor@test.com",
};

const RESERVA = {
  id: "r-1",
  pagadorId: "u-1",
  beneficiarioId: "u-1",
  tutorId: "t-1",
  horario: "2026-01-01T15:00:00Z",
  precio: 15000,
  estado: "confirmada",
  motivoCancelacion: null,
};

const SESION = {
  id: "s-1",
  reservaId: "r-1",
  estado: "no_iniciada",
  livekitRoomId: "sesion-s-1",
  inicioReal: null,
  finReal: null,
  duracionEfectivaSegundos: null,
};

async function esperarSinErroresDeHidratacion(
  page: Page,
  pageErrors: Error[],
  consoleErrores: string[]
): Promise<void> {
  await page.waitForLoadState("networkidle");
  for (const msg of pageErrors) {
    expect(msg).toBeUndefined();
  }
  for (const text of consoleErrores) {
    expect(text).not.toMatch(/[Hh]ydrat|[Mm]ismatch/);
  }
}

test.describe("Hidratación SSR — rutas de /cuenta y /resetear-password", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, SESION_TUTOR_AR);
  });

  for (const ruta of [
    { path: "/cuenta", handlers: { "GET /api/usuarios/me": jsonRoute(200, PERFIL_TUTOR) } },
    {
      path: "/cuenta/horarios",
      handlers: { "GET /api/tutores/12345678/franjas": jsonRoute(200, []) },
    },
    {
      path: "/cuenta/precio",
      handlers: {
        "GET /api/pagos/precio-referencia/Buenos Aires": jsonRoute(200, {
          provincia: "Buenos Aires",
          valorSugerido: 20000,
          version: 1,
          vigenteDesde: "2026-01-01T00:00:00Z",
        }),
      },
    },
    {
      path: "/cuenta/menores",
      handlers: { "GET /api/solicitudes/pendientes": jsonRoute(200, []) },
    },
    { path: "/cuenta/reservas", handlers: { "GET /api/reservas": jsonRoute(200, []) } },
    {
      path: "/cuenta/reservas/r-1",
      handlers: {
        "GET /api/reservas/r-1": jsonRoute(200, RESERVA),
        "GET /api/sesiones/por-reserva/r-1": jsonRoute(200, SESION),
      },
    },
    { path: "/resetear-password?token=abc123", handlers: {} },
    { path: "/login?expirado=1", handlers: {} },
  ] as { path: string; handlers: Handlers }[]) {
    test(`hidrata sin errores en ${ruta.path} @e2e @HIDRATACION-E2E-001`, async ({ page }) => {
      const pageErrors: Error[] = [];
      const consoleErrores: string[] = [];
      page.on("pageerror", (err) => pageErrors.push(err));
      page.on("console", (msg) => {
        if (msg.type() === "error") consoleErrores.push(msg.text());
      });

      await mockApi(page, ruta.handlers);
      await page.goto(ruta.path);
      await esperarSinErroresDeHidratacion(page, pageErrors, consoleErrores);
    });
  }
});