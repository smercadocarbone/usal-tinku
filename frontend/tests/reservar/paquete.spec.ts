import { test, expect } from "@playwright/test";
import { ReservarPage } from "./reservar-page";
import { jsonRoute, mockApi, setFakeSession, setFakeSessionConPayload } from "../helpers";

/** Enmienda v2.5 — paquete del mes (FR-RES-032..037, ADR-M5-03). */
const TUTOR_ID = "t-1";
const EN_5_DIAS = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);

const TUTOR = {
  id: TUTOR_ID,
  nombre: "Martín",
  apellido: "Gómez",
  tipo: "TUTOR",
  capacidadEstudiante: false,
  capacidadAdultoResponsable: false,
  materias: ["Matemática"],
  nivel: "secundario",
  calificacionPromedio: 4.8,
  cantidadCalificaciones: 12,
  precioHora: 5000,
};

const FRANJA = { id: "f-1", tutorId: TUTOR_ID, diaSemana: null, fechaEspecifica: EN_5_DIAS, horaInicio: "10:00", horaFin: "12:00", activa: true };

const OFERTA = { disponible: true, descuentoPorcentaje: 10, clases: 4, semanas: 4 };

test.describe("Paquete del mes — reserva", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test(
    "si el tutor lo ofrece, se elige el paquete, se ven las 4 fechas y se paga en la reserva ancla",
    { tag: ["@e2e", "@reserva", "@PAQUETE-E2E-001"] },
    async ({ page }) => {
      let cuerpo: Record<string, unknown> | null = null;
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [FRANJA]),
        "GET /api/reservas/paquete/oferta": jsonRoute(200, OFERTA),
        "POST /api/reservas/paquete": async (route) => {
          cuerpo = route.request().postDataJSON() as Record<string, unknown>;
          await route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify({ id: "paq-1", reservaAnclaId: "r-ancla", cantidadClases: 4, precioTotal: 18000, fechas: [] }),
          });
        },
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirHorario("10:00 a 11:00");

      await expect(page.getByRole("radio", { name: /Solo esta clase/ })).toHaveAttribute("aria-checked", "true");
      // 5000/h × 1 h × 90 % × 4 = 18.000
      await page.getByRole("radio", { name: /Paquete del mes · 4 clases/ }).click();
      await expect(page.getByRole("radio", { name: /Paquete del mes/ })).toContainText("10 % off");
      await expect(page.getByText("Una clase por semana, mismo día y horario:")).toBeVisible();
      await expect(page.getByRole("listitem").filter({ hasText: /^[1-4]\. / })).toHaveCount(4);
      await expect(page.getByRole("main").getByText(/18\.000/).first()).toBeVisible();

      await page.getByRole("button", { name: "Confirmar y pagar el paquete" }).click();
      await expect(page).toHaveURL(/\/pagar\?reserva=r-ancla/);
      expect(cuerpo).toMatchObject({ tutorId: TUTOR_ID, duracionMinutos: 60 });
    }
  );

  test(
    "si alguna semana choca, se avisa qué fechas y no se sale de la reserva",
    { tag: ["@e2e", "@reserva", "@PAQUETE-E2E-002"] },
    async ({ page }) => {
      const tercera = new Date(Date.now() + 19 * 86400000).toISOString();
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [FRANJA]),
        "GET /api/reservas/paquete/oferta": jsonRoute(200, OFERTA),
        "POST /api/reservas/paquete": jsonRoute(422, { error: "Hay fechas ocupadas", fechas: [tercera] }),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirHorario("10:00 a 11:00");
      await page.getByRole("radio", { name: /Paquete del mes/ }).click();
      await page.getByRole("button", { name: "Confirmar y pagar el paquete" }).click();

      await expect(page.getByText(/El tutor no tiene libre esta fecha/)).toBeVisible();
      await expect(page).toHaveURL(/\/reservar\?tutor=t-1/);
    }
  );

  test(
    "si el tutor no lo ofrece, no aparece la opción",
    { tag: ["@e2e", "@reserva", "@PAQUETE-E2E-003"] },
    async ({ page }) => {
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [FRANJA]),
        "GET /api/reservas/paquete/oferta": jsonRoute(200, { ...OFERTA, disponible: false }),
      });

      const reservar = new ReservarPage(page);
      await reservar.goto(TUTOR_ID);
      await reservar.elegirHorario("10:00 a 11:00");
      await expect(page.getByRole("button", { name: "Confirmar y pagar" })).toBeVisible();
      await expect(page.getByRole("radio", { name: /Paquete del mes/ })).toHaveCount(0);
    }
  );

  test(
    "el pago del paquete muestra el total de las 4 clases",
    { tag: ["@e2e", "@pago", "@PAQUETE-E2E-004"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/reservas/r-ancla": jsonRoute(200, {
          id: "r-ancla",
          precio: 4500,
          estado: "pendiente_pago",
          horario: new Date(Date.now() + 5 * 86400000).toISOString(),
          tutorNombre: "Martín",
          paqueteId: "paq-1",
          paqueteClase: 1,
          paqueteTotal: 18000,
        }),
        "POST /api/pagos/preferencia": jsonRoute(200, { preferenciaId: "pref-1", initPoint: "https://mercadopago.example/p", bypass: false }),
      });

      await page.goto("/pagar?reserva=r-ancla");
      await expect(page.getByText("Paquete del mes con Martín")).toBeVisible();
      await expect(page.getByText(/18\.000/).first()).toBeVisible();
    }
  );
});

test.describe("Paquete del mes — detalle de una clase", () => {
  function clase(extra: Record<string, unknown> = {}) {
    return {
      id: "r-2",
      pagadorId: "u-1",
      beneficiarioId: "u-1",
      tutorId: TUTOR_ID,
      horario: new Date(Date.now() + 9 * 86400000).toISOString(),
      duracionMinutos: 60,
      precio: 4500,
      estado: "confirmada",
      motivoCancelacion: null,
      tutorNombre: "Martín",
      tutorApellido: "Gómez",
      puedeCancelar: true,
      cancelarReembolsaTotal: false,
      paqueteId: "paq-1",
      paqueteClase: 2,
      paqueteTotal: 18000,
      paqueteVigenteHasta: new Date(Date.now() + 30 * 86400000).toISOString(),
      puedeCancelarPaquete: true,
      ...extra,
    };
  }

  function rutasDetalle(reserva: Record<string, unknown>) {
    return {
      "GET /api/reservas/r-2": jsonRoute(200, reserva),
      "GET /api/reservas/r-2/pedido-reprogramacion": async (route: import("@playwright/test").Route) => route.fulfill({ status: 204 }),
      "GET /api/reservas/r-2/pedido": async (route: import("@playwright/test").Route) => route.fulfill({ status: 204 }),
      "GET /api/sesiones/por-reserva/r-2": async (route: import("@playwright/test").Route) => route.fulfill({ status: 404 }),
    };
  }

  test(
    "quien pagó ve la clase n de 4, que cancelarla no devuelve y que el paquete entero sí",
    { tag: ["@e2e", "@PAQUETE-E2E-005"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1" });
      let cancelado = false;
      await mockApi(page, {
        ...rutasDetalle(clase()),
        "POST /api/reservas/paquete/paq-1/cancelar": async (route) => {
          cancelado = true;
          await route.fulfill({ status: 204 });
        },
      });

      await page.goto("/cuenta/reservas/r-2");
      await expect(page.getByText("Clase 2 de 4 · paquete del mes")).toBeVisible();

      await page.getByRole("button", { name: "Cancelar clase" }).click();
      await expect(page.getByRole("dialog")).toContainText("no hay devolución y el tutor la cobra igual");
      await page.getByRole("dialog").getByRole("button", { name: "Volver" }).click();

      await page.getByRole("button", { name: "Cancelar el paquete entero" }).click();
      await expect(page.getByRole("dialog")).toContainText("te devolvemos el total");
      await page.getByRole("dialog").getByRole("button", { name: "Cancelar el paquete" }).click();
      await expect.poll(() => cancelado).toBe(true);
    }
  );

  test(
    "el tutor no ve 'cancelar el paquete' y su cancelación devuelve esa clase",
    { tag: ["@e2e", "@PAQUETE-E2E-006"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: TUTOR_ID });
      await mockApi(page, rutasDetalle(clase({ beneficiarioNombre: "Lucas" })));

      await page.goto("/cuenta/reservas/r-2");
      await expect(page.getByText("Clase 2 de 4 · paquete del mes")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar el paquete entero" })).toHaveCount(0);
      await page.getByRole("button", { name: "Cancelar clase" }).click();
      await expect(page.getByRole("dialog")).toContainText("le devolvemos a quien pagó el valor de esta clase");
    }
  );

  test(
    "con menos de 24 hs, la clase del paquete ya no se mueve",
    { tag: ["@e2e", "@PAQUETE-E2E-007"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1" });
      await mockApi(page, {
        ...rutasDetalle(clase({ horario: new Date(Date.now() + 10 * 3600000).toISOString(), puedeCancelarPaquete: false })),
        [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [FRANJA]),
      });

      await page.goto("/cuenta/reservas/r-2");
      await expect(page.getByRole("button", { name: "Cancelar el paquete entero" })).toHaveCount(0);
      await page.getByRole("button", { name: "Cambiar horario" }).click();
      await expect(page.getByRole("dialog")).toContainText("ya no se puede mover");
      await expect(page.getByRole("button", { name: "Confirmar nuevo horario" })).toBeDisabled();
    }
  );
});

test.describe("Paquete del mes — configuración del tutor", () => {
  test(
    "el tutor lo habilita y guarda un descuento dentro del tope",
    { tag: ["@e2e", "@PAQUETE-E2E-008"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      const puts: unknown[] = [];
      await mockApi(page, {
        "GET /api/pagos/tarifa": jsonRoute(200, { tutorId: "t-1", precioHora: 10000, comisionPorcentaje: 27, paqueteHabilitado: false, paqueteDescuentoPorcentaje: 0 }),
        "PUT /api/pagos/tarifa/paquete": async (route) => {
          puts.push(route.request().postDataJSON());
          await route.fulfill({ status: 200, contentType: "application/json", body: "{}" });
        },
      });

      await page.goto("/cuenta/precio");
      // Se tilda recién cuando el backend confirma el guardado.
      await page.getByRole("checkbox", { name: "Ofrecer el paquete del mes" }).click();
      await expect.poll(() => puts.length).toBe(1);
      await expect(page.getByRole("checkbox", { name: "Ofrecer el paquete del mes" })).toBeChecked();
      expect(puts[0]).toEqual({ habilitado: true, descuentoPorcentaje: 0 });

      await page.getByLabel("Descuento (%)").fill("35");
      await expect(page.getByText("El descuento va de 0 a 30 %")).toBeVisible();
      await expect(page.getByRole("button", { name: "Guardar descuento" })).toBeDisabled();

      await page.getByLabel("Descuento (%)").fill("10");
      // 10000 × 90 % × 4 = 36.000
      await expect(page.getByText(/sale \$\s?36\.000/)).toBeVisible();
      await page.getByRole("button", { name: "Guardar descuento" }).click();
      await expect.poll(() => puts.length).toBe(2);
      expect(puts[1]).toEqual({ habilitado: true, descuentoPorcentaje: 10 });
    }
  );
});
