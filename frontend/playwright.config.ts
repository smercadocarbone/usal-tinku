import { defineConfig, devices } from "@playwright/test";

/**
 * Tests E2E de los flujos críticos del frontend, aislados del backend Java
 * (que no corre en este pipeline): cada spec mockea `/api/**` con
 * `page.route()`. Esto verifica el FRONTEND — contrato de request/response,
 * navegación, estados de carga/error — no reemplaza los tests de integración
 * del backend (esos ya viven en `backend/`, contra la base real).
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: "html",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      // Los `.e2e.spec.ts` van contra un backend y un LiveKit reales: solo en `e2e-real`.
      testIgnore: /.*\.e2e\.spec\.ts/,
      use: {
        ...devices["Desktop Chrome"],
        // Entornos con un Chromium preinstalado de otra versión (p. ej. CI efímero o contenedores de agentes).
        ...(process.env.CHROMIUM_PATH ? { launchOptions: { executablePath: process.env.CHROMIUM_PATH } } : {}),
      },
    },
    {
      // M3C-06 (T-M3-06f): E2E del kill-switch contra backend y LiveKit reales
      // (docker-compose.e2e.yml). Lee E2E_API_URL; el frontend corre con `npm run dev`
      // para poder inyectar el modelo falso. `npm run test:e2e-real`.
      name: "e2e-real",
      testMatch: /.*\.e2e\.spec\.ts/,
      fullyParallel: false,
      timeout: 180_000,
      use: {
        ...devices["Desktop Chrome"],
        permissions: ["camera", "microphone"],
        launchOptions: {
          ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
          args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"],
        },
      },
    },
  ],
  webServer: {
    command: process.env.PLAYWRIGHT_DEV_PORT ? `PORT=${process.env.PLAYWRIGHT_DEV_PORT} npm run dev` : "npm run dev",
    url: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    // e2e-real: el navegador llama al backend real.
    ...(process.env.E2E_API_URL ? { env: { NEXT_PUBLIC_API_URL: process.env.E2E_API_URL } } : {}),
    timeout: 30_000,
  },
});
