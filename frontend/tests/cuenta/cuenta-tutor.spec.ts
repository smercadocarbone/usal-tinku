import { test, expect } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSessionConPayload } from "../helpers";

const YO = "t-1";

const TUTOR = {
  id: YO,
  nombre: "Martín",
  apellido: "Gómez",
  materias: ["Matemática"],
  nivel: "secundario",
  calificacionPromedio: null,
  cantidadCalificaciones: 0,
  bio: "Doy clases hace 5 años.",
  tieneFoto: false,
  verificado: false,
  precioHora: 8000,
  habilitadoParaMenores: false,
};

const ESTADO = {
  visibleEnBusquedas: false,
  ultimaCredencial: null,
  tieneCredencialAprobada: false,
  tieneMaterias: true,
  tienePrecio: true,
  tieneBio: true,
  tieneFoto: false,
  cap: null,
  habilitadoParaMenores: false,
  aceptaMenores: true,
};

/** Mi cuenta unificada del Tutor: todo lo de su perfil vive en Mi cuenta, con vista previa. */
test.describe("Mi cuenta del tutor", () => {
  test.beforeEach(async ({ context, baseURL }) => {
    await setFakeSessionConPayload(context, baseURL!, { tipo: "TUTOR", sub: YO });
  });

  test(
    "la presentación muestra una vista previa en vivo, y 'Mi perfil' redirige acá",
    { tag: ["@e2e", "@tutor", "@CUENTA-TUTOR-E2E-001"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, { id: YO, nombre: "Martín", apellido: "Gómez", tipo: "TUTOR", capacidadEstudiante: false, capacidadAdultoResponsable: false }),
        [`GET /api/tutores/${YO}`]: jsonRoute(200, TUTOR),
        "GET /api/tutores/me/estado-perfil": jsonRoute(200, ESTADO),
      });

      await page.goto("/cuenta/perfil-tutor");
      await expect(page).toHaveURL(/\/cuenta\/presentacion$/);
      const secciones = page.getByRole("navigation", { name: "Secciones" });
      for (const s of ["Presentación", "Materias", "Precio", "Credenciales", "Clases con menores"]) {
        await expect(secciones.getByRole("link", { name: s })).toBeVisible();
      }
      await expect(secciones.getByRole("link", { name: "Presentación" })).toHaveAttribute("aria-current", "page");

      await page.getByRole("tab", { name: "En tu perfil" }).click();
      await page.getByLabel("Sobre mí").fill("Enseño álgebra con ejemplos de la vida real.");
      await expect(page.getByText("Enseño álgebra con ejemplos de la vida real.", { exact: true }).last()).toBeVisible();
    }
  );

  test(
    "clases con menores: apagarlo pide confirmación y avisa las clases canceladas",
    { tag: ["@e2e", "@tutor", "@seguridad-menor", "@CUENTA-TUTOR-E2E-002"] },
    async ({ page }) => {
      await mockApi(page, {
        "GET /api/usuarios/me": jsonRoute(200, { id: YO, tipo: "TUTOR" }),
        "GET /api/tutores/me/estado-perfil": jsonRoute(200, ESTADO),
        "PUT /api/tutores/me/menores": jsonRoute(200, { aceptaMenores: false, clasesCanceladas: 2 }),
      });

      await page.goto("/cuenta/clases-con-menores");
      const interruptor = page.getByRole("switch", { name: "Doy clases a menores" });
      await expect(interruptor).toBeChecked();
      await expect(page.getByRole("heading", { name: "Certificado de Antecedentes Penales" })).toBeVisible();

      await interruptor.click();
      await page.getByRole("button", { name: "Sí, dejar de darlas" }).click();
      await expect(page.getByText("Cancelamos 2 clases con menores")).toBeVisible();
      await expect(interruptor).not.toBeChecked();
      await expect(page.getByRole("heading", { name: "Certificado de Antecedentes Penales" })).toHaveCount(0);
    }
  );

  test(
    "materias: el asistente sugiere temas a partir de lo que cuenta y los marca",
    { tag: ["@e2e", "@tutor", "@CUENTA-TUTOR-E2E-003"] },
    async ({ page }) => {
      const guardados: string[][] = [];
      await mockApi(page, {
        "GET /api/catalogos": jsonRoute(200, [
          { nivel: "secundario", cursos: [{ nombre: "3°", materias: [{ nombre: "Matemática", temas: [
            { id: "tema-fun", nombre: "Funciones lineales", descripcion: "Pendiente y ordenada" },
            { id: "tema-ecu", nombre: "Ecuaciones", descripcion: "Primer grado" },
          ] }] }] },
        ]),
        "GET /api/tutores/me/temas": jsonRoute(200, { tema_ids: [] }),
        "PUT /api/tutores/me/temas": async (route) => {
          guardados.push(route.request().postDataJSON().tema_ids);
          await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ tema_ids: [] }) });
        },
        "POST /api/tutores/me/temas/sugerencias": jsonRoute(200, [
          { id: "tema-fun", nombre: "Funciones lineales", materia: "Matemática", curso: "3°", nivel: "secundario", score: 0.8 },
        ]),
      });

      await page.goto("/cuenta/materias");
      await page.getByLabel("Qué enseñás").fill("Doy apoyo de matemática en secundaria, sobre todo funciones");
      await page.getByRole("button", { name: "Sugerirme temas" }).click();
      const sugerencia = page.getByRole("button", { name: /Funciones lineales/ }).first();
      await expect(sugerencia).toHaveAttribute("aria-pressed", "false");
      await sugerencia.click();
      await expect(sugerencia).toHaveAttribute("aria-pressed", "true");
      await expect(page.getByRole("heading", { name: "Tus temas (1)" })).toBeVisible();
      await expect.poll(() => guardados.at(-1)).toEqual(["tema-fun"]);
    }
  );
});
