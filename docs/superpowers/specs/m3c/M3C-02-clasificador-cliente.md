# M3C-02 — Clasificador NSFWJS sobre el video remoto

**Branch:** `chunk/m3-c` · **Riesgo:** alto (Artículo II) · **Bloqueada por:** M3C-01

## 1. Problema

FR-AULA-003 pide un clasificador on-device en tiempo real. Hoy `frontend/` no tiene NSFWJS ni
TensorFlow.js, y el aula (`src/app/aula/[id]/page.tsx`) no mira ningún frame. El modelo ya está en el
repo (`spikes/nsfw-classifier/models/`, `model.json` + un shard de ~2,6 MB).

## 2. Implementación

1. **Dependencias:** `nsfwjs` y `@tensorflow/tfjs` en `frontend/package.json`, con versión fija. Se
   cargan con `import()` dinámico **solo dentro del aula**. El resto del sitio no paga el peso.
2. **Modelo auto-hosteado** (ADR-M3-01: el CDN por defecto de NSFWJS está roto o sin CORS). Copiar
   `model.json` y el shard a `frontend/public/modelos/nsfw/`. Cabecera `Cache-Control` larga e
   inmutable para esa ruta en `next.config.js`. Dejar los archivos del spike donde están.
3. **`src/lib/killswitch/clasificador.ts`**, sin React:
   - `cargarClasificador(): Promise<Clasificador>`: elige backend TF.js (`webgl`, si no `wasm`), carga
     el modelo, hace **una inferencia de calentamiento** sobre un canvas negro y recién ahí resuelve.
     Si algo falla, rechaza con un error tipado (`ClasificadorNoDisponible`), que usa M3C-03.
   - `observar(track: RemoteVideoTrack, duenoId: string)`: muestrea el track cada **intervalo de D3**
     (Tabla de Tiempos) dibujándolo en un canvas fuera de pantalla de 224×224, y clasifica.
   - Regla de confirmación de D4: suma `Porn + Hentai`; si supera el umbral en N muestras
     consecutivas **del mismo track**, emite `deteccion({ duenoId, track })` una sola vez por track.
     Una muestra bajo el umbral reinicia el contador.
   - `latido`: emite cada vez que una clasificación termina. Lo usa M3C-03 para saber si el modelo
     sigue vivo.
   - `detener(track)` y `destruir()`: liberan tensores (`tf.tidy`/`dispose`) y el canvas.
   - Nunca guarda frames: cada frame se dibuja, se clasifica y se pisa.
4. **Qué se observa (D1, D2):** en `page.tsx`, en `RoomEvent.TrackSubscribed`, todo track de video
   remoto, sea `Track.Source.Camera` o `Track.Source.ScreenShare`. `duenoId` es el id de usuario de
   Tinku del participante remoto: confirmar cómo viaja hoy en la identidad o metadata del token de
   LiveKit. Si no viaja, agregarlo en `LiveKitService` y documentarlo. En `TrackUnsubscribed` se
   deja de observar.
5. **Solo con la sesión en curso:** se empieza a clasificar al conectar a la sala y se para en
   `Disconnected` o al finalizar.
6. **Rendimiento:** si una clasificación tarda más que el intervalo, la siguiente se saltea (nunca se
   encolan). Se mide el tiempo de cada inferencia y se expone para M3C-07.

## 3. Tests (RED primero)

Hoy el frontend solo tiene Playwright, sin tests unitarios. Elegir uno de los dos caminos y
documentarlo en el commit:

- **Opción A (recomendada):** probar `clasificador.ts` desde Playwright con
  `page.evaluate` y un modelo falso inyectado (una interfaz `Modelo` que el test reemplaza).
- **Opción B:** agregar `vitest` solo para `src/lib/killswitch/`. Suma una dependencia de desarrollo;
  justificarla contra 1 desarrollador.

Casos:
1. N−1 muestras sobre el umbral y una debajo → no emite.
2. N consecutivas sobre el umbral → emite una vez con el `duenoId` correcto.
3. Dos tracks del mismo dueño (cámara y pantalla) → el contador es por track.
4. `cargarClasificador` con el modelo inaccesible (404) → rechaza con `ClasificadorNoDisponible`.
5. El modelo real carga desde `/modelos/nsfw/model.json` en Chromium headless y clasifica un frame
   neutro (sin contenido sensible) por debajo del umbral.

## 4. Criterios de aceptación

- Ninguna página fuera de `/aula/[id]` descarga TF.js ni el modelo (verificarlo en la pestaña de red).
- Con dispositivos falsos (`--use-fake-device-for-media-stream`), el aula clasifica en cada intervalo
  sin errores de consola ni pérdida de tensores (`tf.memory().numTensors` estable durante 2 minutos).
- `npm run build` y `npm run lint` verdes.

## 5. NO tocar

- El backend del kill-switch.
- La pizarra (sigue apagada con menor).
- El video local: ver D1 en `00-LEEME-m3c.md`.
