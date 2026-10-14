# M3C-03 — Clasificador NSFWJS en el navegador (módulos sin React)

**Tarea:** T-M3-06c · **Riesgo:** alto (Artículo II) · **Depende de:** nada (se integra al aula en M3C-05) · **Commits:** 3 (dependencias y modelo; tests RED; implementación)

## 1. Qué falta hoy

`frontend/` no tiene NSFWJS ni TensorFlow.js. Los pesos del modelo están en
`spikes/nsfw-classifier/models/`:
- `model.json`: Keras layers, MobileNetV2, entrada 224×224×3;
- `group1-shard1of1`: ~2,6 MB.

Esta spec arma la biblioteca del clasificador. **No toca `aula/[id]/page.tsx`**: eso va en M3C-05.

Ya se probó, el 2026-10-14 en Chromium headless (anexo de ADR-M3-01), que esta combinación carga el
modelo y clasifica:
- `@tensorflow/tfjs@4.22.0`;
- `@tensorflow/tfjs-backend-wasm@4.22.0`;
- `nsfwjs@4.3.0`;
- llamada `nsfwjs.load("/modelos/nsfw/model.json", { size: 224 })`.

## 2. Commit 1 — dependencias y modelo

1. En `frontend/`:
   `npm install --save-exact @tensorflow/tfjs@4.22.0 @tensorflow/tfjs-backend-wasm@4.22.0 nsfwjs@4.3.0`.
   - El repo tiene `bun.lock`: fijate cuál de los lockfiles es el que se usa (Dockerfile y CI) y
     actualizá ese. Si hay dos, actualizá los dos.
2. Copiá los pesos a `frontend/public/modelos/nsfw/`:
   `cp spikes/nsfw-classifier/models/model.json spikes/nsfw-classifier/models/group1-shard1of1 frontend/public/modelos/nsfw/`.
   Dejá los originales.
3. Copiá los `.wasm` de TF.js a `frontend/public/modelos/tfjs-wasm/`:
   - `tfjs-backend-wasm.wasm`;
   - `tfjs-backend-wasm-simd.wasm`;
   - `tfjs-backend-wasm-threaded-simd.wasm`.
   Salen de `node_modules/@tensorflow/tfjs-backend-wasm/dist/`. Agregá un comentario en
   `frontend/public/modelos/LEEME.md`: de dónde salen, qué versión, y que se vuelven a copiar si se
   actualiza el paquete.
4. En `next.config.js`, una entrada más en `headers()` para `source: "/modelos/:path*"` con
   `Cache-Control: public, max-age=31536000, immutable`.
5. Mensaje: `chore(aula): dependencias de TF.js y NSFWJS y modelo auto-hosteado (M3C-03, ADR-M3-01)`.

## 3. Módulos a crear en `frontend/src/lib/killswitch/`

### 3.1 `config.ts` — constantes, todas citando la Tabla de Tiempos o el ADR

```ts
/** Tabla de Tiempos: "Intervalo de clasificación del kill-switch". */
export const INTERVALO_CLASIFICACION_MS = 250;
/** Tabla de Tiempos: "Confirmación de una detección". */
export const MUESTRAS_PARA_CONFIRMAR = 3;
/** Tabla de Tiempos: "Tiempo máximo de carga del clasificador". */
export const CARGA_MAXIMA_MS = 20_000;
/** Tabla de Tiempos: "Tiempo sin respuesta del clasificador". */
export const SIN_RESPUESTA_MS = 3_000;
/** Tabla de Tiempos: "Reintentos para reactivar el clasificador". */
export const REINTENTOS_REACTIVAR = 3;
export const ESPERA_ENTRE_REINTENTOS_MS = 3_000;
/** ADR-M3-07 §4. */
export const UMBRAL_DETECCION = 0.85;
export const TAMANO_ENTRADA = 224;
export const URL_MODELO = "/modelos/nsfw/model.json";
export const URL_WASM = "/modelos/tfjs-wasm/";
```

### 3.2 `regla.ts` — lógica pura, sin TF.js ni DOM

```ts
export type ClaseNsfw = "Drawing" | "Hentai" | "Neutral" | "Porn" | "Sexy";
export interface Prediccion { className: ClaseNsfw; probability: number }

/** ADR-M3-07 §4: con un menor cuentan Porn+Hentai+Sexy; entre adultos, Porn+Hentai. */
export function puntaje(predicciones: Prediccion[], hayMenor: boolean): number;

/** Cuenta muestras consecutivas sobre el umbral, por track. */
export class ContadorDetecciones {
  /** Devuelve true SOLO la vez que el track llega a MUESTRAS_PARA_CONFIRMAR. */
  registrar(trackId: string, puntaje: number): boolean;
  /** Reinicia ese track (después de un "No" en la rama adultos, o al reactivar el clasificador). */
  reiniciar(trackId: string): void;
  quitar(trackId: string): void;
  reiniciarTodo(): void;
}
```

Reglas de `registrar`:
- un puntaje `>= UMBRAL_DETECCION` suma 1;
- uno menor deja el contador en 0;
- al llegar a `MUESTRAS_PARA_CONFIRMAR` devuelve `true` una vez, y no vuelve a devolver `true` para
  ese track hasta un `reiniciar(trackId)`.

### 3.3 `modelo.ts` — carga del modelo real (único archivo que importa TF.js)

```ts
export interface Modelo {
  clasificar(fuente: HTMLCanvasElement): Promise<Prediccion[]>;
  liberar(): void;
}
export class ClasificadorNoDisponible extends Error {}

/** Carga TF.js y NSFWJS con import() dinámico, hace una inferencia de calentamiento y resuelve. */
export async function cargarModelo(opciones?: { backend?: "webgl" | "wasm" }): Promise<Modelo>;
// `backend` fuerza el backend de TF.js: solo lo usa la página de diagnóstico (M3C-07). El aula no lo pasa.
```

Implementación:
1. **Modelo falso para tests**, antes de nada:
   ```ts
   if (process.env.NODE_ENV !== "production" && typeof window !== "undefined"
       && (window as any).__tinkuModeloFalso) return (window as any).__tinkuModeloFalso as Modelo;
   ```
   - En el build de producción, Next reemplaza `process.env.NODE_ENV` y elimina este bloque. Verificalo
     con `rg __tinkuModeloFalso .next/static` después de `npm run build`: **tiene que dar vacío**.
   - Playwright corre contra `npm run dev`, donde el bloque existe.
2. Imports dinámicos: `const tf = await import("@tensorflow/tfjs")`,
   `await import("@tensorflow/tfjs-backend-wasm")` y `const nsfwjs = await import("nsfwjs")`.
3. Backend (si `opciones.backend` viene, se usa solo ese, sin fallback):
   - intentá `await tf.setBackend("webgl")`;
   - si devuelve `false` o tira error, `setWasmPaths(URL_WASM)` y `await tf.setBackend("wasm")`;
   - después, `await tf.ready()`.
4. `const m = await nsfwjs.load(URL_MODELO, { size: TAMANO_ENTRADA })`.
5. Calentamiento: un canvas negro de 224×224, `await m.classify(canvas)`.
6. Todo el proceso corre bajo un `Promise.race` con `CARGA_MAXIMA_MS`. Si vence o algo tira error,
   rechaza con `new ClasificadorNoDisponible(motivo)`.
7. `liberar()` llama a `m.model.dispose()`, o al equivalente de la versión; verificalo en
   `node_modules/nsfwjs/dist/esm/core.d.ts`.

### 3.4 `clasificador.ts` — ciclo de clasificación, detección, caída y reactivación

```ts
export type EventoClasificador = "NO_CARGA" | "CAIDA" | "REACTIVADO" | "CORTE_SIN_REACTIVAR";

export interface OpcionesClasificador {
  hayMenor: boolean;
  /** Detección confirmada. duenoId = participant.identity (UUID del usuario en Tinku). */
  alDetectar: (d: { trackId: string; duenoId: string }) => void;
  /** El clasificador dejó de responder: si hay menor, la UI oculta el video remoto ya. */
  alCaer: () => void;
  /** Revivió: la UI vuelve a mostrar el video. */
  alReactivar: () => void;
  /** Se agotaron los reintentos: con menor la UI sale de la sala; entre adultos solo registra. */
  alFallarDefinitivo: () => void;
  /** Inyectable para tests; por defecto, cargarModelo. */
  cargar?: () => Promise<Modelo>;
}

export class ClasificadorKillswitch {
  /** Carga el modelo. Rechaza con ClasificadorNoDisponible si no carga. */
  static crear(op: OpcionesClasificador): Promise<ClasificadorKillswitch>;
  observar(trackId: string, duenoId: string, pista: MediaStreamTrack): void;
  dejarDeObservar(trackId: string): void;
  /** Después de un "No" en la rama adultos: vuelve a mirar ese track desde cero. */
  reanudar(trackId: string): void;
  /** Para de mirar un track con una detección en curso (no lo clasifica de nuevo hasta reanudar). */
  pausar(trackId: string): void;
  /** Cambia el umbral si el token trae un hayMenor distinto (M3C-05 §4.2.4). Reinicia los contadores. */
  actualizarHayMenor(hayMenor: boolean): void;
  destruir(): void;
  /** Para M3C-07: latencias de las últimas 200 inferencias. */
  estadisticas(): { muestras: number; p50: number; p95: number; max: number; backend: string };
}
```

Comportamiento exacto:

1. **Un `<video>` oculto por track observado:**
   - `muted`, `playsInline`, `srcObject = new MediaStream([pista])`, `play()`;
   - sin agregarlo al DOM visible: `display:none` o fuera del documento si el navegador lo reproduce
     igual (verificar en Chromium y Safari; si Safari exige que esté en el DOM, usá un contenedor
     `position:fixed; width:1px; height:1px; opacity:0; pointer-events:none`).
   - Un solo `<canvas>` de 224×224 compartido.
2. **Ciclo:**
   - un `setTimeout` encadenado (no `setInterval`) cada `INTERVALO_CLASIFICACION_MS`;
   - en cada turno toma el **siguiente** track no pausado, rotando;
   - si `video.readyState < 2` (sin frame todavía, o cámara remota apagada), saltea el turno sin
     contarlo como falla;
   - si hay una inferencia en curso, saltea el turno.
3. **Muestra:**
   - `ctx.drawImage(video, 0, 0, 224, 224)`, luego `modelo.clasificar(canvas)`, luego
     `puntaje(pred, hayMenor)`, luego `contador.registrar(trackId, p)`;
   - si da `true`: `pausar(trackId)` y `alDetectar({ trackId, duenoId })`.
   - El frame nunca se guarda: el canvas se pisa en la muestra siguiente.
4. **Medición:** guarda la duración de cada inferencia en un buffer circular de 200 para
   `estadisticas()`.
5. **Caída**, ADR-M3-07 §6. El clasificador se considera caído si:
   - `modelo.clasificar` rechaza o tira error; o
   - una inferencia lleva más de `SIN_RESPUESTA_MS` sin terminar. Se controla con un `setTimeout`
     armado al empezar cada inferencia y desarmado al terminar.
6. **Reactivación**, al detectar la caída:
   1. `alCaer()`.
   2. Para el ciclo y hace `modelo.liberar()` dentro de un `try`.
   3. Hasta `REINTENTOS_REACTIVAR` veces, esperando `ESPERA_ENTRE_REINTENTOS_MS` antes de cada
      intento: `modelo = await cargar()`.
   4. Al primer éxito: `contador.reiniciarTodo()`, retoma el ciclo y llama a `alReactivar()`.
   5. Si fallan todos: `alFallarDefinitivo()` y queda detenido; `destruir()` lo llama la UI.
   - Mientras reactiva, una segunda caída no inicia otra reactivación (bandera `reactivando`).
7. `destruir()`:
   - corta timers;
   - hace `video.srcObject = null` en cada `<video>` oculto y los saca del DOM;
   - libera el modelo;
   - **no** para las pistas (`pista.stop()`), porque son de LiveKit.

## 4. Tests (commit 2, RED) — `frontend/tests/aula/killswitch-clasificador.spec.ts`

Seguí el patrón de `tests/aula/pizarra.spec.ts`: tests de lógica pura importando de `../../src/lib/...`
(corren en Node) y tests de navegador con `page`. Tags `@aula` y `@KS-0xx`.

**Lógica pura (Node):**

| ID | Caso |
|---|---|
| KS-001 | `puntaje` con menor suma Porn+Hentai+Sexy |
| KS-002 | `puntaje` sin menor ignora Sexy (`{Sexy:0.9}` sin menor da 0; con menor, 0,9) |
| KS-003 | `registrar`: 2 sobre el umbral, 1 debajo, 2 sobre → nunca `true` |
| KS-004 | 3 consecutivas → `true` una sola vez; la 4ª devuelve `false` |
| KS-005 | El contador es por track: alternar A y B sobre el umbral → cada uno confirma en su 3ª muestra |
| KS-006 | `reiniciar(A)` permite volver a confirmar A |
| KS-007 | Umbral exacto 0,85 cuenta; 0,8499 no |

**Navegador** (página de prueba `src/app/dev/killswitch/page.tsx`, protegida igual que
`/dev/componentes`: si esa ruta solo existe en desarrollo, hacé lo mismo):
- La página crea un `MediaStreamTrack` con `canvas.captureStream()`, monta `ClasificadorKillswitch`
  con un `cargar` falso controlable desde `window`, y expone en `window.__ks` los eventos recibidos.

| ID | Caso |
|---|---|
| KS-010 | Modelo falso que devuelve `Porn:0.99` → `alDetectar` una vez con el `duenoId` correcto y el track pausado |
| KS-011 | Modelo falso que rechaza en la 1ª carga → `crear()` rechaza con `ClasificadorNoDisponible` |
| KS-012 | Modelo que tira error en una inferencia y carga bien en el 2º intento → `alCaer` y después `alReactivar`, sin `alFallarDefinitivo` |
| KS-013 | Modelo que tira error y no vuelve a cargar → `alCaer`, 3 intentos con ≈3 s entre cada uno, `alFallarDefinitivo` (usá `page.clock` de Playwright para no esperar 10 s reales) |
| KS-014 | Modelo cuya inferencia nunca resuelve → caída a los 3 s |
| KS-015 | **Modelo real** (sin `__tinkuModeloFalso`): carga desde `/modelos/nsfw/model.json` y clasifica un canvas de color liso con puntaje sin menor < 0,85. Timeout del test 60 s |

Mensaje: `test(aula): RED del clasificador del kill-switch (M3C-03)`.

## 5. Implementación (commit 3, GREEN)

Mensaje: `feat(aula): clasificador NSFWJS sobre el video remoto con reactivación (M3C-03, ADR-M3-07)`.

## 6. Verificación

- `npm run lint`, `npm run build` y `npx playwright test tests/aula` verdes.
- Después del build, `rg -l "nsfwjs|tfjs" .next/static/chunks`: solo en chunks que carga `/aula/[id]` o
  `/dev/*`. Abrí `/` y `/buscar` con la pestaña de red: no se descarga TF.js ni el modelo.
- `rg __tinkuModeloFalso .next/static` vacío.
- Tildá T-M3-06c en las dos listas de tareas.

## 7. NO tocar

- `aula/[id]/page.tsx` (M3C-05).
- Los archivos de `spikes/`.
