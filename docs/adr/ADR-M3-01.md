# ADR-M3-01 — Modelo y framework del clasificador on-device del kill-switch

## Estado

Aceptado (decisiones del Spike del clasificador, Artículo XI de la Constitución; ver `spikes/nsfw-classifier/`). Cerrado el 2026-09-08 tras SPIKE-A (modelo + latencia) y SPIKE-B (buffer de 30s).

## Contexto

M3 (Aula Virtual) necesita detectar contenido inapropiado/ilegal en el video del Tutor en tiempo real para el kill-switch. La Constitución fijó que el clasificador corre **on-device** (en el navegador), no en el backend: el backend nunca recibe video en tiempo real (Plan M3 §3.3). El Artículo XI exige un **spike** antes de comprometer fechas sobre el resto del sistema. T-SPIKE-01/02/03 evaluaron modelos pre-entrenados y el buffer de 30s.

## Spike — hallazgos

- **NSFWJS** (`infinitered/nsfwjs` + `GantMan/nsfw_model`, MobileNetV2, 5 clases: Drawing/Hentai/Neutral/Porn/Sexy): clasificador de imagen, **~5 MB** cuantizado, público, self-host trivial vía TF.js (WebGL/WebGPU/WASM). **Latencia real medida en la máquina de desarrollo: ~12 ms/inferencia promedio** (baseline en backend nativo de Node/Apple Silicon; el navegador de un gama media en WebGL es más lento).
- **NudeNet** (`vladmandic/nudenet`): detección de objetos (boxes de partes íntimas), más granular, pero **~70 MB**, **no publicado en npm** (solo GitHub/HuggingFace), build pesado. Para el MVP del kill-switch (que solo necesita "¿es contenido NSFW?", no localizar la parte del cuerpo) es sobre-dimensionado.
- **Descartado** entrenar un modelo propio (fuera de presupuesto, salvo que el spike demostrara que los pre-entrenados no alcanzan; no fue el caso).

## Decisión

**Clasificador:** NSFWJS (modelo MobileNetV2 5-clases) sobre **TensorFlow.js**, ejecutado on-device en el cliente.

**Framework:** TensorFlow.js, con selección automática de backend (`webgl` como default en móvil moderno, `wasm` como fallback). Modelo auto-hosteado (no depender del CDN público en producción — el CDN por defecto de NSFWJS está roto/sin CORS; se sirve el `model.json` + shard cuantizado desde `spikes/nsfw-classifier/models/`, ignorado por git).

**Diseño de latencia (para gama media):** no se clasifica cada frame del stream — se clasifica con throttling (ej. cada N frames / cada M ms, configurable) sobre un frame tomado del track de video (p. ej. cada ~250–500ms), para que la latencia de inferencia no compita con el render de la videollamada. Estimación del desarrollo para la carga de trabajo objetivo del MVP en gama media: **~42–60 ms por frame clasificado** (rango razonable, aún no es un número cerrado medido en dispositivo físico). Ese rango, sumado al throttling propuesto, deja una ventana de reacción al contenido de <1s, compatible con FR-AULA-003. El valor exacto se ajusta con la corrida de `browser-benchmark/` en el dispositivo real antes de cerrar M3-C (ver Consecuencias).

## Alternativas consideradas y por qué se descartaron

- **NudeNet**: más granular (detección por región) y permite blur por zona; pero peso (~70MB), packaging frágil y complejidad de integración que no aportan al requisito real del MVP. Queda como alternativa si el producto llegara a necesitar localización/blur selectivo en el futuro.
- **NsfwSpy.js**: alternativa JS menor, menos robusta y con menos trayectoria que NSFWJS para el caso de uso.
- **Entrenar modelo propio**: descartado por presupuesto/timeline; los pre-entrenados cumplen.

## Consecuencias

- M3-C (clasificador real + endpoint de killswitch) usa NSFWJS/TF.js on-device; el backend decide la rama (menor/adultos) con datos propios de M1, **nunca confiando en un flag del cliente** (Plan M3 §3.3, T-M3-07).
- El evento que detona el clasificador es local al cliente (una detección confirmada); el contrato con el backend es el endpoint `/api/sesiones/{id}/killswitch` (no se sube video hasta que el clip ya está armado).
- **Verificación no bloqueante, pendiente antes de cerrar M3-C:** corrida de `spikes/nsfw-classifier/browser-benchmark/index.html` **en un dispositivo de gama media** real para fijar el T+X efectivo de reacción y el valor concreto del throttling (el rango objetivo estimado es ~42–60 ms/inferencia). El dato de exactitud sobre un set etiquetado real se completa en M3-C.
- El buffer de 30s se validó en SPIKE-B (riesgo de códec: los chunks concatenados del mismo stream son reproducibles, con la salvedad del keyframe al inicio de la ventana — ver `spikes/mediarecorder-buffer/`).

## Registro de Decisiones Técnicas (constitución)

Se agrega fila: **Capa** = "Clasificador de contenido NSFW on-device (kill-switch)"; **Elección** = "NSFWJS (MobileNetV2 5-clases) sobre TensorFlow.js, model auto-host; NudeNet descartado"; **Estado** = "Decidido (ADR-M3-01)".