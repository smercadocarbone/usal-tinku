import type { BrowserContext, Page, Route } from "@playwright/test";

/**
 * El backend Java no corre en este pipeline: cada spec mockea `/api/**` con
 * estas respuestas fijas en vez de un servidor real. Sirve para verificar el
 * contrato y la navegación del frontend, no la lógica de negocio del backend
 * (esa la cubren los tests de integración de `backend/`).
 */
export async function mockApi(
  page: Page,
  handlers: Record<string, (route: Route) => Promise<void> | void>
): Promise<void> {
  await page.route("**/api/**", async (route) => {
    const url = new URL(route.request().url());
    const metodo = route.request().method();
    const entrada = Object.entries(handlers).find(([pattern]) => {
      const [method, path] = pattern.split(" ");
      return method === metodo && matchPath(path, url.pathname);
    });
    if (entrada) {
      await entrada[1](route);
      return;
    }
    await route.fulfill({
      status: 404,
      body: JSON.stringify({ error: `no mockeado: ${metodo} ${url.pathname}` }),
    });
  });
}

function matchPath(pattern: string, pathname: string): boolean {
  const regex = new RegExp(
    "^" + pattern.replace(/:[^/]+/g, "[^/]+").replace(/\//g, "\\/") + "$"
  );
  return regex.test(pathname);
}

export function jsonRoute(status: number, body: unknown) {
  return async (route: Route) => {
    await route.fulfill({
      status,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  };
}

/**
 * El middleware (server) solo exige que exista la cookie de sesión, sin
 * validar firma. `getSession()` (cliente) lee `localStorage`, no la cookie —
 * hace falta cargar las dos para simular un adulto autenticado sin
 * capacidades especiales (payload indecodificable -> `{}`, mismo efecto que
 * un JWT real de un Estudiante adulto sin AR).
 */
export async function setFakeSession(context: BrowserContext, baseURL: string): Promise<void> {
  await context.addCookies([
    { name: "tinku_jwt", value: "fake-session-token", url: baseURL },
  ]);
  await context.addInitScript(() => {
    window.localStorage.setItem("tinku_jwt", "fake-session-token");
  });
}

/**
 * Sesión con un payload de JWT real (decodificable por `getSession()`), para
 * los casos donde el rol importa: Menor, Adulto Responsable, Tutor, etc.
 * `header.payload.signature` con base64url — no valida firma, el middleware
 * y `getSession()` no la chequean del lado del cliente.
 */
export async function setFakeSessionConPayload(
  context: BrowserContext,
  baseURL: string,
  payload: Record<string, unknown>
): Promise<void> {
  const payloadB64 = Buffer.from(JSON.stringify(payload)).toString("base64url");
  const token = `header.${payloadB64}.signature`;
  await context.addCookies([{ name: "tinku_jwt", value: token, url: baseURL }]);
  await context.addInitScript((t) => window.localStorage.setItem("tinku_jwt", t), token);
}
