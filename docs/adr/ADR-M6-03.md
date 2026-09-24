# ADR-M6-03 — Proveedor de LLM: Gemini 3.5 Flash-Lite, pipeline de dos llamadas

**Estado:** Aceptado — 2026-09-24 (spec `tesis/T07-proveedor-llm.md`, cierra T-FIN-03)

## Contexto

AUD-024 (`ABIERTO`): M6 no puede generar ningún resumen — `ResumenProveedor` solo tenía la
implementación fail-closed (`ResumenProveedorFailClosed`) y `TranscriptSesionProveedor` solo
`TranscriptSesionProveedorNoDisponible`. El ADR del proveedor está pendiente desde el cierre de
FASE FIN (T-FIN-03, fila "LLM (resumen y transcripción)" del Registro de Decisiones Técnicas de la
Constitución: "GPT-4o vs. Gemini 2.0 Flash — Pendiente — ADR"). Los dos candidatos originales ya no
sirven: **Gemini 2.0 Flash fue dado de baja el 01/06/2026**, y GPT-4o cuesta del orden de cinco
veces más por sesión que el resto de las opciones evaluadas en el Cap. 5 de la tesis.

La tesis (Cap. 5, evaluación económica) adopta **Gemini 3.5 Flash-Lite**: USD 0,30 por millón de
tokens de entrada (el audio cuenta como input a 32 tokens/segundo) y USD 2,50 por millón de tokens
de salida. Para una sesión de 51 minutos (el promedio esperado del piloto), el costo total del
pipeline completo (transcripción + resumen) es **USD 0,033**.

## Alternativas descartadas

| Alternativa | Motivo de descarte |
| --- | --- |
| **Gemini 2.0 Flash** | Dado de baja por Google el 01/06/2026 — ya no es una opción disponible, no solo más cara. |
| **Gemini 3.6 Flash** | USD 0,079 por sesión de 51 min (2,4× el costo de 3.5 Flash-Lite) y su precio se **duplica desde 01/2027** según el anuncio de pricing de Google — insostenible contra el presupuesto de infra de USD 0-100/mes (Artículo VII / AGENTS §1.4). |
| **GPT-4o** | Del orden de cinco veces más caro por sesión que Gemini 3.5 Flash-Lite; sin ninguna ventaja funcional que justifique el costo para este caso de uso (resumen + transcripción de audio corto). |
| **Whisper como transcriptor separado** | Ya descartado en la fila previa del Registro de Decisiones ("Transcripción + resumen de sesión"): se unifica en el LLM elegido, que acepta audio como input directo — Whisper agregaría una dependencia y un proveedor más sin necesidad. |

## Decisión

1. **Proveedor:** Gemini 3.5 Flash-Lite, vía API HTTP directa (`generateContent`), sin SDK nuevo
   (A5) — mismo patrón de cliente que `LiveKitService`/`MercadoPagoClientHttp` (`RestClient` de
   Spring). La API key vive **solo** en la variable de entorno `GEMINI_API_KEY`, nunca en el repo.
2. **Pipeline de dos llamadas** (decisión PT7, 2026-09-23, ya resuelta antes de este ADR):
   audio → transcript (`TranscriptSesionProveedorGemini`, invocado por T08 cuando exista el
   storage del audio); transcript **ya anonimizado** por `AnonimizadorTranscript` (ADR-M6-02) →
   resumen (`ResumenProveedorGemini`). Se descarta la variante de una sola llamada (audio →
   resumen directo) porque con ella la anonimización de FR-SUM-005 no podría correr antes de que
   el audio saliera hacia el proveedor externo — el costo extra de la segunda llamada (texto, no
   audio) es despreciable frente al costo del audio, que domina el total de USD 0,033/sesión.
3. **Activación condicional:** los beans reales (`ResumenProveedorGemini`,
   `TranscriptSesionProveedorGemini`) se activan solo con la property
   `tinku.resumen.proveedor=gemini`. Sin ella, siguen los fail-closed actuales
   (`ResumenProveedorFailClosed`, `TranscriptSesionProveedorNoDisponible`) — ningún test ni entorno
   de desarrollo llama a Google salvo que se configure explícitamente.
4. **Contrato del puerto sin cambios:** `ResumenProveedor.ResumenRequest` conserva los campos
   `audioMimeType`/`audioBase64` aunque `ResumenProveedorGemini` no los use — el audio va al
   transcriptor (paso de la primera llamada), nunca a este segundo paso. Se documenta así en el
   javadoc del puerto para que no se lea como un descuido.

## Riesgo de discontinuación (riesgo R-06 de la tesis)

Gemini 2.0 Flash ya se dio de baja una vez durante este mismo proyecto — el riesgo de que Google
discontinúe o repricee Gemini 3.5 Flash-Lite no es hipotético, es un patrón ya observado. La
mitigación no es contractual (no hay forma de garantizar la continuidad de un proveedor externo):
es arquitectónica. `GeminiCliente` vive detrás de los puertos `ResumenProveedor` y
`TranscriptSesionProveedor` (ya existentes desde T-M6-05); ningún otro módulo conoce el nombre
"Gemini" ni el formato de su API. Cambiar de proveedor el día de mañana implica escribir un
adapter nuevo detrás del mismo puerto y cambiar el valor de `tinku.resumen.proveedor` — no tocar
`ResumenService` ni ningún llamador. Este ADR dispara la migración; el puerto ya la absorbe sin
refactor.

## Implementación

- `backend/src/main/java/com/tinku/resumen/GeminiCliente.java` — cliente HTTP (`RestClient`),
  modelo `gemini-3.5-flash-lite`, header `x-goog-api-key`.
- `backend/src/main/java/com/tinku/resumen/port/ResumenProveedorGemini.java` — segunda llamada
  (transcript anonimizado → resumen), activa con `tinku.resumen.proveedor=gemini`.
- `backend/src/main/java/com/tinku/resumen/port/TranscriptSesionProveedorGemini.java` — primera
  llamada (audio → transcript); devuelve `null` hasta que T08 provea el origen del audio.
- `backend/src/main/resources/application.yml` — `tinku.resumen.proveedor` (default `none`,
  fail-closed) y `tinku.resumen.gemini.api-key` (`${GEMINI_API_KEY:}`).
- Tests contra `MockRestServiceServer` (sin red real, sin API key real):
  `GeminiClienteTest`, `ResumenProveedorGeminiTest`, `BeansProveedorGeminiTest`,
  `ResumenProveedorFailClosedTest` (regresión del fail-closed).

## Consecuencias

- AUD-024 pasa de `ABIERTO` a `EN CURSO`: el proveedor de LLM ya existe y está probado, pero el
  finding no cierra hasta que T08 provea el audio real de la sesión (`TranscriptSesionProveedorGemini`
  sigue devolviendo `null` mientras tanto — Spec M6 §5, caso borde #2: sin contenido útil no se
  genera resumen, nunca se inventa contenido).
- La fila "LLM (resumen y transcripción)" del Registro de Decisiones Técnicas de la Constitución
  pasa de "Pendiente — ADR" a "Decidido" (Gemini 3.5 Flash-Lite).
- `AGENTS.md` §2 deja de listar "proveedor de LLM (GPT-4o vs. Gemini 2.0 Flash)" como ejemplo de
  decisión pendiente de ADR — la decisión ya está tomada acá.

## Registro de Decisiones Técnicas (Constitución)

Actualiza la fila "LLM (resumen y transcripción)": **Decidido — Gemini 3.5 Flash-Lite (ADR-M6-03,
2026-09-24)**.
