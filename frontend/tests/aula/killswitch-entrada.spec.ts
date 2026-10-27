import { test, expect, type Page } from "@playwright/test";
import { jsonRoute, mockApi, setFakeSession } from "../helpers";

/**
 * M3C-05 (T-M3-06e): entrada protegida al aula (US-6bis, FR-AULA-010). Con un menor no se entra
 * sin clasificador; entre adultos sí, y se avisa al backend. Cámara y micrófono falsos de Chromium.
 */
test.use({
  permissions: ["camera", "microphone"],
  launchOptions: {
    executablePath: process.env.CHROMIUM_PATH || undefined,
    args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"],
  },
});

const SESION = "11111111-1111-4111-8111-111111111111";
const modeloFalso = () =>
  (window as unknown as Record<string, unknown>).__tinkuModeloFalso = {
    backend: "falso",
    liberar() {},
    clasificar: async () => [{ className: "Neutral", probability: 0.99 }],
  };

async function preparar(page: Page, baseURL: string, hayMenor: boolean, modelo: "ok" | "caido") {
  await setFakeSession(page.context(), baseURL);
  if (modelo === "ok") await page.addInitScript(modeloFalso);
  else await page.route("**/modelos/**", (r) => r.abort());
  const pedidos: { metodo: string; url: string; cuerpo: string }[] = [];
  await mockApi(page, {
    [`GET /api/sesiones/${SESION}/killswitch`]: jsonRoute(200, { hayMenor, confirmacionPendiente: false, detectadoId: null, venceAt: null }),
    [`POST /api/sesiones/${SESION}/clasificador`]: async (route) => {
      pedidos.push({ metodo: "POST", url: route.request().url(), cuerpo: route.request().postData() ?? "" });
      await route.fulfill({ status: 204 });
    },
    [`POST /api/sesiones/${SESION}/token`]: async (route) => {
      pedidos.push({ metodo: "POST", url: route.request().url(), cuerpo: "" });
      await route.fulfill({ status: 422, contentType: "application/json", body: JSON.stringify({ error: "La sala aún no fue creada" }) });
    },
  });
  await page.goto(`/aula/${SESION}`);
  return pedidos;
}

test.describe("Kill-switch — entrada al aula", () => {
  test("con un menor y el clasificador caído no se entra ni se pide token", { tag: ["@aula", "@e2e", "@KS-050"] }, async ({ page, baseURL }) => {
    const pedidos = await preparar(page, baseURL!, true, "caido");
    await expect(page.getByText("No pudimos activar la protección del aula en este dispositivo")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("link", { name: "Volver a mis clases" })).toBeVisible();
    expect(pedidos.filter((p) => p.url.includes("/token"))).toHaveLength(0);
  });

  test("con un menor y el clasificador listo se entra y el token declara clasificadorListo=true", { tag: ["@aula", "@e2e", "@KS-051"] }, async ({ page, baseURL }) => {
    const pedidos = await preparar(page, baseURL!, true, "ok");
    const boton = page.getByRole("button", { name: /^Entrar a la (clase|sala de espera)$/ });
    await expect(boton).toBeEnabled({ timeout: 20_000 });
    await boton.click();
    await expect.poll(() => pedidos.filter((p) => p.url.includes("/token")).length).toBe(1);
    expect(pedidos.find((p) => p.url.includes("/token"))!.url).toContain("clasificadorListo=true");
  });

  test("entre adultos con el clasificador caído se puede entrar y se avisa NO_CARGA", { tag: ["@aula", "@e2e", "@KS-052"] }, async ({ page, baseURL }) => {
    const pedidos = await preparar(page, baseURL!, false, "caido");
    const boton = page.getByRole("button", { name: /^Entrar a la (clase|sala de espera)$/ });
    await expect(boton).toBeEnabled({ timeout: 20_000 });
    await expect.poll(() => pedidos.filter((p) => p.url.includes("/clasificador")).length).toBe(1);
    expect(pedidos.find((p) => p.url.includes("/clasificador"))!.cuerpo).toContain("NO_CARGA");
    await boton.click();
    await expect.poll(() => pedidos.filter((p) => p.url.includes("/token")).length).toBe(1);
    expect(pedidos.find((p) => p.url.includes("/token"))!.url).toContain("clasificadorListo=false");
  });
});
