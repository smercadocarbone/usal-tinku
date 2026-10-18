import { test, expect, type Route } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSessionConPayload } from "../helpers";
import { BuscarPage } from "../buscar/buscar-page";

/**
 * T-TES-11b (docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md §4): NPS in-app, panel de
 * Admin "Evaluación" y aceptación de sugerencias (I-01). La API está mockeada; las reglas (una vez
 * por usuario, nunca a un Menor, flag) las prueba el backend.
 */

const ADULTO_ME = { id: "u-1", nombre: "Lucía", apellido: "Díaz", tipo: "ADULTO", capacidadEstudiante: true, capacidadAdultoResponsable: false, email: "l@test.tinku" };

const BASE_CUENTA = {
  "GET /api/usuarios/me": jsonRoute(200, ADULTO_ME),
  "GET /api/reservas": jsonRoute(200, []),
  "GET /api/solicitudes": jsonRoute(200, []),
  "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
};

test.describe("Evaluación sin piloto (T-TES-11b)", () => {
  test("el NPS aparece después de una clase y manda el puntaje elegido", { tag: ["@evaluacion", "@T11B-NPS-001"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_est: true });
    let enviado: unknown = null;
    await mockApi(page, {
      ...BASE_CUENTA,
      "GET /api/evaluacion/nps/pendiente": jsonRoute(200, { ofrecer: true }),
      "POST /api/evaluacion/nps": async (route: Route) => {
        enviado = route.request().postDataJSON();
        await jsonRoute(201, { puntaje: 9 })(route);
      },
    });
    await page.goto("/cuenta/reservas");

    const modal = page.getByRole("dialog", { name: "¿Recomendarías Tinku?" });
    await expect(modal).toBeVisible();
    await expect(modal.getByRole("button", { name: "Enviar" })).toBeDisabled();
    await modal.getByRole("radio", { name: "9" }).click();
    await modal.getByRole("button", { name: "Enviar" }).click();

    await expect(modal).toBeHidden();
    await expect(page.getByText("¡Gracias por tu respuesta!")).toBeVisible();
    expect(enviado).toEqual({ puntaje: 9 });
  });

  test("\"Ahora no\" cierra sin responder y no vuelve en la misma pestaña", { tag: ["@evaluacion", "@T11B-NPS-002"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_est: true });
    let posts = 0;
    let consultas = 0;
    await mockApi(page, {
      ...BASE_CUENTA,
      "GET /api/evaluacion/nps/pendiente": async (route: Route) => {
        consultas++;
        await jsonRoute(200, { ofrecer: true })(route);
      },
      "POST /api/evaluacion/nps": async (route: Route) => {
        posts++;
        await route.fulfill({ status: 201 });
      },
    });
    await page.goto("/cuenta/reservas");
    const modal = page.getByRole("dialog", { name: "¿Recomendarías Tinku?" });
    await modal.getByRole("button", { name: "Ahora no" }).click();
    await expect(modal).toBeHidden();
    const consultasAntes = consultas;

    await page.reload();
    await expect(page.getByRole("heading", { level: 1 }).first()).toBeVisible();
    await expect(modal).toBeHidden();
    expect(posts).toBe(0);
    // Pospuesto en esta pestaña: después de recargar ni siquiera se vuelve a consultar.
    expect(consultas).toBe(consultasAntes);
  });

  test("con la evaluación apagada (404) no aparece nada", { tag: ["@evaluacion", "@T11B-NPS-003"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_est: true });
    await mockApi(page, BASE_CUENTA); // /api/evaluacion/nps/pendiente sin mock → 404
    await page.goto("/cuenta/reservas");
    await expect(page.getByRole("heading", { level: 1 }).first()).toBeVisible();
    await expect(page.getByRole("dialog", { name: "¿Recomendarías Tinku?" })).toHaveCount(0);
  });

  test("a una cuenta Menor nunca se le pregunta", { tag: ["@evaluacion", "@seguridad-menor", "@T11B-NPS-004"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "MENOR", sub: "m-1", cap_est: true });
    const pedidos: string[] = [];
    page.on("request", (r) => pedidos.push(r.url()));
    await mockApi(page, {
      ...BASE_CUENTA,
      "GET /api/usuarios/me": jsonRoute(200, { ...ADULTO_ME, id: "m-1", tipo: "MENOR" }),
      "GET /api/evaluacion/nps/pendiente": jsonRoute(200, { ofrecer: true }),
    });
    await page.goto("/cuenta/reservas");
    await expect(page.getByRole("heading", { level: 1 }).first()).toBeVisible();
    await expect(page.getByRole("dialog", { name: "¿Recomendarías Tinku?" })).toHaveCount(0);
    expect(pedidos.some((u) => u.includes("/api/evaluacion/"))).toBe(false);
  });

  test("el panel de Admin muestra los indicadores y filtra por período", { tag: ["@evaluacion", "@admin", "@T11B-PANEL-001"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "a-1" });
    const consultas: string[] = [];
    await mockApi(page, {
      "GET /api/admin/yo": jsonRoute(200, { rol: "soporte_financiero" }),
      "GET /api/admin/evaluacion/indicadores": async (route: Route) => {
        consultas.push(new URL(route.request().url()).search);
        await jsonRoute(200, {
          habilitada: true,
          desde: null,
          hasta: null,
          topK: 5,
          busquedas: { total: 12, conResultado: 10, sinResultado: 2 },
          i01: { busquedasConAceptacion: 7, tasa: 0.7, aceptacionesPerfil: 9, aceptacionesReserva: 4, porPosicion: [{ posicion: 1, cantidad: 5 }, { posicion: 3, cantidad: 2 }] },
          nps: { respuestas: 6, promotores: 3, pasivos: 2, detractores: 1, valor: 33.333 },
          i02: { calificaciones: 5, promedio: 4.4 },
        })(route);
      },
    });
    await page.goto("/admin/evaluacion");

    const nav = page.getByRole("navigation", { name: "Secciones" });
    await expect(nav.getByRole("link", { name: "Evaluación" })).toHaveAttribute("aria-current", "page");
    await expect(page.getByRole("heading", { name: "Evaluación", level: 2 })).toBeVisible();
    const i01 = page.getByRole("article").filter({ hasText: "Aceptación de sugerencias (I-01)" });
    await expect(i01).toContainText("70 %");
    await expect(i01).toContainText("7 de 10 búsquedas");
    await expect(page.getByRole("article").filter({ hasText: "Recomendación (NPS)" })).toContainText("33,3");
    await expect(page.getByRole("article").filter({ hasText: "Calidad percibida (I-02)" })).toContainText("4,4 / 5");
    await expect(page.getByRole("article").filter({ hasText: "Búsquedas sin resultado" })).toContainText("2");
    await expect(page.getByRole("row", { name: "3 2" })).toBeVisible();

    await page.getByLabel("Desde").fill("2026-10-20");
    await page.getByLabel("Hasta").fill("2026-10-31");
    await page.getByRole("button", { name: "Ver período" }).click();
    await expect.poll(() => consultas.at(-1)).toBe("?desde=2026-10-20&hasta=2026-10-31");
  });

  test("abrir el perfil de un tutor sugerido registra la aceptación con el id de la búsqueda", { tag: ["@evaluacion", "@busqueda", "@T11B-I01-001"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_est: true });
    let aceptacion: { url: string; cuerpo: unknown } | null = null;
    const tutor = { id: "t-1", nombre: "Martín", apellido: "Gómez", materias: ["Matemática"], calificacionPromedio: 4.8, cantidadCalificaciones: 12, precioHora: 5000 };
    await mockApi(page, {
      "GET /api/catalogos": jsonRoute(200, []),
      "GET /api/busquedas/guardadas": jsonRoute(200, []),
      "POST /api/busquedas": async (route: Route) => {
        await route.fulfill({
          status: 200,
          contentType: "application/json",
          headers: { "X-Busqueda-Id": "b-123", "Access-Control-Expose-Headers": "X-Busqueda-Id" },
          body: JSON.stringify([{ tutorId: "t-1", score: 0.91, noAutorizado: false }]),
        });
      },
      "GET /api/tutores/t-1": jsonRoute(200, tutor),
      "GET /api/tutores/t-1/calificaciones": jsonRoute(200, []),
      "POST /api/busquedas/b-123/aceptaciones": async (route: Route) => {
        aceptacion = { url: route.request().url(), cuerpo: route.request().postDataJSON() };
        await route.fulfill({ status: 204 });
      },
    });

    const buscar = new BuscarPage(page);
    await buscar.goto();
    await buscar.buscar("cómo dividir polinomios");
    await page.getByRole("link", { name: "Martín Gómez" }).click();

    await expect(page).toHaveURL(/\/tutores\/t-1$/);
    await expect.poll(() => aceptacion?.cuerpo).toEqual({ tutorId: "t-1", tipo: "PERFIL" });
  });
});
