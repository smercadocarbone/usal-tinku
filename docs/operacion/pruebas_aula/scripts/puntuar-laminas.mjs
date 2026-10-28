// ADR-M3-07 §4bis: puntúa con NSFWJS (el mismo modelo del aula) las imágenes de una carpeta y
// compara la regla entre adultos (Porn+Hentai ≥ 0,85) con el modo salud (Porn ≥ 0,95).
// Uso: con `npm run dev` corriendo, `node puntuar-laminas.mjs <carpeta>`.
import { chromium } from "playwright";
import fs from "node:fs"; import path from "node:path";
const dir = process.argv[2];
const nav = await chromium.launch();
const page = await nav.newPage();
await page.goto("http://localhost:3000/presentacion/index.html");
await page.addScriptTag({ url: "/presentacion/media/vendor/nsfwjs-4.3.0.min.js" });
await page.evaluate(async () => { window.__m = await nsfwjs.load("/modelos/nsfw/model.json", { size: 224 }); });
for (const f of fs.readdirSync(dir).sort()) {
  const ext = path.extname(f).slice(1).replace("jpg", "jpeg");
  const url = `data:image/${ext};base64,` + fs.readFileSync(path.join(dir, f)).toString("base64");
  const p = await page.evaluate(async (u) => {
    const img = new Image(); img.src = u; await img.decode();
    const c = document.createElement("canvas"); c.width = c.height = 224;
    const x = c.getContext("2d"); x.fillStyle = "#fff"; x.fillRect(0, 0, 224, 224); x.drawImage(img, 0, 0, 224, 224);
    const r = await window.__m.classify(c); const o = {}; r.forEach((k) => (o[k.className] = k.probability)); return o;
  }, url);
  const f2 = (v) => (v ?? 0).toFixed(3);
  const adultos = (p.Porn ?? 0) + (p.Hentai ?? 0), menor = adultos + (p.Sexy ?? 0), salud = p.Porn ?? 0;
  console.log([f.padEnd(36), "P", f2(p.Porn), "H", f2(p.Hentai), "S", f2(p.Sexy), "N", f2(p.Neutral), "D", f2(p.Drawing), "| adultos", f2(adultos), adultos >= .85 ? "CORTA" : "ok   ", "| salud", f2(salud), salud >= .95 ? "CORTA" : "ok"].join(" "));
}
await nav.close();
