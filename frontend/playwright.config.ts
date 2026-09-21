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
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: process.env.PLAYWRIGHT_DEV_PORT ? `PORT=${process.env.PLAYWRIGHT_DEV_PORT} npm run dev` : "npm run dev",
    url: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
