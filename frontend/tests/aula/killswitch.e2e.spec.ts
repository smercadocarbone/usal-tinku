import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { test, expect, type Browser, type BrowserContext, type Page } from "@playwright/test";

/**
 * M3C-06 (T-M3-06f): E2E del kill-switch contra un backend, un Postgres y un LiveKit REALES
 * (`docker-compose.e2e.yml`, opción A de la spec). Nada de `/api/**` está mockeado: una detección
 * en el navegador tiene que terminar en una Alerta con clip.
 *
 * Corre con: `E2E_API_URL=http://localhost:8080 npm run test:e2e-real` (ver la spec para el resto).
 *
 * Qué NO es real:
 * - el modelo: el build real no tiene uno falso y no se puede usar contenido sensible (BR-KS-04).
 *   Con `npm run dev` se inyecta `window.__tinkuModeloFalso` (lo lee `cargarModelo` fuera de
 *   producción) y `window.__tinkuForzar = true` hace que devuelva `Porn: 0.99`;
 * - el reloj de la sala: la sala se crea a T-5 (Tabla de Tiempos) y una reserva necesita 21 min
 *   de anticipación. En vez de esperar, el test adelanta el trigger `sala-trigger-{sesionId}` de
 *   Quartz en la base (`next_fire_time = ahora`); el scheduler lo toma en su próxima vuelta
 *   (hasta ~30 s). El plazo de la Tabla no se toca;
 * - las verificaciones sin endpoint (escrow, estado de la sesión, confirmación vencida, tutor
 *   suspendido) se leen por SQL con `docker exec` sobre el contenedor de Postgres
 *   (`E2E_PG_CONTAINER`, default `tinku-postgres`). Por eso esta spec no corre contra staging
 *   (opción B) tal cual.
 *
 * Estado: los casos dejan datos (alertas, suspensiones preventivas, reservas). Se corren en orden
 * sobre una base recién sembrada (`docker compose ... down -v` antes); el orden evita que la
 * suspensión preventiva de un caso afecte al siguiente.
 */

const API = process.env.E2E_API_URL ?? "";
const PASSWORD = process.env.E2E_PASSWORD ?? "password123";
const TUTOR_CON_MENOR = process.env.E2E_TUTOR_DNI ?? "31000028";
const TUTOR_ADULTOS = "30111222";
/**
 * Segundo Tutor autorizado para Mía (también con CAP). E2E-KS-02 lo usa en vez de 31000028: una clase
 * que termina deja al Tutor con una calificación pendiente (M7) y no puede recibir otra reserva hasta
 * calificar, así que KS-02 y KS-01 no pueden compartir Tutor en la misma corrida.
 */
const TUTOR_CON_MENOR_2 = "30777888";
const AR = "40000012";
const MIA = "50000001";
const ESTUDIANTE = "40000001";
const ADMIN_MODERACION = process.env.E2E_ADMIN_MODERACION_DNI ?? "40000013";
const ADMIN_FINANCIERO = process.env.E2E_ADMIN_FINANCIERO_DNI ?? "40000014";
const PG = process.env.E2E_PG_CONTAINER ?? "tinku-postgres";
const DURACION = 60;

test.skip(!API, "E2E contra backend real: requiere E2E_API_URL (docker-compose.e2e.yml).");
test.describe.configure({ mode: "default" });

// ------------------------------------------------------------------ helpers HTTP

async function http<T = unknown>(metodo: string, ruta: string, jwt?: string, cuerpo?: unknown): Promise<{ status: number; body: T }> {
  const r = await fetch(`${API}${ruta}`, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(jwt ? { Authorization: `Bearer ${jwt}` } : {}) },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  });
  const texto = await r.text();
  let body: unknown = texto;
  try {
    body = texto ? JSON.parse(texto) : null;
  } catch {
    // no es JSON
  }
  return { status: r.status, body: body as T };
}

async function ok<T>(metodo: string, ruta: string, jwt?: string, cuerpo?: unknown): Promise<T> {
  const r = await http<T>(metodo, ruta, jwt, cuerpo);
  if (r.status >= 300) throw new Error(`${metodo} ${ruta} → ${r.status}: ${JSON.stringify(r.body)}`);
  return r.body;
}

const jwts = new Map<string, string>();
async function login(dni: string): Promise<string> {
  const r = await ok<{ token: string }>("POST", "/api/usuarios/login", undefined, { dni, password: PASSWORD });
  jwts.set(dni, r.token);
  return r.token;
}

async function idDe(jwt: string): Promise<string> {
  return (await ok<{ id: string }>("GET", "/api/usuarios/me", jwt)).id;
}

async function prenderBypass(jwtFinanciero: string) {
  const r = await ok<{ habilitada: boolean; bypassPermitido: boolean }>("PATCH", "/api/admin/financiero/pasarela", jwtFinanciero, {
    habilitada: false,
  });
  if (!r.bypassPermitido) throw new Error("El backend no permite el modo Bypass (¿perfil prod?): el E2E no puede pagar.");
  expect(r.habilitada).toBe(false);
}

// ------------------------------------------------------------------ helpers SQL (sin endpoint)

function sql(consulta: string): string {
  return execFileSync("docker", ["exec", PG, "psql", "-U", "tinku_dev", "-d", "tinku", "-tAc", consulta], { encoding: "utf8" }).trim();
}

const uuid = (s: string) => {
  if (!/^[0-9a-f-]{36}$/i.test(s)) throw new Error(`id inválido: ${s}`);
  return s;
};

function logBackend(): string {
  if (process.env.E2E_BACKEND_LOG) return readFileSync(process.env.E2E_BACKEND_LOG, "utf8");
  return execFileSync("docker", ["logs", process.env.E2E_BACKEND_CONTAINER ?? "tinku-backend"], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] });
}

// ------------------------------------------------------------------ reserva → sesión → sala

/** Último horario tomado en esta corrida: cada reserva va después, así no se pisan entre sí. */
let ultimoHorario = 0;

async function primerHorarioLibre(jwt: string, tutorId: string): Promise<string> {
  for (let d = 0; d < 14; d++) {
    const fecha = new Date(Date.now() + d * 86_400_000).toISOString().slice(0, 10);
    const slots = await ok<{ startTime: string; isAvailable: boolean }[]>(
      "GET",
      `/api/tutores/${tutorId}/horarios?fecha=${fecha}&duracionMinutos=${DURACION}`,
      jwt,
    );
    const libre = slots.find((s) => s.isAvailable && Date.parse(s.startTime) >= ultimoHorario + DURACION * 60_000);
    if (libre) {
      ultimoHorario = Date.parse(libre.startTime);
      return libre.startTime;
    }
  }
  throw new Error(`El Tutor ${tutorId} no tiene horarios libres en dos semanas`);
}

/** Reserva, paga en Bypass (reserva confirmada) y devuelve la sesión. */
async function reservarYPagar(jwtPagador: string, tutorId: string, beneficiarioId?: string): Promise<string> {
  const horario = await primerHorarioLibre(jwtPagador, tutorId);
  const reserva = await ok<{ id: string }>("POST", "/api/reservas", jwtPagador, {
    tutorId,
    beneficiarioId: beneficiarioId ?? null,
    horario,
    duracionMinutos: DURACION,
  });
  const pref = await ok<{ bypass: boolean }>("POST", "/api/pagos/preferencia", jwtPagador, { reservaId: reserva.id });
  expect(pref.bypass, "la preferencia tiene que salir en Bypass (reserva confirmada)").toBe(true);
  const sesion = await ok<{ id: string }>("GET", `/api/sesiones/por-reserva/${reserva.id}`, jwtPagador);
  return sesion.id;
}

/**
 * Adelanta `CrearSalaJob` de esta sesión (no la Tabla de Tiempos) y espera la sala.
 */
async function abrirSala(sesionId: string) {
  const id = uuid(sesionId);
  sql(`UPDATE public.qrtz_triggers SET next_fire_time = (extract(epoch from now()) * 1000)::bigint
       WHERE trigger_name = 'sala-trigger-${id}' AND trigger_group = 'm3-aula'`);
  await expect
    .poll(() => sql(`SELECT livekit_room_id IS NOT NULL FROM aula.sesiones_aprendizaje WHERE id = '${id}'`), {
      timeout: 90_000,
      intervals: [2_000],
      message: "la sala de LiveKit no se creó (¿Quartz o LiveKit caídos?)",
    })
    .toBe("t");
}

// ------------------------------------------------------------------ navegador

interface ModeloE2E {
  /** Desde que es true, cada inferencia devuelve Porn 0.99. */
  forzar: boolean;
  /** Desde que es true, cada inferencia tira un error (el clasificador "cae"). */
  romper: boolean;
  /** Cuántas cargas del modelo fallan a partir de ahora (-1 = todas). */
  cargasQueFallan: number;
}

/** `window.__tinkuModeloFalso` como getter: cada lectura es una carga del modelo. */
function instalarModeloFalso() {
  const w = window as unknown as Record<string, unknown> & { __tinkuE2E: ModeloE2E; __tinkuForzar?: boolean };
  w.__tinkuE2E = { forzar: false, romper: false, cargasQueFallan: 0 };
  const clases = ["Drawing", "Hentai", "Neutral", "Porn", "Sexy"];
  const modelo = {
    backend: "falso-e2e",
    liberar() {},
    async clasificar() {
      await new Promise((r) => setTimeout(r, 30));
      const cfg = w.__tinkuE2E;
      if (cfg.romper) throw new Error("inferencia rota (E2E)");
      const porn = cfg.forzar || w.__tinkuForzar === true;
      return clases.map((className) => ({
        className,
        probability: porn ? (className === "Porn" ? 0.99 : 0) : className === "Neutral" ? 0.99 : 0,
      }));
    },
  };
  Object.defineProperty(window, "__tinkuModeloFalso", {
    configurable: true,
    get() {
      const cfg = w.__tinkuE2E;
      if (cfg.cargasQueFallan !== 0) {
        if (cfg.cargasQueFallan > 0) cfg.cargasQueFallan--;
        throw new Error("el modelo no carga (E2E)");
      }
      cfg.romper = false; // un modelo recién cargado anda
      return modelo;
    },
  });
}

async function contextoCon(browser: Browser, jwt: string, conModelo = true): Promise<BrowserContext> {
  const ctx = await browser.newContext();
  const base = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";
  // Como `setFakeSession` de tests/helpers.ts, pero con el JWT real.
  await ctx.addCookies([{ name: "tinku_jwt", value: jwt, url: base }]);
  await ctx.addInitScript((t) => window.localStorage.setItem("tinku_jwt", t), jwt);
  if (conModelo) await ctx.addInitScript(instalarModeloFalso);
  return ctx;
}

async function entrar(page: Page, sesionId: string) {
  await page.goto(`/aula/${sesionId}`);
  const boton = page.getByRole("button", { name: "Unirme a la clase" });
  await expect(boton).toBeEnabled({ timeout: 40_000 });
  await boton.click();
}

/** Abre el aula en dos navegadores y espera que cada uno vea el video del otro. */
async function dosParticipantes(browser: Browser, jwtA: string, jwtB: string, sesionId: string) {
  const a = await (await contextoCon(browser, jwtA)).newPage();
  const b = await (await contextoCon(browser, jwtB)).newPage();
  await entrar(a, sesionId);
  await entrar(b, sesionId);
  // "En la sala" = conectado y con el otro presente; además, el video remoto tiene que tener frames.
  for (const p of [a, b]) {
    await expect(p.getByText("En la sala", { exact: true })).toBeVisible({ timeout: 60_000 });
    await expect(p.getByText("Esperando al otro participante")).toBeHidden({ timeout: 60_000 });
    await expect
      .poll(() => p.evaluate(() => [...document.querySelectorAll("video")].filter((v) => v.readyState >= 2).length), { timeout: 60_000 })
      .toBeGreaterThanOrEqual(2);
  }
  return { a, b };
}

async function forzarDeteccion(page: Page) {
  await page.evaluate(() => {
    (window as unknown as { __tinkuForzar: boolean }).__tinkuForzar = true;
  });
}

async function configurarModelo(page: Page, cambios: Partial<ModeloE2E>) {
  await page.evaluate((c) => Object.assign((window as unknown as { __tinkuE2E: ModeloE2E }).__tinkuE2E, c), cambios);
}

const CORTE = "La clase se cortó por seguridad";

async function alertaDe(jwtModeracion: string, sesionId: string) {
  const lista = await ok<{ sesionId: string; rama: string; detectadoId: string; tieneClip: boolean }[]>(
    "GET",
    "/api/admin/moderacion/alertas",
    jwtModeracion,
  );
  return lista.find((x) => x.sesionId === sesionId);
}

// ------------------------------------------------------------------ datos comunes

const ids: Record<string, string> = {};

test.beforeAll(async () => {
  for (const dni of [AR, MIA, ESTUDIANTE, TUTOR_CON_MENOR, TUTOR_CON_MENOR_2, TUTOR_ADULTOS, ADMIN_MODERACION, ADMIN_FINANCIERO]) {
    ids[dni] = await idDe(await login(dni));
  }
  await prenderBypass(jwts.get(ADMIN_FINANCIERO)!);
});

const jwt = (dni: string) => jwts.get(dni)!;
/** Clase de Mía (la paga el AR) con el Tutor que tiene CAP. */
const claseConMenor = () => reservarYPagar(jwt(AR), ids[TUTOR_CON_MENOR], ids[MIA]);
/** Clase entre adultos. */
const claseEntreAdultos = () => reservarYPagar(jwt(ESTUDIANTE), ids[TUTOR_ADULTOS]);

// ------------------------------------------------------------------ casos (en este orden)

test("E2E-KS-06: con un menor, si el modelo no carga no se entra ni se pide token", { tag: ["@aula", "@e2e-real", "@E2E-KS-06"] }, async ({ browser }) => {
  const sesionId = await claseConMenor();
  const page = await (await contextoCon(browser, jwt(MIA), false)).newPage();
  const tokens: string[] = [];
  page.on("request", (r) => {
    if (r.url().includes(`/api/sesiones/${sesionId}/token`)) tokens.push(r.url());
  });
  await page.route("**/modelos/nsfw/**", (r) => r.abort());
  await page.goto(`/aula/${sesionId}`);
  await expect(page.getByText("No pudimos activar la protección del aula en este dispositivo")).toBeVisible({ timeout: 40_000 });
  expect(tokens).toEqual([]);
});

test("E2E-KS-09: entre adultos, si el modelo no carga se entra igual y el backend registra NO_CARGA", { tag: ["@aula", "@e2e-real", "@E2E-KS-09"] }, async ({ browser }) => {
  const sesionId = await claseEntreAdultos();
  await abrirSala(sesionId);
  const page = await (await contextoCon(browser, jwt(ESTUDIANTE), false)).newPage();
  await page.route("**/modelos/nsfw/**", (r) => r.abort());
  const reporte = page.waitForResponse((r) => r.url().includes(`/api/sesiones/${sesionId}/clasificador`), { timeout: 40_000 });
  await entrar(page, sesionId);
  expect((await reporte).status()).toBe(204);
  await expect(page.getByText("Esperando al otro participante").first()).toBeVisible({ timeout: 30_000 });
  await expect.poll(() => logBackend()).toMatch(new RegExp(`evento=NO_CARGA sesion=${sesionId}`));
});

test("E2E-KS-03: US-7, el Estudiante responde «No» y la clase sigue", { tag: ["@aula", "@e2e-real", "@E2E-KS-03"] }, async ({ browser }) => {
  const sesionId = await claseEntreAdultos();
  await abrirSala(sesionId);
  const { a: estudiante } = await dosParticipantes(browser, jwt(ESTUDIANTE), jwt(TUTOR_ADULTOS), sesionId);
  await forzarDeteccion(estudiante);
  const dialogo = estudiante.getByRole("dialog");
  await expect(dialogo).toContainText("¿Viste algo incorrecto o ilegal", { timeout: 30_000 });
  await expect(estudiante.getByText("Video oculto por seguridad")).toBeVisible();
  await estudiante.evaluate(() => ((window as unknown as { __tinkuForzar: boolean }).__tinkuForzar = false));
  await dialogo.getByRole("button", { name: "No", exact: true }).click();
  await expect(estudiante.getByText("Video oculto por seguridad")).toBeHidden({ timeout: 10_000 });
  const estado = await ok<{ confirmacionPendiente: boolean }>("GET", `/api/sesiones/${sesionId}/killswitch`, jwt(ESTUDIANTE));
  expect(estado.confirmacionPendiente).toBe(false);
  expect(sql(`SELECT estado FROM aula.sesiones_aprendizaje WHERE id = '${uuid(sesionId)}'`)).toBe("en_curso");
  expect(await alertaDe(jwt(ADMIN_MODERACION), sesionId)).toBeUndefined();
});

test("E2E-KS-05: US-7, la pregunta vence a los 5 min y una detección nueva vuelve a preguntar", { tag: ["@aula", "@e2e-real", "@lento", "@E2E-KS-05"] }, async ({ browser }) => {
  // Plazo de la Tabla de Tiempos ("Plazo de respuesta de la rama adultos": 5 min) + margen. No se acorta.
  test.setTimeout(10 * 60_000);
  const sesionId = await claseEntreAdultos();
  await abrirSala(sesionId);
  const { a: estudiante } = await dosParticipantes(browser, jwt(ESTUDIANTE), jwt(TUTOR_ADULTOS), sesionId);
  await forzarDeteccion(estudiante);
  const dialogo = estudiante.getByRole("dialog");
  await expect(dialogo).toContainText("¿Viste algo incorrecto o ilegal", { timeout: 30_000 });
  await estudiante.evaluate(() => ((window as unknown as { __tinkuForzar: boolean }).__tinkuForzar = false));
  await expect(dialogo).toBeHidden({ timeout: 6 * 60_000 });
  await expect(estudiante.getByText("Video oculto por seguridad")).toBeHidden({ timeout: 10_000 });
  await expect
    .poll(() => sql(`SELECT vencida FROM aula.confirmaciones_killswitch WHERE sesion_id = '${uuid(sesionId)}'`), { timeout: 60_000 })
    .toBe("t");
  expect(sql(`SELECT estado FROM aula.sesiones_aprendizaje WHERE id = '${uuid(sesionId)}'`)).toBe("en_curso");
  // Un disparo nuevo vuelve a preguntar.
  await forzarDeteccion(estudiante);
  await expect(dialogo).toContainText("¿Viste algo incorrecto o ilegal", { timeout: 30_000 });
});

test("E2E-KS-08: US-6bis, el clasificador cae y revive en el segundo intento", { tag: ["@aula", "@e2e-real", "@E2E-KS-08"] }, async ({ browser }) => {
  const sesionId = await claseConMenor();
  await abrirSala(sesionId);
  const { a: mia } = await dosParticipantes(browser, jwt(MIA), jwt(TUTOR_CON_MENOR), sesionId);
  await configurarModelo(mia, { cargasQueFallan: 1, romper: true });
  await expect(mia.getByText("Reactivando la protección…")).toBeVisible({ timeout: 15_000 });
  // 1.er intento a los 5 s (falla), 2.º a los 10 s (carga).
  await expect(mia.getByText("Reactivando la protección…")).toBeHidden({ timeout: 30_000 });
  await expect(mia.getByText("Esperando al otro participante")).toBeHidden();
  await expect(mia.getByText(CORTE)).toHaveCount(0);
  expect(sql(`SELECT estado FROM aula.sesiones_aprendizaje WHERE id = '${uuid(sesionId)}'`)).toBe("en_curso");
  await expect.poll(() => logBackend()).toMatch(new RegExp(`evento=REACTIVADO sesion=${sesionId}`));
});

test("E2E-KS-07: US-6bis, el clasificador cae y no revive: Mía sale de la sala", { tag: ["@aula", "@e2e-real", "@E2E-KS-07"] }, async ({ browser }) => {
  const sesionId = await claseConMenor();
  await abrirSala(sesionId);
  const { a: mia } = await dosParticipantes(browser, jwt(MIA), jwt(TUTOR_CON_MENOR), sesionId);
  await configurarModelo(mia, { cargasQueFallan: -1, romper: true });
  await expect(mia.getByText("Reactivando la protección…")).toBeVisible({ timeout: 15_000 });
  // Hasta ~35 s (10 s sin respuesta + 5 intentos cada 5 s); con error inmediato son ~25 s.
  await expect(mia.getByRole("heading", { name: "Se cortó la clase" })).toBeVisible({ timeout: 60_000 });
  await expect(mia.getByText("no se pudo reactivar la protección del aula")).toBeVisible();
  await expect.poll(() => logBackend(), { timeout: 15_000 }).toMatch(new RegExp(`evento=CAIDA sesion=${sesionId}`));
  await expect.poll(() => logBackend(), { timeout: 15_000 }).toMatch(new RegExp(`evento=CORTE_SIN_REACTIVAR sesion=${sesionId}`));
  // Camino de conectividad (US-5): no hay corte por seguridad, la sesión sigue y Mía figura desconectada.
  await expect
    .poll(() => sql(`SELECT estado || ',' || estudiante_conectado FROM aula.sesiones_aprendizaje WHERE id = '${uuid(sesionId)}'`), { timeout: 30_000 })
    .toBe("en_curso,false");
  expect(await alertaDe(jwt(ADMIN_MODERACION), sesionId)).toBeUndefined();
});

test("E2E-KS-02: US-6, caso borde 4: se detecta a la menor y se corta para los dos", { tag: ["@aula", "@e2e-real", "@E2E-KS-02"] }, async ({ browser }) => {
  const sesionId = await reservarYPagar(jwt(AR), ids[TUTOR_CON_MENOR_2], ids[MIA]);
  await abrirSala(sesionId);
  const { a: mia, b: tutor } = await dosParticipantes(browser, jwt(MIA), jwt(TUTOR_CON_MENOR_2), sesionId);
  await forzarDeteccion(tutor);
  for (const p of [tutor, mia]) await expect(p.getByText(CORTE).first()).toBeVisible({ timeout: 40_000 });
  await expect.poll(async () => (await alertaDe(jwt(ADMIN_MODERACION), sesionId))?.tieneClip, { timeout: 30_000 }).toBe(true);
  const alerta = (await alertaDe(jwt(ADMIN_MODERACION), sesionId))!;
  expect(alerta.rama).toBe("menor");
  expect(alerta.detectadoId).toBe(ids[MIA]);
  expect(sql(`SELECT estado FROM aula.sesiones_aprendizaje WHERE id = '${uuid(sesionId)}'`)).toBe("finalizada");
  // Riesgo aceptado en Spec_M3 US-6: el Tutor no queda suspendido.
  expect(sql(`SELECT activo_para_matching FROM identidad.usuarios WHERE id = '${uuid(ids[TUTOR_CON_MENOR_2])}'`)).toBe("t");
});

test("E2E-KS-01: US-6, se detecta al Tutor: corte, Alerta con clip, escrow en pausa y aviso al AR", { tag: ["@aula", "@e2e-real", "@E2E-KS-01"] }, async ({ browser }) => {
  const sesionId = await claseConMenor();
  await abrirSala(sesionId);
  const { a: mia, b: tutor } = await dosParticipantes(browser, jwt(MIA), jwt(TUTOR_CON_MENOR), sesionId);
  await forzarDeteccion(mia);
  for (const p of [mia, tutor]) await expect(p.getByText(CORTE).first()).toBeVisible({ timeout: 40_000 });
  await expect.poll(async () => (await alertaDe(jwt(ADMIN_MODERACION), sesionId))?.tieneClip, { timeout: 30_000 }).toBe(true);
  const alerta = (await alertaDe(jwt(ADMIN_MODERACION), sesionId))!;
  expect(alerta.rama).toBe("menor");
  expect(alerta.detectadoId).toBe(ids[TUTOR_CON_MENOR]);
  const id = uuid(sesionId);
  expect(sql(`SELECT estado FROM aula.sesiones_aprendizaje WHERE id = '${id}'`)).toBe("finalizada");
  // Con la sesión cerrada nadie vuelve a recibir token (los participant_left del corte no la reabren).
  expect((await http("POST", `/api/sesiones/${sesionId}/token?clasificadorListo=true`, jwt(TUTOR_CON_MENOR))).status).toBeGreaterThanOrEqual(400);
  // Sin endpoint de escrow por reserva: SQL (documentado en la spec).
  await expect
    .poll(() => sql(`SELECT t.estado FROM pagos.transacciones t JOIN aula.sesiones_aprendizaje s ON s.reserva_id = t.reserva_id WHERE s.id = '${id}'`), {
      timeout: 15_000,
    })
    .toBe("pausado_alerta");
  const avisos = await ok<{ tipo: string; datos: Record<string, string> }[]>("GET", "/api/notificaciones", jwt(AR));
  expect(avisos.some((n) => n.tipo === "KILLSWITCH_MENOR" && n.datos.sesionId === sesionId)).toBe(true);
});

test("E2E-KS-04: US-7, el Estudiante responde «Sí»: corte y Alerta de adultos con clip", { tag: ["@aula", "@e2e-real", "@E2E-KS-04"] }, async ({ browser }) => {
  const sesionId = await claseEntreAdultos();
  await abrirSala(sesionId);
  const { a: estudiante, b: tutor } = await dosParticipantes(browser, jwt(ESTUDIANTE), jwt(TUTOR_ADULTOS), sesionId);
  await forzarDeteccion(estudiante);
  const dialogo = estudiante.getByRole("dialog");
  await expect(dialogo).toContainText("¿Viste algo incorrecto o ilegal", { timeout: 30_000 });
  await dialogo.getByRole("button", { name: "Sí", exact: true }).click();
  for (const p of [estudiante, tutor]) await expect(p.getByText(CORTE).first()).toBeVisible({ timeout: 40_000 });
  await expect.poll(async () => (await alertaDe(jwt(ADMIN_MODERACION), sesionId))?.tieneClip, { timeout: 30_000 }).toBe(true);
  const alerta = (await alertaDe(jwt(ADMIN_MODERACION), sesionId))!;
  expect(alerta.rama).toBe("adultos");
  expect(alerta.detectadoId).toBe(ids[TUTOR_ADULTOS]);
  expect(sql(`SELECT estado FROM aula.sesiones_aprendizaje WHERE id = '${uuid(sesionId)}'`)).toBe("finalizada");
});
