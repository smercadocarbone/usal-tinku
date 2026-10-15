import { test, expect } from "@playwright/test";
import { agruparPorDia, unirContiguas, type Franja } from "../../src/lib/agenda";
import { jsonRoute, mockApi, setFakeSession, setFakeSessionConPayload } from "../helpers";

/**
 * Chunk REM-D (docs/superpowers/specs/remediacion-pruebas/R-D-agenda-franjas.md): quitar franjas
 * (T2-H1), franjas contiguas (T1-M2), lista agrupada (T7-3), perfil con disponibilidad real
 * (T2-H10), rótulo de lo que ya pasó (T1-8), pedido en clases terminadas (T1-9) y precio negativo
 * (T1-12).
 */

const TUTOR_ID = "t-1";

function franja(id: string, diaSemana: number | null, desde: string, hasta: string, fecha: string | null = null): Franja {
  return { id, tutorId: TUTOR_ID, diaSemana, fechaEspecifica: fecha, horaInicio: desde, horaFin: hasta, activa: true };
}

const hh = (h: number) => `${String(h).padStart(2, "0")}:00`;

test.describe("unirContiguas — regla", () => {
  test("une contiguas y superpuestas, no las separadas", { tag: ["@agenda", "@AGENDA-001"] }, () => {
    expect(unirContiguas([franja("a", 1, "09:00", "10:00"), franja("b", 1, "08:00", "09:00")])).toEqual([
      { desde: "08:00", hasta: "10:00" },
    ]);
    expect(unirContiguas([franja("a", 1, "08:00", "10:00"), franja("b", 1, "09:00", "11:00")])).toEqual([
      { desde: "08:00", hasta: "11:00" },
    ]);
    expect(unirContiguas([franja("a", 1, "08:00", "09:00"), franja("b", 1, "10:00", "11:00")])).toEqual([
      { desde: "08:00", hasta: "09:00" },
      { desde: "10:00", hasta: "11:00" },
    ]);
    const { semanal } = agruparPorDia([franja("a", 1, "08:00", "09:00"), franja("b", 2, "08:00", "09:00")]);
    expect([...semanal.keys()].sort()).toEqual([1, 2]);
  });
});

const PERFIL_TUTOR = { id: TUTOR_ID, nombre: "Jorge", apellido: "Test", tipo: "TUTOR" };

test.describe("Mi agenda — quitar y lista agrupada", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { sub: TUTOR_ID, tipo: "TUTOR" });
  });

  test("105 franjas de 1 h se ven como 7 filas, una por día", { tag: ["@agenda", "@AGENDA-002"] }, async ({ page }) => {
    const franjas: Franja[] = [];
    for (let d = 0; d <= 6; d++) for (let h = 8; h < 23; h++) franjas.push(franja(`f-${d}-${h}`, d, hh(h), hh(h + 1)));
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, PERFIL_TUTOR),
      [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, franjas),
    });
    await page.goto("/cuenta/horarios");
    const lista = page.getByRole("list").filter({ hasText: "Lunes de 08:00 a 23:00" }).first();
    await expect(lista.locator(":scope > li")).toHaveCount(7);
    await expect(page.getByText("Lunes de 08:00 a 23:00")).toBeVisible();
  });

  test("Quitar pide confirmación y llama a DELETE", { tag: ["@agenda", "@AGENDA-003"] }, async ({ page }) => {
    let borrada: string | null = null;
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, PERFIL_TUTOR),
      [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [franja("f1", 1, "09:00", "11:00")]),
      "DELETE /api/tutores/franjas/:id": async (route) => {
        borrada = new URL(route.request().url()).pathname.split("/").pop() ?? null;
        await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ id: "f1", reservasFuturasEnLaFranja: 2 }) });
      },
    });
    await page.goto("/cuenta/horarios");
    await page.getByText("Lunes de 09:00 a 11:00").click();
    await page.getByRole("button", { name: /^Quitar Lunes/ }).click();
    await expect(page.getByText("Las clases ya reservadas se mantienen.")).toBeVisible();
    await page.getByRole("button", { name: "Quitar horario" }).click();
    await expect(page.getByText(/Tenés 2 clases reservadas/)).toBeVisible();
    expect(borrada).toBe("f1");
  });
});

const TUTOR_PERFIL = {
  id: TUTOR_ID,
  nombre: "Martín",
  apellido: "Gómez",
  tipo: "TUTOR",
  materias: ["Matemática"],
  nivel: "secundario",
  calificacionPromedio: 4.8,
  cantidadCalificaciones: 12,
  precioHora: 5000,
};

test.describe("Perfil público — disponibilidad real", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test("muestra los rangos libres que devuelve /disponibilidad", { tag: ["@agenda", "@AGENDA-004"] }, async ({ page }) => {
    await mockApi(page, {
      [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR_PERFIL),
      [`GET /api/tutores/${TUTOR_ID}/disponibilidad`]: jsonRoute(200, [
        { fecha: "2026-11-11", rangos: [{ desde: "08:00", hasta: "14:00" }, { desde: "15:00", hasta: "23:00" }] },
      ]),
    });
    await page.goto(`/tutores/${TUTOR_ID}`);
    await expect(page.getByText("08:00–14:00 · 15:00–23:00")).toBeVisible();
  });

  test("sin rangos, avisa que no hay horarios libres", { tag: ["@agenda", "@AGENDA-005"] }, async ({ page }) => {
    await mockApi(page, {
      [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR_PERFIL),
      [`GET /api/tutores/${TUTOR_ID}/disponibilidad`]: jsonRoute(200, []),
    });
    await page.goto(`/tutores/${TUTOR_ID}`);
    await expect(page.getByText("Todavía no tiene horarios libres esta semana.")).toBeVisible();
  });
});

test.describe("Reservar — franjas contiguas y lo que ya pasó", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSession(context, baseURL!);
  });

  test("dos franjas de 1 h seguidas ofrecen una clase de 2 h", { tag: ["@agenda", "@AGENDA-006"] }, async ({ page }) => {
    const fecha = new Date(Date.now() + 5 * 86400000).toISOString().slice(0, 10);
    await mockApi(page, {
      [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR_PERFIL),
      [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [
        franja("f-1", null, "10:00", "11:00", fecha),
        franja("f-2", null, "11:00", "12:00", fecha),
      ]),
    });
    await page.goto(`/reservar?tutor=${TUTOR_ID}`);
    await page.getByRole("radio", { name: /^2 h/ }).click();
    await expect(page.getByRole("button", { name: /^10:00 a 12:00/ })).toBeVisible();
  });

  test("un horario dentro de la ventana mínima dice 'Ya no se puede reservar', no 'Ocupado'", { tag: ["@agenda", "@AGENDA-007"] }, async ({ page }) => {
    // 10:15 en Argentina (UTC-3).
    await page.clock.setFixedTime(new Date("2026-11-10T13:15:00Z"));
    await mockApi(page, {
      [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, TUTOR_PERFIL),
      [`GET /api/tutores/${TUTOR_ID}/franjas`]: jsonRoute(200, [franja("f-1", null, "10:00", "13:00", "2026-11-10")]),
      [`GET /api/tutores/${TUTOR_ID}/horarios`]: jsonRoute(200, []),
    });
    await page.goto(`/reservar?tutor=${TUTOR_ID}`);
    await expect(page.getByRole("button", { name: "10:30 a 11:30, ya no se puede reservar" })).toBeVisible();
    await expect(page.getByText("Ocupado")).toHaveCount(0);
    await expect(page.getByRole("button", { name: /^11:00 a 12:00$/ })).toBeEnabled();
  });
});

function reservaDetalle(estado: string, dias: number) {
  return {
    id: "r-1",
    pagadorId: "u-ad",
    beneficiarioId: "u-ad",
    tutorId: TUTOR_ID,
    horario: new Date(Date.now() + dias * 86400000).toISOString(),
    precio: 15000,
    estado,
    motivoCancelacion: null,
    tutorNombre: "Pablo",
    tutorApellido: "Sosa",
    beneficiarioNombre: "Ana",
    beneficiarioApellido: "Prueba",
    beneficiarioMenor: false,
  };
}

test.describe("Detalle de reserva — pedido previo solo en clases por venir", () => {
  for (const [estado, dias, visible] of [
    ["cancelada", 2, false],
    ["finalizada", -2, false],
    ["confirmada", 2, true],
  ] as const) {
    test(`en una clase ${estado} ${visible ? "se" : "no se"} ofrece escribir el pedido`, { tag: ["@agenda", "@AGENDA-008"] }, async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-ad", cap_est: true });
      await mockApi(page, {
        "GET /api/reservas/r-1": jsonRoute(200, reservaDetalle(estado, dias)),
        "GET /api/reservas/r-1/pedido": async (route) => route.fulfill({ status: 204 }),
      });
      await page.goto("/cuenta/reservas/r-1");
      await expect(page.getByRole("heading", { name: /Pablo|clase/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: "Escribir el pedido" })).toHaveCount(visible ? 1 : 0);
      await expect(page.getByRole("heading", { name: "Qué querés ver en la clase" })).toHaveCount(visible ? 1 : 0);
    });
  }
});

test.describe("Buscar — precio máximo negativo", () => {
  test("avisa en vez de buscar con el número sin signo", { tag: ["@agenda", "@AGENDA-009"] }, async ({ page, context, baseURL }) => {
    await setFakeSession(context, baseURL!);
    await mockApi(page, {});
    await page.goto("/buscar");
    const campo = page.getByLabel("Precio máximo por hora").first();
    await campo.fill("-5");
    await expect(page.getByText("Ingresá un precio mayor a cero").first()).toBeVisible();
  });
});
