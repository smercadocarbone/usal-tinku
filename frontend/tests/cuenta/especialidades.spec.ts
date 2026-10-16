import { test, expect, type Route } from "@playwright/test";
import { mockApi, jsonRoute, setFakeSession, setFakeSessionConPayload } from "../helpers";

/**
 * ADR-M1-06 (T-ESP-03, chunk E3): especialidades del Tutor, credencial guiada por tipo, tarjeta del
 * Admin e insignias por especialidad. La UI nueva aparece SOLO si el backend informa el flag
 * encendido (`GET /api/especialidades/config`); si no, todo queda como antes.
 */

const CONFIG_ON = { "GET /api/especialidades/config": jsonRoute(200, { habilitado: true }) };
const MATERIAS = ["Biología", "Física", "Lengua", "Matemática"];

test.describe("Especialidades — Mi cuenta → Credenciales (Tutor)", () => {
  test(
    "con el flag apagado, la pantalla es la de siempre",
    { tag: ["@e2e", "@ESPECIALIDADES-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        "GET /api/especialidades/config": jsonRoute(200, { habilitado: false }),
        "GET /api/tutores/me/credencial": (route) => route.fulfill({ status: 204, body: "" }),
      });
      await page.goto("/cuenta/credenciales");

      await expect(page.getByText("Todavía no cargaste tu credencial académica")).toBeVisible();
      await expect(page.getByLabel("Tipo de documento", { exact: true }).locator("option")).toHaveText([
        "Título",
        "Certificado analítico",
        "Matrícula",
      ]);
      await expect(page.getByRole("heading", { name: "Tus especialidades" })).toHaveCount(0);
    }
  );

  test(
    "con el flag encendido: elige especialidades y carga la credencial guiada con la cláusula pendiente",
    { tag: ["@critical", "@e2e", "@ESPECIALIDADES-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      let especialidadesGuardadas: unknown = null;
      let datosCredencial: Record<string, unknown> | null = null;
      await mockApi(page, {
        ...CONFIG_ON,
        "GET /api/especialidades/materias": jsonRoute(200, MATERIAS),
        "GET /api/tutores/me/especialidades": jsonRoute(200, []),
        "PUT /api/tutores/me/especialidades": async (route: Route) => {
          especialidadesGuardadas = route.request().postDataJSON();
          await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
              { id: "e-1", materia: "Matemática", nivelMaximo: "SECUNDARIO", estado: "DECLARADA" },
            ]),
          });
        },
        "GET /api/tutores/me/credencial": (route) => route.fulfill({ status: 204, body: "" }),
        "POST /api/tutores/credenciales/prellenar": jsonRoute(200, {
          institucion: "ISFD N° 41",
          denominacion: "Profesora de Matemática",
          anioEgreso: 2015,
        }),
        "POST /api/tutores/credenciales": async (route: Route) => {
          const cuerpo = route.request().postDataBuffer()?.toString("utf8") ?? "";
          const json = cuerpo.match(/\{"tipoDocumento"[^\n]*\}/);
          datosCredencial = json ? JSON.parse(json[0]) : null;
          await route.fulfill({
            status: 201,
            contentType: "application/json",
            body: JSON.stringify({
              id: "c-1",
              tipoDocumento: "PROFESORADO",
              estado: "PENDIENTE",
              numeroIntento: 1,
              createdAt: "2026-10-01T00:00:00Z",
              tieneAprobada: false,
            }),
          });
        },
      });
      await page.goto("/cuenta/credenciales");

      // Especialidades.
      await expect(page.getByRole("heading", { name: "Tus especialidades" })).toBeVisible();
      await page.getByLabel("Materia 1").selectOption("Matemática");
      await page.getByLabel("Nivel máximo 1").selectOption("SECUNDARIO");
      await page.getByRole("button", { name: "Guardar especialidades" }).click();
      await expect(page.getByText("Declarada", { exact: true })).toBeVisible();
      expect(especialidadesGuardadas).toEqual({
        especialidades: [{ materia: "Matemática", nivelMaximo: "SECUNDARIO" }],
      });

      // Credencial guiada: tipos nuevos, prellenado, cláusula con el marcador del dueño.
      const tipo = page.getByLabel("Tipo de documento", { exact: true });
      await expect(tipo.locator("option")).toHaveText([
        "Título secundario",
        "Terciario (no docente)",
        "Profesorado",
        "Título universitario",
        "Alumno universitario (certificado de alumno regular)",
        "Matrícula profesional",
        "Otro",
      ]);
      await tipo.selectOption("PROFESORADO");
      await expect(page.getByText("Texto legal pendiente del dueño")).toBeVisible();

      await page.getByLabel("Archivo", { exact: true }).setInputFiles({
        name: "titulo.png",
        mimeType: "image/png",
        buffer: Buffer.from([0x89, 0x50, 0x4e, 0x47]),
      });
      await page.getByRole("button", { name: "Leer datos del archivo" }).click();
      await expect(page.getByLabel("Institución")).toHaveValue("ISFD N° 41");
      await expect(page.getByLabel("Título o certificado")).toHaveValue("Profesora de Matemática");
      await expect(page.getByLabel("Año de egreso")).toHaveValue("2015");

      // Digital sin enlace: el formulario lo pide antes de enviar.
      await page.getByLabel("Es un título digital (con QR o enlace)").check();
      await page.getByRole("button", { name: "Cargar credencial" }).click();
      await expect(page.getByText("Indicá el enlace o el código de verificación.")).toBeVisible();
      await page.getByLabel("Enlace o código de verificación").fill("https://mi.argentina.gob.ar/titulos/abc");

      // Sin aceptar la cláusula no se envía.
      await page.getByRole("button", { name: "Cargar credencial" }).click();
      await expect(page.getByText("Tenés que aceptar la consulta a los registros oficiales.")).toBeVisible();
      await page.getByLabel(/Acepto que el equipo de Tinku consulte/).check();
      await page.getByRole("button", { name: "Cargar credencial" }).click();

      await expect(page.getByText("Tu credencial está en revisión por el equipo de Tinku.")).toBeVisible();
      expect(datosCredencial).toMatchObject({
        tipoDocumento: "PROFESORADO",
        institucion: "ISFD N° 41",
        denominacion: "Profesora de Matemática",
        anioEgreso: 2015,
        esDigital: true,
        enlaceVerificacion: "https://mi.argentina.gob.ar/titulos/abc",
        aceptaConsultaRegistros: true,
      });
    }
  );

  test(
    "una credencial rechazada muestra el motivo que eligió el Admin",
    { tag: ["@e2e", "@ESPECIALIDADES-E2E-003"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR" });
      await mockApi(page, {
        ...CONFIG_ON,
        "GET /api/especialidades/materias": jsonRoute(200, MATERIAS),
        "GET /api/tutores/me/especialidades": jsonRoute(200, [
          { id: "e-1", materia: "Matemática", nivelMaximo: "SECUNDARIO", estado: "DECLARADA" },
        ]),
        "GET /api/tutores/me/credencial": jsonRoute(200, {
          id: "c-1",
          tipoDocumento: "UNIVERSITARIO",
          estado: "RECHAZADO",
          numeroIntento: 1,
          createdAt: "2026-10-01T00:00:00Z",
          tieneAprobada: false,
          motivoRechazo: "NO_FIGURA_EN_REGISTRO",
          motivoRechazoTexto: "No encontramos el título en el registro oficial.",
          detalleRechazo: "Con tu DNI no figura ningún título de la UNC.",
        }),
      });
      await page.goto("/cuenta/credenciales");

      await expect(page.getByText("No encontramos el título en el registro oficial.")).toBeVisible();
      await expect(page.getByText("Con tu DNI no figura ningún título de la UNC.")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cargar credencial" })).toBeVisible();
    }
  );
});

test.describe("Especialidades — insignias en el perfil público", () => {
  const PERFIL = {
    id: "t-1",
    nombre: "Valeria",
    apellido: "Gómez",
    tipo: "TUTOR",
    materias: ["Matemática"],
    nivel: "secundario",
    calificacionPromedio: null,
    cantidadCalificaciones: 0,
    bio: null,
    tieneFoto: false,
    verificado: true,
    precioHora: 5000,
    habilitadoParaMenores: false,
  };

  test(
    "con especialidades: una insignia por especialidad en vez del 'Título verificado' genérico",
    { tag: ["@e2e", "@ESPECIALIDADES-E2E-004"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        "GET /api/tutores/t-1": jsonRoute(200, {
          ...PERFIL,
          especialidades: [
            { id: "e-1", materia: "Matemática", nivelMaximo: "SECUNDARIO", estado: "VERIFICADA" },
            { id: "e-2", materia: "Física", nivelMaximo: "PRIMARIO", estado: "DECLARADA" },
          ],
        }),
      });
      await page.goto("/tutores/t-1");

      const insignias = page.getByRole("list", { name: "Especialidades" });
      await expect(insignias.getByText("Matemática · secundario")).toBeVisible();
      await expect(insignias.getByText("Verificada")).toBeVisible();
      await expect(insignias.getByText("Física · primario")).toBeVisible();
      await expect(insignias.getByText("Declarada")).toBeVisible();
      await expect(page.getByText("Título verificado")).toHaveCount(0);
    }
  );

  test(
    "sin el campo especialidades (flag apagado), sigue el 'Título verificado' de siempre",
    { tag: ["@e2e", "@ESPECIALIDADES-E2E-005"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, { "GET /api/tutores/t-1": jsonRoute(200, PERFIL) });
      await page.goto("/tutores/t-1");

      await expect(page.getByText("Título verificado")).toBeVisible();
      await expect(page.getByRole("list", { name: "Especialidades" })).toHaveCount(0);
    }
  );
});

test.describe("Especialidades — tarjeta del Admin", () => {
  const FILA = {
    id: "c-1",
    tutorId: "t-1",
    tutorNombre: "Valeria",
    tutorApellido: "Gómez",
    tipoDocumento: "PROFESORADO",
    estado: "PENDIENTE",
    numeroIntento: 1,
    cicloEsperaHasta: null,
    createdAt: "2026-10-01T00:00:00Z",
    tutorDni: "37000001",
    institucion: "ISFD N° 41",
    denominacion: "Profesora de Matemática",
    anioEgreso: 2015,
    esDigital: true,
    enlaceVerificacion: "https://mi.argentina.gob.ar/titulos/abc",
    nombreCoincide: false,
    pasoASeguir: "Abrí el QR o el enlace (Mi Argentina / ReFE) y compará nombre y DNI.",
    nivelMaximoQueRespalda: "SECUNDARIO",
    especialidades: [
      { id: "e-1", materia: "Matemática", nivelMaximo: "SECUNDARIO", estado: "DECLARADA" },
      { id: "e-2", materia: "Física", nivelMaximo: "UNIVERSITARIO", estado: "DECLARADA" },
    ],
  };

  test(
    "muestra DNI, datos, la marca de nombre y el paso; aprueba con método y especialidades",
    { tag: ["@critical", "@e2e", "@ESPECIALIDADES-E2E-006"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      let decision: unknown = null;
      await mockApi(page, {
        "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
        "GET /api/admin/moderacion/credenciales": jsonRoute(200, [FILA]),
        "POST /api/admin/moderacion/credenciales/c-1/resolver": async (route: Route) => {
          decision = route.request().postDataJSON();
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(FILA) });
        },
      });
      await page.goto("/admin/credenciales");

      await expect(page.getByText("DNI 37000001")).toBeVisible();
      await expect(page.getByText("ISFD N° 41 · Profesora de Matemática · 2015")).toBeVisible();
      await expect(page.getByText("El nombre del documento no coincide con el del DNI.")).toBeVisible();
      await expect(page.getByText(FILA.pasoASeguir)).toBeVisible();
      await expect(page.getByRole("link", { name: FILA.enlaceVerificacion })).toHaveAttribute(
        "href",
        FILA.enlaceVerificacion
      );

      await page.getByRole("button", { name: "Aprobar" }).click();
      const modal = page.getByRole("dialog");
      await modal.getByLabel("Registro oficial").check();
      await modal.getByLabel("Matemática · secundario").check();
      // Física universitario está por encima de lo que respalda un profesorado.
      await expect(modal.getByLabel("Física · universitario")).toBeDisabled();
      await modal.getByRole("button", { name: "Aprobar credencial" }).click();

      await expect(page.getByText("No hay credenciales pendientes de revisión.")).toBeVisible();
      expect(decision).toEqual({
        decision: "APROBAR",
        metodoVerificacion: "REGISTRO_OFICIAL",
        especialidadesCubiertas: ["e-1"],
      });
    }
  );

  test(
    "rechaza con motivo de la lista y detalle",
    { tag: ["@e2e", "@ESPECIALIDADES-E2E-007"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      let decision: unknown = null;
      await mockApi(page, {
        "GET /api/admin/yo": jsonRoute(200, { rol: "moderacion_seguridad" }),
        "GET /api/admin/moderacion/credenciales": jsonRoute(200, [FILA]),
        "POST /api/admin/moderacion/credenciales/c-1/resolver": async (route: Route) => {
          decision = route.request().postDataJSON();
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(FILA) });
        },
      });
      await page.goto("/admin/credenciales");

      await page.getByRole("button", { name: "Rechazar" }).click();
      const modal = page.getByRole("dialog");
      await modal.getByLabel("Motivo").selectOption("NOMBRE_NO_COINCIDE");
      await modal.getByLabel("Detalle para el Tutor").fill("El título es de otra persona.");
      await modal.getByRole("button", { name: "Rechazar credencial" }).click();

      await expect(page.getByText("No hay credenciales pendientes de revisión.")).toBeVisible();
      expect(decision).toEqual({
        decision: "RECHAZAR",
        motivoRechazo: "NOMBRE_NO_COINCIDE",
        detalleRechazo: "El título es de otra persona.",
      });
    }
  );
});
