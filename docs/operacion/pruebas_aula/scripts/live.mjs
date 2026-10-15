// Prueba en vivo del aula y del kill-switch contra tinku.site (pasarela en bypass).
// Uso: node live.mjs   (espera sola a que exista la sala; deja capturas y live-result.json)
import { chromium } from 'playwright';
import fs from 'node:fs';

const API = 'https://api.tinku.site', WEB = 'https://tinku.site';
const OUT = new URL('.', import.meta.url).pathname;
const S = { A: '4e29e2e9-b01b-433b-aed5-d2bdeea9e408', B: '7daf2062-26f7-423f-a313-74fdce1cc340' };
const R = []; // resultados
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rec = (id, esperado, obtenido, ok, nota = '') => { R.push({ id, esperado, obtenido, ok, nota }); log(ok ? 'OK ' : 'MAL', id, '→', obtenido, nota); };

async function api(method, token, path, body) {
  const r = await fetch(API + path, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text(); let j; try { j = JSON.parse(t); } catch { j = t; }
  return { s: r.status, j };
}
async function login(dni, pw = 'password123') {
  let r;
  for (let i = 0; i < 6; i++) {
    r = await fetch(API + '/api/usuarios/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ dni, password: pw }) });
    if (r.status !== 429) break;
    log('login 429, espero 20 s…'); await sleep(20_000);
  }
  const j = await r.json(); const p = JSON.parse(Buffer.from(j.token.split('.')[1], 'base64url')); return { token: j.token, id: p.sub, exp: j.expiresInMinutes };
}

const U = {};
for (const [k, dni, pw] of [['est', '40000001'], ['tut', '30111222'], ['ar', '40000012'], ['mia', '50000001'], ['lor', '31000028'], ['mod', '99900301', process.env.ADMIN_PASS], ['x', '40000002']]) U[k] = await login(dni, pw);

const PROXY = process.env.HTTPS_PROXY || process.env.https_proxy;
const browser = await chromium.launch({
  executablePath: '/opt/pw-browsers/chromium',
  proxy: PROXY ? { server: PROXY } : undefined,
  args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required'],
});

async function abrir(usuario, sesionId, etiqueta) {
  const ctx = await browser.newContext({ permissions: ['camera', 'microphone'], viewport: { width: 1280, height: 720 }, ignoreHTTPSErrors: true });
  await ctx.addCookies([{ name: 'tinku_jwt', value: encodeURIComponent(usuario.token), url: WEB }]);
  await ctx.addInitScript((t) => { try { localStorage.setItem('tinku_jwt', t); } catch {} }, usuario.token);
  const page = await ctx.newPage();
  const info = { etiqueta, consola: [], fallidas: [], modelo: 0, ws: [] };
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) info.consola.push(`${m.type()}: ${m.text().slice(0, 200)}`); });
  page.on('requestfailed', (q) => info.fallidas.push(`${q.method()} ${q.url().slice(0, 120)} ${q.failure()?.errorText}`));
  page.on('response', (r) => { if (r.url().includes('/modelos/nsfw/')) info.modelo++; if (r.status() >= 400 && r.url().includes('api.tinku.site')) info.fallidas.push(`${r.status()} ${r.request().method()} ${r.url().replace(API, '')}`); });
  page.on('websocket', (w) => info.ws.push(w.url().slice(0, 60)));
  await page.goto(`${WEB}/aula/${sesionId}`, { waitUntil: 'domcontentloaded' });
  return { ctx, page, info };
}
async function unirse(c) {
  const btn = c.page.getByRole('button', { name: /Unirme a la clase/ });
  await btn.waitFor({ timeout: 90_000 });
  const t0 = Date.now();
  await c.page.waitForFunction(() => { const b = [...document.querySelectorAll('button')].find((x) => /Unirme a la clase/.test(x.textContent || '')); return b && !b.disabled; }, null, { timeout: 90_000 });
  c.info.msHastaPoderUnirse = Date.now() - t0;
  await btn.click();
}
const texto = (c) => c.page.evaluate(() => document.body.innerText.replace(/\s+/g, ' ').slice(0, 400));
async function videos(c) { return c.page.evaluate(() => [...document.querySelectorAll('video')].map((v) => ({ w: v.videoWidth, h: v.videoHeight, t: +v.currentTime.toFixed(1), pausado: v.paused, src: !!v.srcObject }))); }

async function clipWebm() {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true }); const p = await ctx.newPage(); await p.goto('about:blank');
  const b64 = await p.evaluate(async () => {
    const c = document.createElement('canvas'); c.width = 320; c.height = 240; const g = c.getContext('2d'); let i = 0;
    const iv = setInterval(() => { g.fillStyle = `hsl(${(i++ * 9) % 360},60%,50%)`; g.fillRect(0, 0, 320, 240); g.fillStyle = '#fff'; g.fillText('clip de prueba ' + i, 20, 120); }, 66);
    const rec = new MediaRecorder(c.captureStream(15), { mimeType: 'video/webm;codecs=vp8', videoBitsPerSecond: 300000 }); const parts = [];
    rec.ondataavailable = (e) => parts.push(e.data); rec.start(1000); await new Promise((r) => setTimeout(r, 4500)); const done = new Promise((r) => (rec.onstop = r)); rec.stop(); await done; clearInterval(iv);
    const buf = new Uint8Array(await new Blob(parts).arrayBuffer()); let s = ''; for (const x of buf) s += String.fromCharCode(x); return btoa(s);
  });
  await ctx.close(); return Buffer.from(b64, 'base64');
}
async function subirClip(token, sesionId, buf, seg = 4) {
  const fd = new FormData(); fd.append('clip', new Blob([buf], { type: 'video/webm' }), 'evidencia.webm');
  const r = await fetch(`${API}/api/sesiones/${sesionId}/evidencia?duracionSegundos=${seg}`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd });
  return { s: r.status, j: await r.text() };
}

// ---- esperar a que exista la sala (T-5 min) ----
log('esperando la sala…');
for (;;) { const r = await api('POST', U.est.token, `/api/sesiones/${S.A}/token?clasificadorListo=true`); if (r.s === 200) break; if (r.s !== 422) { log('token A inesperado', r.s, JSON.stringify(r.j)); } await sleep(20_000); }
log('sala lista');
{
  const t = await api('POST', U.est.token, `/api/sesiones/${S.A}/token?clasificadorListo=true`);
  const host = new URL(String(t.j.livekitUrl).replace(/^wss?:/, 'https:')).host;
  log('LiveKit host =', host);
  fs.writeFileSync(`${OUT}livekit-host.txt`, host);
  try { const r = await fetch(`https://${host}`, { signal: AbortSignal.timeout(15_000) }); log('LiveKit alcanzable, HTTP', r.status); }
  catch (e) { log('LIVEKIT_BLOQUEADO', host, String(e.cause?.message || e.message)); fs.writeFileSync(`${OUT}livekit-bloqueado.txt`, host); process.exit(2); }
}
const clip = await clipWebm(); log('clip sintético', clip.length, 'bytes');

// ============ BLOQUE 0: la puerta del token (sin navegador) ============
{
  let r = await api('POST', U.ar.token, `/api/sesiones/${S.B}/token?clasificadorListo=false`);
  rec('PUERTA-1 menor sin clasificador', '409 CLASIFICADOR_NO_LISTO', `${r.s} ${r.j.codigo}`, r.s === 409 && r.j.codigo === 'CLASIFICADOR_NO_LISTO');
  r = await api('POST', U.x.token, `/api/sesiones/${S.B}/token?clasificadorListo=false`);
  rec('PUERTA-2 tercero', '403', String(r.s), r.s === 403);
  r = await api('POST', U.est.token, `/api/sesiones/${S.A}/token?clasificadorListo=false`);
  rec('PUERTA-3 adultos sin clasificador', '200 hayMenor=false', `${r.s} hayMenor=${r.j.hayMenor}`, r.s === 200 && r.j.hayMenor === false);
  r = await api('POST', U.lor.token, `/api/sesiones/${S.B}/token?clasificadorListo=true`);
  rec('PUERTA-4 menor con clasificador', '200 hayMenor=true, pizarra apagada', `${r.s} hayMenor=${r.j.hayMenor} pizarra=${r.j.pizarraHabilitada}`, r.s === 200 && r.j.hayMenor === true && r.j.pizarraHabilitada === false);
}

// ============ BLOQUE A: aula entre adultos + rama adultos ============
log('=== A: adultos ===');
const a1 = await abrir(U.est, S.A, 'A-estudiante'), a2 = await abrir(U.tut, S.A, 'A-tutor');
await Promise.all([unirse(a1), unirse(a2)]);
await sleep(25_000);
for (const c of [a1, a2]) await c.page.screenshot({ path: `${OUT}A-${c.info.etiqueta}-en-clase.png` });
const sesA = await api('GET', U.est.token, `/api/sesiones/por-reserva/267ba00f-2663-4b12-85c1-9ba068054f5d`);
rec('AULA-1 sesión en curso con dos participantes', 'en_curso', sesA.j.estado, sesA.j.estado === 'en_curso');
const vA = [await videos(a1), await videos(a2)];
rec('AULA-2 cada uno recibe video del otro', 'video remoto con dimensiones', JSON.stringify(vA), vA.every((v) => v.some((x) => x.w > 0 && x.src)));
rec('AULA-3 el modelo del clasificador se descargó', '>0 archivos de /modelos/nsfw', `${a1.info.modelo}/${a2.info.modelo}`, a1.info.modelo > 0 && a2.info.modelo > 0);
const tA = [await texto(a1), await texto(a2)];
rec('AULA-4 no hay cartel de protección con adultos', 'sin "Reactivando"/"Verificando"', JSON.stringify(tA.map((t) => /Reactivando|Verificando/.test(t))), tA.every((t) => !/Reactivando|Verificando/.test(t)));
rec('AULA-5 tiempo hasta poder unirse', 'informativo', `${a1.info.msHastaPoderUnirse} ms / ${a2.info.msHastaPoderUnirse} ms`, true);

// disparo (afirmación del cliente, ADR-M3-02): estudiante detecta al tutor
let r = await api('POST', U.est.token, `/api/sesiones/${S.A}/killswitch`, { detectadoId: U.tut.id });
rec('RAMA-AD-1 disparo entre adultos NO corta', '200 en_curso', `${r.s} ${r.j.estado}`, r.s === 200 && r.j.estado === 'en_curso');
r = await api('GET', U.est.token, `/api/sesiones/${S.A}/killswitch`);
const vence = r.j.venceAt ? (new Date(r.j.venceAt) - Date.now()) / 1000 : null;
rec('RAMA-AD-2 confirmación pendiente con plazo de 5 min', 'pendiente, vence en ~300 s', `pendiente=${r.j.confirmacionPendiente} vence en ${vence?.toFixed(0)} s`, r.j.confirmacionPendiente === true && vence > 270 && vence <= 301);
r = await api('POST', U.tut.token, `/api/sesiones/${S.A}/killswitch/confirmacion`, { vio: true });
rec('RAMA-AD-3 el propio detectado no puede responder', '4xx', String(r.s), r.s >= 400 && r.s < 500, JSON.stringify(r.j));
r = await api('POST', U.est.token, `/api/sesiones/${S.A}/killswitch/confirmacion`, { vio: false });
rec('RAMA-AD-4 respuesta "No" → sigue la clase', '200 en_curso', `${r.s} ${r.j.estado}`, r.s === 200 && r.j.estado === 'en_curso');
await sleep(3000);
const conectados = [await texto(a1), await texto(a2)];
rec('RAMA-AD-5 los dos navegadores siguen en la sala tras el "No"', 'sin pantalla de cierre', JSON.stringify(conectados.map((t) => t.slice(0, 80))), conectados.every((t) => !/se cortó|finalizada|terminó/i.test(t)));
r = await api('GET', U.est.token, `/api/sesiones/${S.A}/killswitch`);
rec('RAMA-AD-6 ya no hay pendiente', 'pendiente=false', String(r.j.confirmacionPendiente), r.j.confirmacionPendiente === false);
// segundo disparo tras un "No" debe volver a preguntar
r = await api('POST', U.tut.token, `/api/sesiones/${S.A}/killswitch`, { detectadoId: U.est.id });
r = await api('GET', U.tut.token, `/api/sesiones/${S.A}/killswitch`);
rec('RAMA-AD-7 un segundo disparo tras un "No" vuelve a preguntar', 'pendiente=true', String(r.j.confirmacionPendiente), r.j.confirmacionPendiente === true);
r = await api('POST', U.est.token, `/api/sesiones/${S.A}/killswitch/confirmacion`, { vio: true }); // el estudiante ES el detectado → no puede
rec('RAMA-AD-8 el detectado (estudiante) no puede confirmar su propio caso', '4xx', String(r.s), r.s >= 400 && r.s < 500);
r = await api('POST', U.tut.token, `/api/sesiones/${S.A}/killswitch/confirmacion`, { vio: true }); // el tutor (el otro) dice Sí
rec('RAMA-AD-9 "Sí" corta la sesión', '200 finalizada', `${r.s} ${r.j.estado}`, r.s === 200 && r.j.estado === 'finalizada');
await sleep(6000);
const finA = [await texto(a1), await texto(a2)];
for (const [i, c] of [a1, a2].entries()) await c.page.screenshot({ path: `${OUT}A-${c.info.etiqueta}-tras-corte.png` });
rec('RAMA-AD-10 los dos navegadores salen de la sala', 'pantalla de cierre o sala cerrada', JSON.stringify(finA.map((t) => t.slice(0, 120))), finA.every((t) => !/Esperando al otro participante/.test(t)));
const up = await subirClip(U.tut.token, S.A, clip);
rec('EVID-1 subir clip de evidencia (WebM) tras el corte', '200', `${up.s} ${up.j.slice(0, 100)}`, up.s === 200);
const bad = await subirClip(U.tut.token, S.A, Buffer.from('no es un video'));
rec('EVID-2 archivo que no es video se rechaza', '422', String(bad.s), bad.s === 422);
r = await api('GET', U.mod.token, '/api/admin/moderacion/alertas');
const alA = Array.isArray(r.j) ? r.j.find((x) => x.sesionId === S.A) : null;
rec('ALERTA-A alerta de la rama adultos en la cola de moderación', 'rama=adultos con clip', JSON.stringify(alA)?.slice(0, 200), !!alA && alA.rama === 'adultos');
r = await api('POST', U.est.token, `/api/sesiones/${S.A}/token?clasificadorListo=true`);
rec('CORTE-A sesión cortada no entrega tokens nuevos', '422', String(r.s), r.s === 422, JSON.stringify(r.j));
for (const c of [a1, a2]) { c.info.texto = await texto(c); }
fs.writeFileSync(`${OUT}A-consola.json`, JSON.stringify([a1.info, a2.info], null, 2));
await a1.ctx.close(); await a2.ctx.close();

// ============ BLOQUE B: aula con un menor + rama menor ============
log('=== B: menor ===');
const b1 = await abrir(U.mia, S.B, 'B-mia'), b2 = await abrir(U.lor, S.B, 'B-tutora');
await Promise.all([unirse(b1), unirse(b2)]);
await sleep(25_000);
for (const c of [b1, b2]) await c.page.screenshot({ path: `${OUT}B-${c.info.etiqueta}-en-clase.png` });
const sesB = await api('GET', U.ar.token, `/api/sesiones/por-reserva/193de8a2-5ff4-44a8-a067-5d4f1f306a5f`);
rec('MENOR-1 sesión con menor en curso', 'en_curso', sesB.j.estado, sesB.j.estado === 'en_curso');
const vB = [await videos(b1), await videos(b2)];
rec('MENOR-2 cada uno recibe video del otro', 'video remoto', JSON.stringify(vB), vB.every((v) => v.some((x) => x.w > 0 && x.src)));
rec('MENOR-3 el clasificador cargó antes de entrar (menor)', 'modelo descargado en los dos', `${b1.info.modelo}/${b2.info.modelo}`, b1.info.modelo > 0 && b2.info.modelo > 0);
const tB = [await texto(b1), await texto(b2)];
rec('MENOR-4 sin cartel de pérdida de protección en clase normal', 'sin "Reactivando"', JSON.stringify(tB.map((t) => /Reactivando/.test(t))), tB.every((t) => !/Reactivando/.test(t)), `Verificando: ${JSON.stringify(tB.map((t) => /Verificando/.test(t)))}`);
rec('MENOR-5 sin pizarra con menor', 'sin "Abrir pizarra"', JSON.stringify(tB.map((t) => /pizarra/i.test(t))), tB.every((t) => !/Abrir pizarra/i.test(t)));
rec('MENOR-6 tiempo hasta poder unirse', 'informativo', `${b1.info.msHastaPoderUnirse} ms / ${b2.info.msHastaPoderUnirse} ms`, true);

r = await api('POST', U.ar.token, `/api/sesiones/${S.B}/killswitch`, { detectadoId: U.lor.id, rama: 'adultos' });
rec('RAMA-MEN-1 disparo con menor corta directo (aunque el cliente mande rama=adultos)', '200 finalizada', `${r.s} ${r.j.estado}`, r.s === 200 && r.j.estado === 'finalizada');
r = await api('POST', U.mia.token, `/api/sesiones/${S.B}/killswitch/confirmacion`, { vio: false });
rec('RAMA-MEN-2 al menor no se le pregunta nada (no hay confirmación)', '4xx', String(r.s), r.s >= 400 && r.s < 500);
await sleep(6000);
for (const c of [b1, b2]) await c.page.screenshot({ path: `${OUT}B-${c.info.etiqueta}-tras-corte.png` });
const finB = [await texto(b1), await texto(b2)];
rec('RAMA-MEN-3 los dos navegadores salen de la sala', 'sin "Esperando…" ni video', JSON.stringify(finB.map((t) => t.slice(0, 140))), finB.every((t) => !/Esperando al otro participante/.test(t)));
r = await api('POST', U.mia.token, `/api/sesiones/${S.B}/token?clasificadorListo=true`);
rec('RAMA-MEN-4 el menor no puede volver a entrar', '422', String(r.s), r.s === 422);
r = await api('GET', U.mod.token, '/api/admin/moderacion/alertas');
const alB = Array.isArray(r.j) ? r.j.find((x) => x.sesionId === S.B) : null;
rec('ALERTA-B alerta de la rama menor en la cola', 'rama=menor', JSON.stringify(alB)?.slice(0, 200), !!alB && alB.rama === 'menor');
const upB = await subirClip(U.ar.token, S.B, clip);
rec('EVID-3 subir clip tras el corte con menor', '200', `${upB.s} ${upB.j.slice(0, 100)}`, upB.s === 200);
r = await api('GET', U.ar.token, '/api/notificaciones');
const nots = Array.isArray(r.j) ? r.j : (r.j?.content ?? []);
const nK = nots.find((n) => /KILLSWITCH/i.test(JSON.stringify(n)));
rec('AVISO-AR el Adulto Responsable recibe el aviso del corte', 'notificación KILLSWITCH_MENOR', nK ? JSON.stringify(nK).slice(0, 250) : `(${nots.length} notificaciones, ninguna)`, !!nK);
const rs = await api('GET', U.ar.token, `/api/reservas/193de8a2-5ff4-44a8-a067-5d4f1f306a5f`);
rec('PLATA-B estado de la reserva tras el corte', 'informativo', `${rs.j.estado}`, true);
for (const c of [b1, b2]) c.info.texto = await texto(c);
fs.writeFileSync(`${OUT}B-consola.json`, JSON.stringify([b1.info, b2.info], null, 2));
await b1.ctx.close(); await b2.ctx.close(); await browser.close();

fs.writeFileSync(`${OUT}live-result.json`, JSON.stringify(R, null, 2));
log('FIN. OK:', R.filter((x) => x.ok).length, 'MAL:', R.filter((x) => !x.ok).length);
