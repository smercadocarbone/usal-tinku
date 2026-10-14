# M3C-04 — Buffer rotativo de 30 s y subida del clip de evidencia

**Tarea:** T-M3-06d · **Riesgo:** alto (Artículo V) · **Depende de:** M3C-03 (solo por la carpeta `src/lib/killswitch/`) · **Commits:** 2 (tests RED, implementación)

## 1. Qué falta hoy

`POST /api/sesiones/{id}/evidencia` existe y valida esto (`EvidenciaService.subir`):
- solo participantes;
- 404 si no hay Alerta;
- 422 si `duracionSegundos` no está en 1–30;
- 422 si el archivo no es WebM o MP4 por magic bytes;
- 422 si la Alerta ya se resolvió;
- límite de 5 MB de `spring.servlet.multipart`.

Ningún cliente lo llama: no hay MediaRecorder de video en `frontend/`.

Valores de la Tabla de Tiempos y de ADR-M3-07 §5:
- buffer de **30 s**;
- **solo video**;
- `videoBitsPerSecond = 1_200_000`;
- tope de **5 MB** (5 × 1024 × 1024 bytes);
- **3 reintentos de subida, 2 s entre intentos**.

## 2. Constantes nuevas en `frontend/src/lib/killswitch/config.ts`

```ts
/** Tabla de Tiempos: "Buffer rotativo de evidencia (kill-switch)". */
export const BUFFER_SEGUNDOS = 30;
/** ADR-M3-07 §5. */
export const CLIP_BITS_POR_SEGUNDO = 1_200_000;
export const CLIP_MAX_BYTES = 5 * 1024 * 1024;
/** Tabla de Tiempos: "Reintentos de subida del clip de evidencia". */
export const REINTENTOS_SUBIDA_CLIP = 3;
export const ESPERA_SUBIDA_CLIP_MS = 2_000;
```

## 3. `frontend/src/lib/killswitch/bufferEvidencia.ts`

Mismo estilo que `src/lib/grabadorAudioResumen.ts`: constructor privado, `static crear()` que devuelve
`null` si el navegador no puede grabar.

```ts
export class BufferEvidencia {
  /** null si no hay MediaRecorder o ningún mimeType de video está soportado. */
  static crear(pista: MediaStreamTrack): BufferEvidencia | null;
  /** Foto del buffer al momento de la detección; para el recorder. null si todavía no hay partes. */
  congelar(): { blob: Blob; duracionSegundos: number } | null;
  /** Libera todo. Idempotente. */
  descartar(): void;
}
```

Reglas:
1. `MediaRecorder(new MediaStream([pista]), { mimeType, videoBitsPerSecond: CLIP_BITS_POR_SEGUNDO })`.
   - `mimeType` es el primero soportado de
     `["video/webm;codecs=vp8", "video/webm;codecs=vp9", "video/webm", "video/mp4"]`.
   - **La pista es solo de video** (ADR-M3-07 §5): nunca agregues pistas de audio al `MediaStream`.
2. `recorder.start(1000)`: una parte por segundo.
3. Se guarda **la primera parte** (`cabecera`, trae el encabezado del contenedor) y las últimas
   `BUFFER_SEGUNDOS - 1` partes. Al llegar una parte nueva y superar ese tope, se descarta la más
   vieja **del medio**, nunca la cabecera. Así el clip dura ≤ 30 s.
4. `congelar()`:
   - `recorder.stop()`, esperando el último `dataavailable`;
   - arma `new Blob([cabecera, ...partes], { type: mimeType })`;
   - `duracionSegundos = min(BUFFER_SEGUNDOS, 1 + partes.length)`;
   - si `blob.size > CLIP_MAX_BYTES`, saca partes del principio (después de la cabecera) hasta que
     entre, y recalcula la duración;
   - después de congelar, el objeto no graba más.
5. Salvedad del spike B (ADR-M3-01): al sacar partes del medio, el clip puede arrancar sin keyframe y
   los primeros cuadros verse mal. Se acepta; documentalo en el javadoc de la clase.
6. **Solo memoria:** ni IndexedDB, ni `localStorage`, ni Cache API, ni `URL.createObjectURL` persistido.
7. `descartar()`: `recorder.stop()` si está activo, vacía los arrays y suelta referencias. **No**
   llama a `pista.stop()`.

## 4. Llamadas a la API en `frontend/src/lib/api.ts`

Funciones nuevas al final del bloque de sesiones o del aula. Tipalas con los DTOs de M3C-02.

```ts
export interface EstadoKillswitch { hayMenor: boolean; confirmacionPendiente: boolean; detectadoId: string | null; venceAt: string | null }
export type EventoClasificador = "NO_CARGA" | "CAIDA" | "REACTIVADO" | "CORTE_SIN_REACTIVAR";
export interface SesionKillswitch { id: string; estado: string }

export function getEstadoKillswitch(sesionId: string): Promise<EstadoKillswitch>;      // GET  /api/sesiones/{id}/killswitch
export function dispararKillswitch(sesionId: string, detectadoId: string): Promise<SesionKillswitch>; // POST /api/sesiones/{id}/killswitch {detectadoId}
export function confirmarKillswitch(sesionId: string, vio: boolean): Promise<SesionKillswitch>;     // POST /api/sesiones/{id}/killswitch/confirmacion {vio}
export function reportarClasificador(sesionId: string, evento: EventoClasificador): Promise<void>;  // POST /api/sesiones/{id}/clasificador {evento}
/** Multipart: parte "clip" + query duracionSegundos. Reintenta REINTENTOS_SUBIDA_CLIP veces. */
export async function subirEvidencia(sesionId: string, clip: Blob, duracionSegundos: number): Promise<void>;
```

- `subirEvidencia` arma un `FormData` con `clip` (nombre de archivo `evidencia.webm` o `.mp4` según
  el tipo) y lo manda a `/api/sesiones/${id}/evidencia?duracionSegundos=${n}` con `api.post`, que ya
  acepta `FormData`.
- Reintenta solo ante un error de red o un 5xx, esperando `ESPERA_SUBIDA_CLIP_MS`. Un 4xx no se
  reintenta: se loguea con `console.error` sin datos del clip.
- **Nunca** manda la rama.

## 5. Tests (commit 1, RED) — `frontend/tests/aula/killswitch-buffer.spec.ts`

Página de prueba: reusá `src/app/dev/killswitch/page.tsx` de M3C-03. Agregá un botón o una función en
`window` que cree un `BufferEvidencia` sobre `canvas.captureStream(15)` animado.

| ID | Caso |
|---|---|
| KS-020 | Después de 45 s de grabación (con `page.clock` si funciona con MediaRecorder; si no, real y con `test.slow()`), `congelar()` devuelve `duracionSegundos ≤ 30` y el blob empieza con `1A 45 DF A3` (EBML/WebM) |
| KS-021 | El blob congelado de 30 s es ≤ 5 MB |
| KS-022 | Tras `congelar()` no llegan más partes (el tamaño no cambia 3 s después) |
| KS-023 | `descartar()` dos veces no tira error |
| KS-024 | `subirEvidencia` manda multipart con la parte `clip` y `duracionSegundos` en la query, sin `rama` (interceptar con `page.route`) |
| KS-025 | `subirEvidencia` ante un 503 reintenta 3 veces y para; ante un 422, no reintenta |
| KS-026 | `BufferEvidencia.crear` con una pista de audio → no la acepta: devuelve `null` o tira error, según elijas, documentado |

Mensaje: `test(aula): RED del buffer de evidencia y la subida del clip (M3C-04)`.

## 6. Implementación (commit 2, GREEN)

Mensaje: `feat(aula): buffer de 30 s solo video en memoria y subida del clip (M3C-04, ADR-M3-07 §5)`.

El cuerpo del commit lleva una revisión contra el Artículo V, en 3 líneas:
- qué se graba;
- dónde vive;
- cuándo se borra.

## 7. Verificación

- `npm run lint`, `npm run build` y `npx playwright test tests/aula` verdes.
- `rg -n "indexedDB|localStorage|caches\\." src/lib/killswitch` vacío.
- Tildá T-M3-06d en las dos listas de tareas.

## 8. NO tocar

- `spring.servlet.multipart.max-file-size` (5 MB, AUD-007).
- `EvidenciaService` y la retención del clip en el servidor (AUD-021, FR-SEC-013).
