#!/usr/bin/env node
/**
 * M3C-07 (T-M3-06g): mide la latencia del clasificador del kill-switch con el código real del aula,
 * frenando la CPU de Chromium por CDP (`Emulation.setCPUThrottlingRate`, el método de Lighthouse).
 *
 * Uso, desde frontend/:
 *   CHROMIUM_PATH=/ruta/a/chromium node scripts/medir-clasificador.mjs
 * Variables opcionales:
 *   PLAYWRIGHT_BASE_URL (default http://localhost:3000) y PLAYWRIGHT_DEV_PORT, como playwright.config.ts;
 *   MEDIR_BACKENDS (default "webgl,wasm"), MEDIR_CPU (default "1,4,6"), MEDIR_SEGUNDOS (default 60).
 *
 * Levanta `npm run dev` si no hay un servidor respondiendo en la URL, o reutiliza el que haya.
 * Imprime una tabla en markdown en stdout y el JSON completo de cada corrida en stderr.
 */
import { spawn } from "node:child_process";
import { chromium } from "@playwright/test";

const BASE = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
const BACKENDS = (process.env.MEDIR_BACKENDS ?? "webgl,wasm").split(",").map((s) => s.trim());
const CPUS = (process.env.MEDIR_CPU ?? "1,4,6").split(",").map(Number);
const SEGUNDOS = Number(process.env.MEDIR_SEGUNDOS ?? 60);

async function responde(url) {
  try {
    const r = await fetch(url);
    return r.status < 500;
  } catch {
    return false;
  }
}

async function levantarServidor() {
  if (await responde(BASE)) return null;
  const puerto = process.env.PLAYWRIGHT_DEV_PORT;
  const proceso = spawn(puerto ? `PORT=${puerto} npm run dev` : "npm run dev", {
    shell: true,
    stdio: "ignore",
    detached: true,
  });
  const limite = Date.now() + 60_000;
  while (Date.now() < limite) {
    if (await responde(BASE)) return proceso;
    await new Promise((r) => setTimeout(r, 1000));
  }
  process.kill(-proceso.pid);
  throw new Error(`El servidor de desarrollo no respondió en ${BASE}`);
}

async function medir(navegador, backend, cpu) {
  const contexto = await navegador.newContext({ permissions: ["camera"] });
  const pagina = await contexto.newPage();
  try {
    const cdp = await contexto.newCDPSession(pagina);
    await cdp.send("Emulation.setCPUThrottlingRate", { rate: cpu });
    const q = new URLSearchParams({ backend });
    if (SEGUNDOS !== 60) q.set("segundos", String(SEGUNDOS));
    await pagina.goto(`${BASE}/diagnostico/clasificador?${q}`, { timeout: 120_000 });
    await pagina.getByRole("button", { name: "Empezar medición" }).click({ timeout: 120_000 });
    await pagina.waitForFunction(
      () => ["listo", "error"].includes(window.__diagnostico?.fase),
      undefined,
      { timeout: (SEGUNDOS + 120) * 1000, polling: 1000 },
    );
    const estado = await pagina.evaluate(() => window.__diagnostico);
    if (estado.fase === "error") return { backend, cpu, error: estado.mensaje };
    return { ...estado.resultado, cpu };
  } catch (e) {
    return { backend, cpu, error: e instanceof Error ? e.message.split("\n")[0] : String(e) };
  } finally {
    await contexto.close();
  }
}

const servidor = await levantarServidor();
const navegador = await chromium.launch({
  ...(process.env.CHROMIUM_PATH ? { executablePath: process.env.CHROMIUM_PATH } : {}),
  args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"],
});

const filas = [];
try {
  // Una corrida de calentamiento descartada: la primera compila la ruta en `next dev`.
  await medir(navegador, "wasm", 1).catch(() => {});
  for (const backend of BACKENDS) {
    for (const cpu of CPUS) {
      const r = await medir(navegador, backend, cpu);
      console.error(JSON.stringify(r));
      filas.push(r);
    }
  }
} finally {
  await navegador.close();
  if (servidor) process.kill(-servidor.pid);
}

console.log(`| Backend pedido | Backend real | CPU | Carga (ms) | Muestras | p50 | p95 | Máx | Salteados | Tensores inicio → final | Detección estimada 1 / 2 tracks (ms) |`);
console.log(`|---|---|---|---|---|---|---|---|---|---|---|`);
for (const r of filas) {
  if (r.error) {
    console.log(`| ${r.backend} | — | ${r.cpu}× | error: ${r.error} | | | | | | | |`);
    continue;
  }
  console.log(
    `| ${r.backendPedido} | ${r.backend} | ${r.cpu}× | ${r.cargaMs} | ${r.muestras} | ${r.p50} | ${r.p95} | ${r.max} | ${r.salteados} | ${r.tensoresInicio} → ${r.tensoresFinal} | ${r.deteccionEstimadaMs.unTrack} / ${r.deteccionEstimadaMs.dosTracks} |`,
  );
}
