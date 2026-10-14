// Flujo REAL del cliente (build de desarrollo local) contra el backend y LiveKit de producción.
// El clasificador se reemplaza por un modelo falso controlable (window.__tinkuModeloFalso, solo existe en dev).
import { chromium } from 'playwright';
import fs from 'node:fs';

const API = 'https://api.tinku.site', WEB = 'http://localhost:3000';
const OUT = new URL('.', import.meta.url).pathname;
const SES = { C: '480ec3d4-1d39-475b-b637-81da571cde6d', D: 'd2dd49d3-2850-4100-992a-887a1828812e', E: 'd73218d4-f839-4b70-8034-c2acb4689a90' };
const ONLY = (process.env.ONLY || 'D,C,E').split(',');
const R = [];
const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const rec = (id, esperado, obtenido, ok, nota = '') => { R.push({ id, esperado, obtenido, ok, nota }); log(ok ? 'OK ' : 'MAL', id, '→', obtenido, nota); fs.writeFileSync(`${OUT}live2-result.json`, JSON.stringify(R, null, 2)); };

async function api(method, token, path, body) {
  const r = await fetch(API + path, { method, headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: body ? JSON.stringify(body) : undefined });
  const t = await r.text(); let j; try { j = JSON.parse(t); } catch { j = t; } return { s: r.status, j };
}
async function login(dni, pw = 'password123') {
  let r; for (let i = 0; i < 6; i++) { r = await fetch(API + '/api/usuarios/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ dni, password: pw }) }); if (r.status !== 429) break; await sleep(20_000); }
  const j = await r.json(); return { token: j.token, id: JSON.parse(Buffer.from(j.token.split('.')[1], 'base64url')).sub };
}
const U = {};
for (const [k, dni, pw] of [['est2', '40000002'], ['tut2', '30222333'], ['ar', '40000012'], ['mia', '50000001'], ['lor', '31000028'], ['mod', '99900301', process.env.ADMIN_PASS]]) U[k] = await login(dni, pw);

const PROXY = process.env.HTTPS_PROXY;
const browser = await chromium.launch({
  executablePath: '/opt/pw-browsers/chromium',
  proxy: { server: PROXY, bypass: 'localhost,127.0.0.1' },
  args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream', '--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--autoplay-policy=no-user-gesture-required', '--disable-web-security', '--disable-features=IsolateOrigins,site-per-process'],
});

const FAKE = () => {
  const mk = (c, p) => [{ className: c, probability: p }, { className: 'Neutral', probability: 1 - p }, { className: 'Drawing', probability: 0 }, { className: 'Hentai', probability: 0 }, { className: c === 'Porn' ? 'Sexy' : 'Porn', probability: 0 }];
  window.__ks = { modo: 'neutro', n: 0, err: 0 };
  window.__tinkuModeloFalso = {
    clasificar: async () => {
      window.__ks.n++;
      const m = window.__ks.modo;
      if (m === 'error') { window.__ks.err++; throw new Error('falla simulada del clasificador'); }
      if (m === 'colgado') return new Promise(() => {});
      if (m === 'porn') return mk('Porn', 0.97);
      if (m === 'sexy') return mk('Sexy', 0.92);
      return [{ className: 'Neutral', probability: 0.98 }, { className: 'Drawing', probability: 0.02 }, { className: 'Hentai', probability: 0 }, { className: 'Porn', probability: 0 }, { className: 'Sexy', probability: 0 }];
    },
    liberar() {},
    backend: 'falso',
  };
};

async function abrir(usuario, sesionId, etiqueta) {
  const ctx = await browser.newContext({ permissions: ['camera', 'microphone'], viewport: { width: 1280, height: 720 }, ignoreHTTPSErrors: true });
  await ctx.addCookies([{ name: 'tinku_jwt', value: encodeURIComponent(usuario.token), url: WEB }]);
  await ctx.addInitScript((t) => { try { localStorage.setItem('tinku_jwt', t); } catch {} }, usuario.token);
  await ctx.addInitScript(FAKE);
  const page = await ctx.newPage();
  const info = { etiqueta, llamadas: [], consola: [] };
  page.on('console', (m) => { if (m.type() === 'error') info.consola.push(m.text().slice(0, 200)); });
  page.on('request', (q) => { const u = q.url(); if (q.method() !== 'GET' && /\/api\/sesiones\//.test(u)) info.llamadas.push({ t: Date.now(), m: q.method(), p: u.replace(API, '').replace(sesionId, '{id}').split('?')[0], body: (q.postData() || '').slice(0, 120) }); });
  await page.goto(`${WEB}/aula/${sesionId}`, { waitUntil: 'domcontentloaded' });
  return { ctx, page, info, shot: (n) => page.screenshot({ path: `${OUT}L2-${etiqueta}-${n}.png` }) };
}
async function unirse(c) {
  const btn = c.page.getByRole('button', { name: /Unirme a la clase/ });
  await btn.waitFor({ timeout: 120_000 });
  await c.page.waitForFunction(() => { const b = [...document.querySelectorAll('button')].find((x) => /Unirme a la clase/.test(x.textContent || '')); return b && !b.disabled; }, null, { timeout: 120_000 });
  await btn.click();
  await c.page.getByText(/Finalizar clase/).first().waitFor({ timeout: 60_000 });
}
const texto = (c) => c.page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
const modo = (c, m) => c.page.evaluate((m) => { window.__ks.modo = m; }, m);
const ks = (c) => c.page.evaluate(() => ({ ...window.__ks }));
const videosRemotos = (c) => c.page.evaluate(() => [...document.querySelectorAll('video')].filter((v) => v.srcObject && v.videoWidth > 0 && v.offsetParent !== null).length);
async function esperarTexto(c, re, ms = 30_000) { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (re.test(await texto(c))) return Date.now() - t0; await sleep(300); } return null; }
async function esperarSala(sid) {
  for (;;) { const r = await api('POST', U.mod.token, `/api/sesiones/${sid}/token?clasificadorListo=true`); if (r.s === 403 || r.s === 200) return; await sleep(15_000); }
}
async function esperarSalaCon(user, sid) { for (;;) { const r = await api('POST', user.token, `/api/sesiones/${sid}/token?clasificadorListo=true`); if (r.s === 200) return; if (r.s !== 422) log('token inesperado', r.s, JSON.stringify(r.j).slice(0, 100)); await sleep(15_000); } }
const alertaDe = async (sid) => { const r = await api('GET', U.mod.token, '/api/admin/moderacion/alertas'); return Array.isArray(r.j) ? r.j.find((x) => x.sesionId === sid) : null; };

// ================== D: menor, detección forzada (Mía clasifica a Lorena) ==================
async function escenarioD() {
  log('=== D: menor con detección forzada ===');
  await esperarSalaCon(U.mia, SES.D);
  const mia = await abrir(U.mia, SES.D, 'D-mia'), lor = await abrir(U.lor, SES.D, 'D-lorena');
  await Promise.all([unirse(mia), unirse(lor)]);
  await sleep(15_000);
  rec('D-1 los dos en la sala con el clasificador (falso) corriendo', 'n clasificaciones > 3 en el cliente de Mía y video remoto', JSON.stringify({ n: (await ks(mia)).n, videos: await videosRemotos(mia) }), (await ks(mia)).n > 3 && (await videosRemotos(mia)) > 0);
  await mia.shot('antes');
  // "Sexy" con un menor cuenta (estricto, ADR-M3-07 §4)
  const t0 = Date.now();
  await modo(mia, 'sexy');
  const corte = await esperarTexto(mia, /se cortó por seguridad/i, 60_000);
  rec('D-2 con un menor, "Sexy" ≥ 0,85 durante 3 muestras dispara el corte', 'pantalla de corte de seguridad en Mía', corte ? `${((Date.now() - t0) / 1000).toFixed(1)} s desde que el modelo empezó a detectar` : 'no cortó', corte !== null);
  await mia.shot('corte');
  const textoMia = await texto(mia);
  rec('D-3 a la menor NO se le pregunta nada', 'sin diálogo "¿Viste algo…"', String(/¿Viste algo/.test(textoMia)), !/¿Viste algo/.test(textoMia));
  rec('D-4 el cartel no revela qué se detectó', 'sin palabras de contenido', textoMia.slice(0, 160), !/(porn|desnud|explícit|sexual)/i.test(textoMia));
  const pLor = await esperarTexto(lor, /se cortó por seguridad/i, 30_000);
  await lor.shot('corte');
  rec('D-5 la Tutora detectada también ve el cierre de seguridad (aviso de datos)', 'pantalla de corte en Lorena', pLor ? 'sí' : (await texto(lor)).slice(0, 140), pLor !== null);
  const llamadas = mia.info.llamadas.map((l) => `${l.m} ${l.p} ${l.body}`);
  rec('D-6 el cliente llamó a /killswitch (sin campo rama) y subió el clip', 'POST killswitch {detectadoId} + POST evidencia', JSON.stringify(llamadas), llamadas.some((l) => /POST \/api\/sesiones\/\{id\}\/killswitch \{"detectadoId"/.test(l) && !/rama/.test(l)) && llamadas.some((l) => /evidencia/.test(l)));
  await sleep(4000);
  const al = await alertaDe(SES.D);
  rec('D-7 alerta rama menor con clip real grabado del track remoto', 'rama=menor, clip presente', JSON.stringify(al)?.slice(0, 260), !!al && al.rama === 'menor' && (JSON.stringify(al).includes('clip') ? !/"tieneClip":false|"clip(Url)?":null/.test(JSON.stringify(al)) : true));
  if (al) { const clip = await fetch(`${API}/api/admin/moderacion/alertas/${al.id}/clip`, { headers: { Authorization: `Bearer ${U.mod.token}` } }); const b = Buffer.from(await clip.arrayBuffer()); fs.writeFileSync(`${OUT}clip-D.webm`, b); rec('D-8 el admin puede descargar el clip', '200, WebM, >1 KB, ≤5 MB', `${clip.status} ${b.length} bytes, magic=${b.subarray(0, 4).toString('hex')}`, clip.status === 200 && b.length > 1000 && b.length <= 5 * 1024 * 1024 && b.subarray(0, 4).toString('hex') === '1a45dfa3'); }
  const st = await api('GET', U.ar.token, `/api/sesiones/${SES.D}/killswitch`);
  rec('D-9 la sesión quedó cerrada y sin confirmación pendiente', 'pendiente=false', JSON.stringify(st.j), st.j.confirmacionPendiente === false);
  const tk = await api('POST', U.mia.token, `/api/sesiones/${SES.D}/token?clasificadorListo=true`);
  rec('D-10 no se puede volver a entrar', '422', String(tk.s), tk.s === 422);
  fs.writeFileSync(`${OUT}L2-D-llamadas.json`, JSON.stringify([mia.info, lor.info], null, 2));
  await mia.ctx.close(); await lor.ctx.close();
}

// ================== C: adultos, rama adultos con el cliente real ==================
async function escenarioC() {
  log('=== C: adultos, rama adultos ===');
  await esperarSalaCon(U.est2, SES.C);
  const est = await abrir(U.est2, SES.C, 'C-estudiante'), tut = await abrir(U.tut2, SES.C, 'C-tutor');
  await Promise.all([unirse(est), unirse(tut)]);
  await sleep(12_000);
  // 1) "Sexy" entre adultos NO cuenta
  await modo(tut, 'sexy'); await sleep(12_000);
  const dlg0 = /¿Viste algo/.test(await texto(tut));
  rec('C-1 entre adultos "Sexy" NO dispara nada (solo Porn/Hentai cuentan)', 'sin diálogo ni llamadas', JSON.stringify({ dialogo: dlg0, llamadas: tut.info.llamadas.length }), !dlg0 && tut.info.llamadas.length === 0);
  // 2) Porn: oculta, pregunta al que detectó; "No" restaura
  const t0 = Date.now(); await modo(tut, 'porn');
  const q = await esperarTexto(tut, /¿Viste algo incorrecto o ilegal/, 60_000);
  await modo(tut, 'neutro');
  rec('C-2 "Porn" ≥ 0,85 x3 oculta el video y pregunta al detector', 'diálogo en el cliente que detectó', q ? `${((Date.now() - t0) / 1000).toFixed(1)} s` : 'no apareció', q !== null);
  await tut.shot('pregunta');
  const aviso = await esperarTexto(est, /Tu video se ocultó un momento/, 20_000);
  await est.shot('video-oculto');
  rec('C-3 el detectado recibe el aviso de que su video se ocultó (sin decir por qué)', 'aviso informativo', aviso ? 'sí' : (await texto(est)).slice(0, 120), aviso !== null);
  const vTut = await videosRemotos(tut);
  rec('C-4 el video del detectado está oculto mientras se espera la respuesta', '0 videos remotos visibles', String(vTut), vTut === 0);
  const st1 = await api('GET', U.tut2.token, `/api/sesiones/${SES.C}/killswitch`);
  rec('C-5 el backend registra la confirmación pendiente con plazo de 5 min', 'pendiente, vence en ~300 s', `pendiente=${st1.j.confirmacionPendiente} vence en ${((new Date(st1.j.venceAt) - Date.now()) / 1000).toFixed(0)} s`, st1.j.confirmacionPendiente === true);
  await tut.page.getByRole('button', { name: /^No$/ }).click();
  await sleep(4000);
  const vTut2 = await videosRemotos(tut); const st2 = await api('GET', U.tut2.token, `/api/sesiones/${SES.C}/killswitch`);
  rec('C-6 respondiendo "No" vuelve el video y sigue la clase', 'video visible, sin pendiente', JSON.stringify({ videos: vTut2, pendiente: st2.j.confirmacionPendiente, dialogo: /¿Viste algo/.test(await texto(tut)) }), vTut2 > 0 && st2.j.confirmacionPendiente === false);
  await tut.shot('tras-no');

  // 3) vencimiento a los 5 minutos sin responder
  log('C: detección sin responder; espero el plazo de 5 min…');
  await sleep(8000);
  await modo(tut, 'porn'); const q2 = await esperarTexto(tut, /¿Viste algo incorrecto o ilegal/, 60_000); await modo(tut, 'neutro');
  const tVence0 = Date.now();
  rec('C-7 una segunda detección vuelve a preguntar', 'diálogo', q2 ? 'sí' : 'no', q2 !== null);
  const stA = await api('GET', U.tut2.token, `/api/sesiones/${SES.C}/killswitch`);
  let cerrado = null; for (let i = 0; i < 400; i++) { if (!/¿Viste algo incorrecto/.test(await texto(tut))) { cerrado = (Date.now() - tVence0) / 1000; break; } await sleep(1000); }
  const stB = await api('GET', U.tut2.token, `/api/sesiones/${SES.C}/killswitch`);
  rec('C-8 sin respuesta, a los 5 min el diálogo se cierra solo', '≈300 s', cerrado ? `${cerrado.toFixed(0)} s` : 'no se cerró', cerrado !== null && cerrado >= 270 && cerrado <= 330);
  rec('C-9 el backend resolvió la confirmación vencida como "No" (sin alerta, clase sigue)', 'pendiente=false y sin alerta', JSON.stringify({ pendiente: stB.j.confirmacionPendiente, alerta: !!(await alertaDe(SES.C)) }), stB.j.confirmacionPendiente === false && !(await alertaDe(SES.C)));
  rec('C-10 tras vencer vuelve el video del detectado', 'video visible', String(await videosRemotos(tut)), (await videosRemotos(tut)) > 0);
  await tut.shot('tras-vencimiento');

  // 4) "Sí" corta
  await sleep(5000);
  await modo(tut, 'porn'); const q3 = await esperarTexto(tut, /¿Viste algo incorrecto o ilegal/, 60_000); await modo(tut, 'neutro');
  await tut.page.getByRole('button', { name: /^Sí$/ }).click();
  const corte = await esperarTexto(tut, /se cortó por seguridad/i, 40_000);
  await sleep(5000);
  rec('C-11 "Sí" corta y el que respondió ve el cierre de seguridad', 'pantalla de corte', corte ? 'sí' : (await texto(tut)).slice(0, 140), corte !== null);
  await tut.shot('corte'); await est.shot('corte');
  const textoEst = await texto(est);
  rec('C-12 el detectado (adulto) también ve un mensaje claro de cierre', 'mensaje de corte, no "Esperando al otro participante"', textoEst.slice(0, 160), /se cortó|cortó por seguridad|finaliz/i.test(textoEst) && !/Esperando al otro participante/.test(textoEst), 'HALLAZGO si falla');
  const al = await alertaDe(SES.C);
  rec('C-13 alerta rama adultos con clip', 'rama=adultos', JSON.stringify(al)?.slice(0, 200), !!al && al.rama === 'adultos');
  fs.writeFileSync(`${OUT}L2-C-llamadas.json`, JSON.stringify([est.info, tut.info], null, 2));
  await est.ctx.close(); await tut.ctx.close();
}

// ================== E: menor, falla del clasificador ==================
async function escenarioE() {
  log('=== E: menor, falla del clasificador ===');
  await esperarSalaCon(U.mia, SES.E);
  const mia = await abrir(U.mia, SES.E, 'E-mia'), lor = await abrir(U.lor, SES.E, 'E-lorena');
  await Promise.all([unirse(mia), unirse(lor)]);
  await sleep(12_000);
  rec('E-1 clase normal con menor: video visible', '>0 videos', String(await videosRemotos(mia)), (await videosRemotos(mia)) > 0);
  // E2: cae y revive en el primer reintento
  let t0 = Date.now(); await modo(mia, 'error');
  const oculto = await esperarTexto(mia, /Reactivando la protección/, 20_000);
  const vOculto = await videosRemotos(mia);
  await modo(mia, 'neutro');
  rec('E-2 al caerse el clasificador con un menor, el video del otro se oculta ya', 'cartel y 0 videos', JSON.stringify({ cartel: oculto !== null, videos: vOculto, ms: oculto }), oculto !== null && vOculto === 0);
  await mia.shot('reactivando');
  let volvio = null; for (let i = 0; i < 40; i++) { if ((await videosRemotos(mia)) > 0) { volvio = (Date.now() - t0) / 1000; break; } await sleep(1000); }
  rec('E-3 revive en el primer reintento (≈5 s) y vuelve el video', 'video visible en ≲ 15 s', volvio ? `${volvio.toFixed(1)} s` : 'no volvió', volvio !== null && volvio < 25);
  const rep = mia.info.llamadas.filter((l) => /clasificador/.test(l.p)).map((l) => l.body);
  rec('E-4 el cliente reportó CAIDA y REACTIVADO', 'CAIDA, REACTIVADO', JSON.stringify(rep), rep.some((b) => /CAIDA/.test(b)) && rep.some((b) => /REACTIVADO/.test(b)));
  await sleep(6000);
  // E1: cae y no revive → 5 reintentos → corta
  await mia.page.route('**/modelos/nsfw/**', (r) => r.abort());
  await mia.page.evaluate(() => { window.__tinkuModeloFalso = undefined; });
  const n0 = mia.info.llamadas.length; t0 = Date.now();
  // el modelo falso ya no está: forzamos la caída haciendo que la próxima clasificación falle
  await mia.page.evaluate(() => { window.__ks.modo = 'error'; });
  // como __tinkuModeloFalso quedó undefined, el modelo en uso sigue siendo el falso ya cargado: alcanza con 'error'
  const cierre = await esperarTexto(mia, /no se pudo reactivar la protección/, 120_000);
  const seg = (Date.now() - t0) / 1000;
  rec('E-5 si no revive, tras los 5 reintentos corta (≈10 s + 5×5 s)', 'cierre "no se pudo reactivar" en ≈ 25–50 s', cierre ? `${seg.toFixed(0)} s` : 'no cortó', cierre !== null && seg >= 20 && seg <= 70);
  await mia.shot('sin-proteccion');
  const rep2 = mia.info.llamadas.slice(n0).filter((l) => /clasificador/.test(l.p)).map((l) => l.body);
  rec('E-6 el cliente reportó CAIDA y CORTE_SIN_REACTIVAR', 'CAIDA, CORTE_SIN_REACTIVAR', JSON.stringify(rep2), rep2.some((b) => /CAIDA/.test(b)) && rep2.some((b) => /CORTE_SIN_REACTIVAR/.test(b)));
  await sleep(5000); await lor.shot('tras-corte');
  const tl = await texto(lor);
  rec('E-7 la otra persona (Tutora) ve un mensaje claro, no queda esperando', 'mensaje de corte', tl.slice(0, 160), !/Esperando al otro participante/.test(tl), 'HALLAZGO si falla');
  const rs = await api('GET', U.ar.token, `/api/sesiones/por-reserva/0fa87337-cc2b-438a-b55b-f79b744346ce`);
  rec('E-8 estado de la sesión tras el corte por falta de protección', 'informativo', JSON.stringify(rs.j).slice(0, 200), true);
  fs.writeFileSync(`${OUT}L2-E-llamadas.json`, JSON.stringify([mia.info, lor.info], null, 2));
  await mia.ctx.close(); await lor.ctx.close();
}

try {
  for (const e of ONLY) { if (e === 'D') await escenarioD(); if (e === 'C') await escenarioC(); if (e === 'E') await escenarioE(); }
} catch (e) { log('EXCEPCIÓN', e.stack?.split('\n').slice(0, 4).join(' | ')); rec('EXCEPCIÓN', 'sin excepción', String(e.message).slice(0, 200), false); }
await browser.close();
log('FIN. OK:', R.filter((x) => x.ok).length, 'MAL:', R.filter((x) => !x.ok).length);
