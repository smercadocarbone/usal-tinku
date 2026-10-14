# M3C-07 — Latencia del clasificador: página de diagnóstico y medición con la CPU frenada

**Tarea:** T-M3-06g · **Riesgo:** bajo · **Depende de:** M3C-03 · **Commits:** 2 (página; anexo con números)

## 1. Contexto

No hay un celular de gama media disponible, así que la latencia se mide en Chromium frenando la CPU.
`Emulation.setCPUThrottlingRate` es el método de Lighthouse y su perfil de gama media es 4×.

La primera medición, con el modelo suelto y no con el código del aula, ya está en el anexo de
ADR-M3-01 (2026-10-14):

| Backend | CPU | p50 | p95 |
|---|---|---|---|
| wasm | 4× | ~370 ms | ~510 ms |
| wasm | 6× | ~620 ms | ~780 ms |

El objetivo de esta spec es repetir la medición con **el código real de M3C-03**, para confirmar que
el ciclo:
- no se traba;
- no pierde memoria;
- deja la latencia de detección dentro de lo que dice ADR-M3-07.

También deja una página para cuando haya un dispositivo físico.

## 2. Página de diagnóstico — `frontend/src/app/diagnostico/clasificador/page.tsx`

- Pública, **sin login**. `export const metadata = { robots: { index: false, follow: false } }`. Fuera
  del sitemap: revisá cómo se arma en `src/app/sitemap.ts`, si existe.
- Usa la **cámara propia** (`getUserMedia({ video: true })`). No hay otro participante.
- Crea `ClasificadorKillswitch` con `hayMenor: true` y observa la pista propia durante 60 s.
- Muestra en pantalla:
  - el backend de TF.js;
  - el tiempo de carga;
  - `estadisticas()` (p50, p95, máx, muestras);
  - `tf.memory().numTensors` al inicio y al final;
  - la cantidad de turnos salteados.
- Un botón "Copiar resultados" lleva el JSON al portapapeles.
- **No sube nada, no guarda frames y no llama a la API.**

## 3. Script de medición — `frontend/scripts/medir-clasificador.mjs`

Usa Playwright y Chromium:
- `executablePath` desde la variable `CHROMIUM_PATH`, si existe;
- `args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"]`.

Pasos:
1. Levanta o reutiliza `npm run dev`, igual que `webServer` en `playwright.config.ts`, y abre
   `/diagnostico/clasificador`.
2. Para cada combinación de backend (`webgl` y `wasm`; la página acepta `?backend=` y fuerza el
   backend de TF.js solo en esta ruta) y CPU (`1`, `4`, `6`, vía CDP):
   - espera los 60 s;
   - lee el JSON de resultados.
3. Imprime una tabla en markdown.

## 4. Commit 2 — anexo con números

1. Corré el script y pegá la tabla en el anexo de `docs/adr/ADR-M3-01.md`, con fecha, máquina (núcleos
   y si tiene GPU) y versiones. Agregalo debajo del anexo existente; no lo reemplaces.
2. Calculá la latencia de detección estimada: `MUESTRAS_PARA_CONFIRMAR × max(INTERVALO_CLASIFICACION_MS, p95)`
   con 1 y con 2 tracks.
3. **Criterio:**
   - si con wasm a 4× la latencia estimada de detección con 1 track es **≤ 3 s**, no se cambia nada;
   - si es mayor, **no cambies la Tabla de Tiempos**: dejalo anotado y señalalo en el resumen como
     decisión del dueño, con la propuesta concreta.
4. Verificá que `numTensors` al final sea igual al inicio, con una diferencia de 0. Si crece, es una
   pérdida de memoria en M3C-03: abrí un arreglo ahí antes de cerrar esta spec.

Mensajes:
- `feat(aula): página de diagnóstico del clasificador (M3C-07)`;
- `docs(adr): latencia del clasificador con el código del aula (M3C-07)`.

## 5. Cuando haya un dispositivo físico (fuera de esta tarea)

Abrir `https://<entorno>/diagnostico/clasificador` en un Android de gama media con Chrome, tocar
"Copiar resultados" y agregar la fila al anexo.

## 6. Verificación

- `npm run lint` y `npm run build` verdes.
- La página no aparece en el sitemap y tiene `noindex`.
- Tildá T-M3-06g.

## 7. NO tocar

- El umbral, el intervalo ni la Tabla de Tiempos: cualquier cambio lo decide el dueño.
- Ninguna medición de "exactitud" con imágenes reales sensibles: está fuera del alcance (BR-KS-04).
