import { test, expect } from "@playwright/test";

/**
 * R-C C-4 (H-AULA-4): en producción, /dev/* responde 404 (antes, 200 con el shell de la página).
 * Corre solo contra un build de producción:
 *   PLAYWRIGHT_PROD=1 npx playwright test tests/dev-rutas.spec.ts   (con `next build && next start` en el puerto de baseURL)
 */
test.skip(!process.env.PLAYWRIGHT_PROD, "solo contra next start (build de producción)");

for (const ruta of ["/dev/componentes", "/dev/killswitch", "/dev/aviso"]) {
  test(`${ruta} da 404 en producción`, { tag: ["@seguridad", "@DEV-001"] }, async ({ request }) => {
    const r = await request.get(ruta, { maxRedirects: 0 });
    expect(r.status()).toBe(404);
  });
}
