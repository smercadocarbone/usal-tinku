import { test, expect, type Page } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSession, setFakeSessionConPayload } from "../helpers";

/**
 * T-TA-10 (enmienda v2.5): las pantallas principales en un teléfono chico (360 y 390 px de ancho)
 * no tienen scroll horizontal. Si falla, el mensaje lista los elementos que se salen del ancho.
 */
const ANCHOS = [360, 390];

const EN_5_DIAS = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);
const TUTOR = {
  id: "t-1",
  nombre: "Martín",
  apellido: "Gómez-Echeverría",
  tipo: "TUTOR",
  materias: ["Matemática", "Física", "Química"],
  nivel: "secundario",
  calificacionPromedio: 4.8,
  cantidadCalificaciones: 12,
  precioHora: 12500,
  verificado: true,
  bio: "Profe de secundaria con diez años de experiencia preparando exámenes de ingreso y recuperatorios.",
};
const FRANJA = { id: "f-1", tutorId: "t-1", diaSemana: null, fechaEspecifica: EN_5_DIAS, horaInicio: "09:00", horaFin: "13:00", activa: true };
const CLASE = {
  id: "r-2",
  pagadorId: "u-1",
  beneficiarioId: "u-1",
  tutorId: "t-1",
  horario: new Date(Date.now() + 9 * 86400000).toISOString(),
  duracionMinutos: 90,
  precio: 16875,
  estado: "confirmada",
  motivoCancelacion: null,
  tutorNombre: "Martín",
  tutorApellido: "Gómez-Echeverría",
  beneficiarioNombre: "Lucas",
  beneficiarioApellido: "Díaz",
  puedeCancelar: true,
  cancelarReembolsaTotal: false,
  paqueteId: "paq-1",
  paqueteClase: 2,
  paqueteTotal: 67500,
  paqueteVigenteHasta: new Date(Date.now() + 30 * 86400000).toISOString(),
  puedeCancelarPaquete: true,
};

/** Elementos visibles cuyo borde derecho pasa el ancho de la ventana (lo que genera scroll horizontal). */
async function desbordes(page: Page): Promise<string[]> {
  return page.evaluate(() => {
    const ancho = document.documentElement.clientWidth;
    const fuera: string[] = [];
    for (const el of Array.from(document.body.querySelectorAll<HTMLElement>("*"))) {
      const r = el.getBoundingClientRect();
      if (r.width === 0 || r.right <= ancho + 1) continue;
      // Un carrusel con overflow propio (scroll-x) no desborda la página.
      let dentroDeScroll = false;
      for (let p = el.parentElement; p && p !== document.body; p = p.parentElement) {
        const ox = getComputedStyle(p).overflowX;
        if (ox === "auto" || ox === "scroll" || ox === "hidden" || ox === "clip") {
          dentroDeScroll = true;
          break;
        }
      }
      if (dentroDeScroll) continue;
      const clase = typeof el.className === "string" ? el.className.split(" ").slice(0, 4).join(".") : "";
      fuera.push(`${el.tagName.toLowerCase()}.${clase} → right ${Math.round(r.right)}px: "${(el.textContent ?? "").trim().slice(0, 40)}"`);
    }
    return fuera.slice(0, 10);
  });
}

async function sinScrollHorizontal(page: Page) {
  await page.waitForLoadState("networkidle");
  if (process.env.CAPTURAS) {
    const n = (globalThis as { __n?: number }).__n = ((globalThis as { __n?: number }).__n ?? 0) + 1;
    await page.screenshot({ path: `${process.env.CAPTURAS}/${page.viewportSize()?.width}-${n}.png`, fullPage: true });
  }
  const { scroll, ancho } = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    ancho: document.documentElement.clientWidth,
  }));
  expect(scroll, `scroll horizontal (${scroll} > ${ancho}):\n${(await desbordes(page)).join("\n")}`).toBeLessThanOrEqual(ancho);
}

for (const ancho of ANCHOS) {
  test.describe(`Pantalla chica ${ancho} px`, () => {
    test.use({ viewport: { width: ancho, height: 760 } });

    test(`públicas: inicio, ingreso, registro y perfil del tutor @mobile`, { tag: ["@mobile", "@e2e", `@MOBILE-${ancho}-001`] }, async ({ page }) => {
      await mockApi(page, {
        "GET /api/tutores/t-1": jsonRoute(200, TUTOR),
        "GET /api/tutores/t-1/franjas": jsonRoute(200, [FRANJA]),
        "GET /api/catalogos": jsonRoute(200, []),
      });
      for (const ruta of ["/", "/login", "/registro", "/tutores/t-1"]) {
        await page.goto(ruta);
        await test.step(ruta, () => sinScrollHorizontal(page));
      }
    });

    test(`búsqueda con resultados y filtros @mobile`, { tag: ["@mobile", "@e2e", `@MOBILE-${ancho}-002`] }, async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, []),
        "POST /api/busquedas": jsonRoute(200, [
          { tutorId: "t-1", score: 0.91, noAutorizado: false, precioHora: 12500, proximoHorario: `${EN_5_DIAS}T12:00:00Z` },
        ]),
        "GET /api/tutores/t-1": jsonRoute(200, TUTOR),
      });
      await page.goto("/buscar");
      await page.getByRole("searchbox").first().fill("ecuaciones de segundo grado para el recuperatorio");
      await page.keyboard.press("Enter");
      await expect(page.getByText("Martín", { exact: false }).first()).toBeVisible();
      await sinScrollHorizontal(page);
    });

    test(`reserva: horario, paquete del mes y pago @mobile`, { tag: ["@mobile", "@e2e", `@MOBILE-${ancho}-003`] }, async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/tutores/t-1": jsonRoute(200, TUTOR),
        "GET /api/tutores/t-1/franjas": jsonRoute(200, [FRANJA]),
        "GET /api/reservas/paquete/oferta": jsonRoute(200, { disponible: true, descuentoPorcentaje: 10, clases: 4, semanas: 4 }),
        "GET /api/reservas/r-2": jsonRoute(200, { ...CLASE, estado: "pendiente_pago", pagoVenceAt: new Date(Date.now() + 600000).toISOString() }),
        "POST /api/pagos/preferencia": jsonRoute(200, { preferenciaId: "p", initPoint: "https://mercadopago.example/p", bypass: false }),
      });
      await page.goto("/reservar?tutor=t-1");
      await expect(page.getByRole("button", { name: /^09:00 a 10:00/ })).toBeVisible();
      await sinScrollHorizontal(page);
      await page.getByRole("button", { name: /^09:00 a 10:00/ }).click();
      await page.getByRole("button", { name: "Continuar", exact: true }).click();
      await page.getByRole("radio", { name: /Paquete del mes/ }).click();
      await page.getByText("¿Qué querés ver en la clase?").click();
      await sinScrollHorizontal(page);

      await page.goto("/pagar?reserva=r-2");
      await expect(page.getByText("Paquete del mes", { exact: false }).first()).toBeVisible();
      await sinScrollHorizontal(page);
    });

    test(`mis clases y el detalle de una clase del paquete @mobile`, { tag: ["@mobile", "@e2e", `@MOBILE-${ancho}-004`] }, async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1" });
      await mockApi(page, {
        "GET /api/reservas": jsonRoute(200, [CLASE, { ...CLASE, id: "r-3", paqueteClase: 3, estado: "finalizada" }]),
        "GET /api/solicitudes": jsonRoute(200, []),
        "GET /api/reservas/r-2": jsonRoute(200, CLASE),
        "GET /api/reservas/r-2/pedido-reprogramacion": jsonRoute(200, {
          id: "p-1",
          reservaId: "r-2",
          horarioOriginal: CLASE.horario,
          horarioPropuesto: new Date(Date.now() + 10 * 86400000).toISOString(),
          motivo: "Me surgió un turno médico que no puedo mover, perdón por el cambio",
          estado: "pendiente",
          createdAt: new Date().toISOString(),
          venceAt: new Date(Date.now() + 8 * 86400000).toISOString(),
          puedoResponder: true,
          puedoRetirar: false,
        }),
        "GET /api/reservas/r-2/pedido": jsonRoute(200, {
          reservaId: "r-2",
          texto: "Quiero repasar factorización y el caso de trinomio cuadrado perfecto para el parcial del jueves",
          updatedAt: new Date().toISOString(),
          editable: true,
        }),
        "GET /api/sesiones/por-reserva/r-2": async (route) => route.fulfill({ status: 404 }),
      });
      await page.goto("/cuenta/reservas");
      await expect(page.getByText("Paquete 2/4")).toBeVisible();
      await sinScrollHorizontal(page);
      await page.goto("/cuenta/reservas/r-2");
      await expect(page.getByText("Clase 2 de 4 · paquete del mes")).toBeVisible();
      await sinScrollHorizontal(page);
      await page.getByRole("button", { name: "Cancelar el paquete entero" }).click();
      await sinScrollHorizontal(page);
    });

    test(`tutor: precio con paquete y mis cobros @mobile`, { tag: ["@mobile", "@e2e", `@MOBILE-${ancho}-005`] }, async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
      await mockApi(page, {
        "GET /api/pagos/tarifa": jsonRoute(200, { tutorId: "t-1", precioHora: 12500, pisoHora: 6000, comisionPorcentaje: 27, paqueteHabilitado: true, paqueteDescuentoPorcentaje: 10 }),
        "GET /api/pagos/mp/estado": jsonRoute(200, { requerida: true, estado: "CONECTADA", conectadaAt: new Date().toISOString() }),
        "GET /api/pagos/mis-cobros": jsonRoute(200, {
          retenido: 10950,
          enRevision: 0,
          liberado: 1219000,
          reembolsado: 0,
          cobros: [
            {
              reservaId: "r-1",
              horario: new Date(Date.now() - 3600000).toISOString(),
              alumnoNombre: "Maximiliano",
              alumnoApellido: "Fernández-Castellanos",
              precioSesion: 15000,
              comision: 4050,
              neto: 10950,
              estado: "retenido",
              liberaAt: null,
              simulado: false,
            },
          ],
        }),
      });
      for (const ruta of ["/cuenta/precio", "/cuenta/cobros"]) {
        await page.goto(ruta);
        await test.step(ruta, () => sinScrollHorizontal(page));
      }
    });

    test(`admin: colas y la pizarra entra en la pantalla @mobile`, { tag: ["@mobile", "@e2e", `@MOBILE-${ancho}-006`] }, async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, { "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }) });
      for (const ruta of ["/admin/alertas", "/admin/denuncias"]) {
        await page.goto(ruta);
        await test.step(ruta, () => sinScrollHorizontal(page));
      }
      // La llamada real del aula necesita LiveKit; el tablero se prueba con la demo de componentes.
      await page.goto("/dev/componentes");
      const tablero = page.getByLabel("Tablero de la pizarra");
      await tablero.scrollIntoViewIfNeeded();
      const caja = (await tablero.boundingBox())!;
      expect(caja.x).toBeGreaterThanOrEqual(0);
      expect(caja.x + caja.width).toBeLessThanOrEqual(ancho);
    });
  });
}
