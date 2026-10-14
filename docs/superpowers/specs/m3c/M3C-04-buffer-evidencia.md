# M3C-04 — Buffer rotativo de 30 s y subida del clip de evidencia

**Branch:** `chunk/m3-c` · **Riesgo:** alto (Artículo V) · **Bloqueada por:** M3C-02

## 1. Problema

FR-AULA-004 y BR-KS-01 piden un buffer local rotativo de 30 s (Tabla de Tiempos) que se sube solo si
el kill-switch se dispara. `POST /api/sesiones/{id}/evidencia` existe (multipart, WebM/MP4 por magic
bytes, ≤30 s, 404 si no hay Alerta), pero **ningún cliente lo llama**: no hay MediaRecorder de video en
`frontend/` (`Spec_M3` US-6, "NO IMPLEMENTADO").

## 2. Implementación

1. **`src/lib/killswitch/bufferEvidencia.ts`**, mismo estilo que `grabadorAudioResumen.ts`:
   - `BufferEvidencia.crear(track: MediaStreamTrack)`: un `MediaRecorder` **solo de video** (D6) sobre
     el track remoto observado, `videoBitsPerSecond` según D7, `mimeType` entre los soportados
     (`video/webm;codecs=vp8`, `video/webm`, `video/mp4`).
   - `start(1000)`: partes de 1 s. Se guarda **la primera parte** (trae la cabecera del contenedor) y
     las últimas 30. Las del medio se descartan al llegar la siguiente.
   - Salvedad del spike B (ADR-M3-01): si la ventana no arranca en un keyframe, los primeros cuadros
     pueden verse mal. Se acepta y se documenta; el reproductor del admin salta hasta el primer
     keyframe.
   - `congelar(): Blob` toma una foto del buffer en el momento de la detección y para el recorder.
   - `descartar()` libera todo. Se llama al finalizar la clase, al desconectarse y al pasar a otro track.
2. **Solo en memoria.** Nunca IndexedDB, `localStorage`, Cache API ni disco. Si la pestaña se cierra,
   el buffer se pierde: es lo que pide el Artículo V.
3. **Un buffer por track observado** (cámara y pantalla del otro, D1/D2). Al detectar, se congela el
   del track que disparó.
4. **Subida** (`src/lib/api.ts`, `subirEvidencia(sesionId, blob, duracionSegundos)`):
   - Se llama **después** de que `POST /killswitch` devuelva 200, porque el endpoint de evidencia exige
     la Alerta.
   - Rama adultos: la Alerta recién existe si el otro responde "Sí". El clip congelado se guarda en
     memoria hasta la respuesta o el plazo de D5. Si la respuesta es "No" o vence el plazo, se
     descarta sin subir.
   - Hasta 3 reintentos ante un error de red, sin bloquear la pantalla.
   - Si el blob supera 5 MB (no debería, por D7), se recorta a las partes más recientes antes de subir.
     Nunca se sube más de 30 s.

## 3. Tests (RED primero)

Frontend (Playwright con dispositivos falsos):
1. Después de 45 s de grabación, el blob congelado dura ≤30 s y empieza con cabecera WebM válida (magic
   bytes `1A 45 DF A3`).
2. Al finalizar la clase sin disparo, no se hace ningún POST a `/evidencia`.
3. Rama menor: tras el disparo, un único POST multipart a `/evidencia` con `duracionSegundos ≤ 30`.
4. Rama adultos con "No" → no se sube nada.
5. El blob de 30 s a 1 Mbps queda por debajo de 5 MB.

Backend: ya cubierto por `FASE2-09` (evidencia por archivo). Agregar solo un test si cambia algo del
contrato.

## 4. Criterios de aceptación

- `Spec_M3` US-6: se quita la marca "NO IMPLEMENTADO" del endpoint de evidencia, con referencia al
  commit.
- Revisión explícita contra el Artículo V en el mensaje del commit: qué se graba, dónde vive y cuándo
  se borra.

## 5. NO tocar

- `spring.servlet.multipart.max-file-size` (5 MB): protege las demás subidas (AUD-007).
- La retención del clip en el servidor (30 días, AUD-021) y la extendida de M9 (FR-SEC-013).
