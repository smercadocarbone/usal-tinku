import { test, expect } from "@playwright/test";
import { TutorPerfilPage } from "./tutores-page";
import { mockApi, jsonRoute, setFakeSession, setFakeSessionConPayload } from "../helpers";

const TUTOR_ID = "t-1";

const PERFIL_MOCK = {
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

test.describe("Perfil de Tutor — denuncia y autorización", () => {
  test(
    "un adulto puede denunciar el perfil de un tutor",
    { tag: ["@critical", "@e2e", "@seguridad-menor", "@TUTORES-E2E-001"] },
    async ({ page, context, baseURL }) => {
      await setFakeSession(context, baseURL!);
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, PERFIL_MOCK),
        "POST /api/denuncias": jsonRoute(201, {
          id: "d-1",
          denunciadoId: TUTOR_ID,
          estado: "registrada",
          motivo: "comportamiento_inapropiado",
          sesionId: null,
          createdAt: "2026-01-01T00:00:00Z",
        }),
      });

      const tutor = new TutorPerfilPage(page);
      await tutor.goto(TUTOR_ID);
      await tutor.enviarDenuncia("comportamiento_inapropiado");

      await expect(page.getByText("Denuncia registrada.")).toBeVisible();
    }
  );

  test(
    "un Estudiante Menor no ve el botón de denunciar (Artículo II)",
    { tag: ["@critical", "@e2e", "@seguridad-menor", "@TUTORES-E2E-002"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "MENOR" });
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, PERFIL_MOCK),
      });

      const tutor = new TutorPerfilPage(page);
      await tutor.goto(TUTOR_ID);

      await expect(page.getByText("Pedile a tu adulto responsable")).toBeVisible();
      await expect(tutor.botonDenunciar).toHaveCount(0);
    }
  );

  test(
    "un Adulto Responsable autoriza al tutor para uno de sus menores",
    { tag: ["@critical", "@e2e", "@TUTORES-E2E-003"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", cap_ar: true });
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, PERFIL_MOCK),
        "GET /api/usuarios/menores": jsonRoute(200, [
          { id: "m-1", nombre: "Sofía", apellido: "Pérez" },
        ]),
        "POST /api/autorizaciones": jsonRoute(201, { id: "auth-1" }),
      });

      const tutor = new TutorPerfilPage(page);
      await tutor.goto(TUTOR_ID);

      await expect(tutor.selectMenorAutorizar).toBeVisible();
      await expect(
        tutor.selectMenorAutorizar.locator("option", { hasText: "Sofía Pérez" })
      ).toHaveCount(1);

      await tutor.botonAutorizar.click();

      await expect(page.getByText("Autorizaste a este tutor para Sofía.")).toBeVisible();
    }
  );

  test(
    "sin menores a cargo, se avisa en vez de ofrecer autorizar",
    { tag: ["@e2e", "@TUTORES-E2E-004"] },
    async ({ page, context, baseURL }) => {
      await setFakeSessionConPayload(context, baseURL!, { tipo: "ADULTO", cap_ar: true });
      await mockApi(page, {
        [`GET /api/tutores/${TUTOR_ID}`]: jsonRoute(200, PERFIL_MOCK),
        "GET /api/usuarios/menores": jsonRoute(200, []),
      });

      const tutor = new TutorPerfilPage(page);
      await tutor.goto(TUTOR_ID);

      await expect(page.getByText("Todavía no diste de alta a ningún menor")).toBeVisible();
      await expect(tutor.selectMenorAutorizar).toHaveCount(0);
    }
  );
});
