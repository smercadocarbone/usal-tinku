import { test, expect, type Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { jsonRoute, mockApi, setFakeSession, setFakeSessionConPayload } from "../helpers";

/**
 * Chunk REM-G (docs/superpowers/specs/remediacion-pruebas/R-G-frontend-a11y.md): móvil (T7-M1/M2),
 * títulos (T7-5), objetivos táctiles (T7-8), contraste y foco (T7-9/10), un h1 (T7-12), textos de
 * cobros y precio (T2-H5/H6/H7), CSP en Report-Only (T6) y /api/admin/yo (T1-7, T2-H12).
 */
test.use({ reducedMotion: "reduce" });

const TUTOR_ME = { id: "t-1", nombre: "Jorge", apellido: "Test", tipo: "TUTOR", capacidadEstudiante: false, capacidadAdultoResponsable: false, email: "j@test.tinku" };
const ADULTO_ME = { id: "u-1", nombre: "Lucía", apellido: "Díaz", tipo: "ADULTO", capacidadEstudiante: true, capacidadAdultoResponsable: true, email: "l@test.tinku" };

function franjasSeed() {
  const franjas = [];
  for (let d = 0; d <= 6; d++) {
    for (let h = 8; h < 23; h++) {
      franjas.push({ id: `f-${d}-${h}`, tutorId: "t-1", diaSemana: d, fechaEspecifica: null, horaInicio: `${String(h).padStart(2, "0")}:00`, horaFin: `${String(h + 1).padStart(2, "0")}:00`, activa: true });
    }
  }
  return franjas;
}

async function tutorMocks(page: Page, extra: Parameters<typeof mockApi>[1] = {}) {
  await mockApi(page, {
    "GET /api/usuarios/me": jsonRoute(200, TUTOR_ME),
    "GET /api/tutores/me/estado-perfil": jsonRoute(200, { tieneFoto: true, tieneBio: true, tieneMaterias: true, tieneHorarios: true, tieneTarifa: true, tieneCredencial: true, completo: true }),
    "GET /api/tutores/t-1/franjas": jsonRoute(200, franjasSeed()),
    "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
    ...extra,
  });
}

async function sinScrollHorizontal(page: Page) {
  await page.waitForLoadState("networkidle");
  const { scroll, ancho } = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    ancho: window.innerWidth,
  }));
  expect(scroll, `scroll horizontal ${scroll} > ${ancho}`).toBeLessThanOrEqual(ancho);
}

test.describe("Móvil 360×640", () => {
  test.use({ viewport: { width: 360, height: 640 } });

  test("/cuenta/horarios no ensancha la página y llega a los 7 días", { tag: ["@mobile", "@REM-G-001"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
    await tutorMocks(page);
    await page.goto("/cuenta/horarios");
    await expect(page.getByText("Lunes de 08:00 a 23:00")).toBeVisible();
    await sinScrollHorizontal(page);
    for (const dia of ["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"]) {
      await expect(page.getByText(dia, { exact: true }).first()).toBeAttached();
    }
  });

  test("/cuenta/materias: un tema largo no se sale del card", { tag: ["@mobile", "@REM-G-002"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
    await tutorMocks(page, {
      "GET /api/catalogos": jsonRoute(200, [
        {
          nivel: "secundario",
          cursos: [{ anioOCarrera: "4°", materias: [{ nombre: "Matemática", temas: [{ id: "tm-1", nombre: "Números Reales y radicales con racionalización de denominadores", descripcion: "x" }] }] }],
        },
      ]),
      "GET /api/tutores/me/temas": jsonRoute(200, { tema_ids: ["tm-1"] }),
    });
    await page.goto("/cuenta/materias");
    await page.waitForLoadState("networkidle");
    await sinScrollHorizontal(page);
  });
});

test.describe("Títulos de pestaña", () => {
  const TITULOS: [string, RegExp][] = [
    ["/login", /^Ingresar · Tinku$/],
    ["/buscar", /^Buscar tutores · Tinku$/],
    ["/cuenta/reservas", /^Mis clases · Tinku$/],
    ["/cuenta/perfil", /^Mi perfil · Tinku$/],
    ["/cuenta/acceso", /^Seguridad y acceso · Tinku$/],
    ["/cuenta/seguridad", /^Casos y reportes · Tinku$/],
    ["/cuenta/menores", /^Mis chicos · Tinku$/],
    ["/no-existe-esta-pagina", /^Página no encontrada · Tinku$/],
  ];
  for (const [ruta, titulo] of TITULOS) {
    test(`${ruta} tiene su título`, { tag: ["@a11y", "@REM-G-003"] }, async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_ar: true, cap_est: true });
      await mockApi(page, { "GET /api/usuarios/me": jsonRoute(200, ADULTO_ME) });
      await page.goto(ruta);
      await expect(page).toHaveTitle(titulo);
    });
  }
});

test.describe("Accesibilidad", () => {
  test("los enlaces sueltos del login y la 404 miden al menos 24 px", { tag: ["@a11y", "@REM-G-004"] }, async ({ page }) => {
    for (const ruta of ["/login", "/no-existe-esta-pagina"]) {
      await page.goto(ruta);
      await page.waitForLoadState("networkidle");
      const chicos = await page.evaluate(() =>
        Array.from(document.querySelectorAll<HTMLElement>("main a, main button, main [role=switch]"))
          .filter((el) => {
            const r = el.getBoundingClientRect();
            if (r.width === 0 || r.height === 0) return false;
            // Excepción de WCAG 2.5.8: un enlace dentro de una oración.
            const padre = el.parentElement;
            if (el.tagName === "A" && padre?.tagName === "P" && (padre.textContent ?? "").trim() !== (el.textContent ?? "").trim()) return false;
            return r.height < 24 || r.width < 24;
          })
          .map((el) => `${el.tagName} "${(el.textContent ?? "").trim()}" ${Math.round(el.getBoundingClientRect().height)}px`)
      );
      expect(chicos, ruta).toEqual([]);
    }
  });

  test("las horas de la grilla tienen contraste suficiente", { tag: ["@a11y", "@REM-G-005"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
    await tutorMocks(page);
    await page.goto("/cuenta/horarios");
    await expect(page.getByText("Lunes de 08:00 a 23:00")).toBeVisible();
    const r = await new AxeBuilder({ page }).withRules(["color-contrast"]).analyze();
    expect(r.violations.flatMap((v) => v.nodes.map((n) => n.target.join(" ")))).toEqual([]);
  });

  test("el foco se ve en la home y en buscar", { tag: ["@a11y", "@REM-G-006"] }, async ({ page, context, baseURL }) => {
    for (const ruta of ["/", "/buscar"]) {
      if (ruta === "/buscar") {
        await setFakeSession(context, baseURL!);
        await mockApi(page, { "GET /api/catalogos": jsonRoute(200, []), "GET /api/busquedas/guardadas": jsonRoute(200, []) });
      }
      await page.goto(ruta);
      await page.waitForLoadState("networkidle");
      const sinAnillo: string[] = [];
      for (let i = 0; i < 12; i++) {
        await page.keyboard.press("Tab");
        const info = await page.evaluate(() => {
          const el = document.activeElement as HTMLElement | null;
          if (!el || el === document.body) return null;
          const cs = getComputedStyle(el);
          let visible = (cs.outlineStyle !== "none" && parseFloat(cs.outlineWidth) > 0) || cs.boxShadow !== "none";
          // Un campo dentro de una caja que marca el foco (focus-within) también cuenta.
          for (let p = el.parentElement, n = 0; !visible && p && n < 3; p = p.parentElement, n++) {
            visible = getComputedStyle(p).boxShadow !== "none" && p.matches(":focus-within");
          }
          return visible ? null : `${el.tagName}#${el.id} "${(el.textContent ?? "").trim().slice(0, 30)}"`;
        });
        if (info) sinAnillo.push(info);
      }
      expect(sinAnillo, ruta).toEqual([]);
    }
  });

  test("/cuenta/seguridad tiene un solo h1", { tag: ["@a11y", "@REM-G-007"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_est: true });
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, ADULTO_ME),
      "GET /api/denuncias/recibidas": jsonRoute(200, []),
      "GET /api/alertas-seguridad/mias": jsonRoute(200, []),
    });
    await page.goto("/cuenta/seguridad");
    await page.waitForLoadState("networkidle");
    await expect(page.locator("h1")).toHaveCount(1);
  });
});

test.describe("Textos de precio y cobros", () => {
  const TARIFA = { tutorId: "t-1", precioHora: 12500, pisoHora: 6140, comisionPorcentaje: 27, comisionMercadoPagoPorcentaje: 6.04, paqueteHabilitado: false, paqueteDescuentoPorcentaje: 0 };

  test("precio bajo el piso: solo el aviso del piso", { tag: ["@cuenta", "@REM-G-008"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
    await tutorMocks(page, {
      "GET /api/pagos/tarifa": jsonRoute(200, TARIFA),
      "GET /api/pagos/mp/estado": jsonRoute(200, { requerida: false, estado: "NO_REQUERIDA" }),
    });
    await page.goto("/cuenta/precio");
    const campo = page.getByLabel("Precio por hora (ARS)");
    await expect(campo).toHaveValue("12500");
    await campo.fill("6139");
    await expect(page.getByText("Así lo ven las familias")).toHaveCount(0);
    await expect(page.getByText(/Te quedan aprox/)).toHaveCount(0);
    await campo.fill("6140");
    await expect(page.getByText("Así lo ven las familias")).toBeVisible();
  });

  test("cobros aclara lo simulado y la comisión de Mercado Pago", { tag: ["@cuenta", "@REM-G-009"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: "t-1" });
    await tutorMocks(page, {
      "GET /api/pagos/mp/estado": jsonRoute(200, { requerida: false, estado: "NO_REQUERIDA" }),
      "GET /api/pagos/mis-cobros": jsonRoute(200, {
        retenido: 0, enRevision: 0, liberado: 0, reembolsado: 0,
        cobros: [{ reservaId: "r-1", horario: new Date(Date.now() - 3600000).toISOString(), alumnoNombre: "Ana", alumnoApellido: "P", precioSesion: 15000, comision: 4050, neto: 10950, estado: "liberado", liberaAt: null, simulado: true }],
      }),
    });
    await page.goto("/cuenta/cobros");
    await expect(page.getByText("Los pagos simulados (pruebas) no suman a los totales.")).toBeVisible();
    await expect(page.getByText(/Mercado Pago descuenta su comisión aparte/).first()).toBeVisible();
  });
});

test.describe("Cabeceras y llamadas", () => {
  test("la CSP va en Report-Only y la home no la viola", { tag: ["@seguridad", "@REM-G-010"] }, async ({ page }) => {
    await page.addInitScript(() => {
      (window as unknown as { __csp: string[] }).__csp = [];
      document.addEventListener("securitypolicyviolation", (e) =>
        (window as unknown as { __csp: string[] }).__csp.push(`${e.violatedDirective} ${e.blockedURI}`)
      );
    });
    const r = await page.goto("/");
    expect(r?.headers()["content-security-policy-report-only"]).toContain("default-src 'self'");
    for (const ruta of ["/", "/login", "/registro"]) {
      await page.goto(ruta);
      await page.waitForLoadState("networkidle");
      expect(await page.evaluate(() => (window as unknown as { __csp: string[] }).__csp), ruta).toEqual([]);
    }
  });

  test("un Estudiante no pide /api/admin/yo", { tag: ["@cuenta", "@REM-G-011"] }, async ({ page, context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", sub: "u-1", cap_est: true });
    const pedidos: string[] = [];
    page.on("request", (r) => pedidos.push(r.url()));
    await mockApi(page, {
      "GET /api/usuarios/me": jsonRoute(200, ADULTO_ME),
      "GET /api/reservas": jsonRoute(200, []),
      "GET /api/solicitudes": jsonRoute(200, []),
      "GET /api/notificaciones/no-leidas": jsonRoute(200, { cantidad: 0 }),
    });
    await page.goto("/cuenta/reservas");
    await page.waitForLoadState("networkidle");
    await page.goto("/cuenta/perfil");
    await page.waitForLoadState("networkidle");
    expect(pedidos.filter((u) => u.includes("/api/admin/yo"))).toEqual([]);
  });
});
