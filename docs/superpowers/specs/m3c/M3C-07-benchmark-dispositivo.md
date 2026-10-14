# M3C-07 — Latencia real del clasificador en un celular de gama media

**Branch:** `chunk/m3-c` · **Riesgo:** bajo · **Bloqueada por:** M3C-02 · **Lo corre:** el dueño del producto, con un dispositivo físico

## 1. Problema

ADR-M3-01 dejó una verificación pendiente antes de cerrar M3-C: correr el benchmark en un dispositivo
de gama media real para fijar el tiempo de reacción y el intervalo de clasificación. Las ~42–60 ms por
inferencia son una estimación.

ADR-M3-01 cita `spikes/nsfw-classifier/browser-benchmark/index.html`, pero **ese archivo no está en el
repo**: solo están los pesos del modelo (`spikes/nsfw-classifier/models/`). Hay que rehacerlo.

## 2. Implementación

### Agente

1. Página de diagnóstico **dentro del frontend** (así usa el mismo modelo, el mismo backend de TF.js y
   el mismo código que el aula): `src/app/diagnostico/clasificador/page.tsx`.
   - No pide login ni cámara de otro participante: usa la cámara propia del dispositivo.
   - Informa: backend de TF.js elegido (`webgl`/`wasm`), tiempo de carga del modelo, latencia por
     inferencia (p50, p95 y máximo sobre 200 inferencias), memoria (`tf.memory()`), y si el video del
     propio preview se traba mientras clasifica.
   - No sube nada a ningún lado ni guarda frames. Los resultados se ven en pantalla y se copian con un
     botón.
   - Se excluye del sitemap y lleva `noindex`.
2. Dejar en `spikes/nsfw-classifier/README.md` dónde está ahora el benchmark.

### Vos

3. Abrir `https://<entorno>/diagnostico/clasificador` en:
   - un Android de gama media (≈ USD 150–250, de los últimos 3 años), en Chrome;
   - si podés, un iPhone viejo en Safari.
4. Pasarme los números copiados.

### Agente, con los números

5. Anexo "Verificación en dispositivo" en ADR-M3-01, con fecha y modelo de los equipos.
6. Si el p95 supera el intervalo de D3, proponer otro intervalo: es un cambio de la Tabla de Tiempos y
   necesita tu OK.

## 3. Exactitud sobre un set etiquetado (opcional)

ADR-M3-01 también nombra "el dato de exactitud sobre un set etiquetado real". BR-KS-04 deja fuera la
calibración en el MVP, así que **no bloquea** el cierre de T-M3-06.

Si se hace:
- lo hacés vos, fuera del repo, solo con un dataset público de contenido adulto con licencia;
- jamás material que involucre a menores;
- al repo entra solo la tabla de resultados.

El agente no descarga ni procesa ese tipo de contenido.

## 4. Criterios de aceptación

- Página de diagnóstico mergeada y funcionando en el entorno de pruebas.
- Anexo de ADR-M3-01 con al menos un dispositivo de gama media medido.

## 5. NO tocar

- Los pesos del modelo.
