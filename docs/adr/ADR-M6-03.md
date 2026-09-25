# ADR-M6-03 — Proveedor de LLM para el resumen: GPT-4o

**Estado:** Aceptado — 2026-09-25. Cierra la fila "LLM (resumen y transcripción) — Pendiente, ADR"
del Registro de Decisiones Técnicas (T-FIN-03) y ajusta la fila "Transcripción + resumen".

## Contexto
M6 genera un resumen de cada sesión (FR-SUM-003). La Constitución dejaba abierta la elección entre
GPT-4o y Gemini 2.0 Flash y proponía una sola llamada con audio directo. El código ya aislaba la
elección tras el puerto `ResumenProveedor`, con un bean fail-closed por defecto.

Dos hechos condicionan la decisión:

1. **FR-SUM-005 exige anonimizar antes de cualquier llamada saliente.** El audio crudo no se
   puede anonimizar: la voz y lo que se dice salen tal cual. Mandar audio directo al LLM viola
   FR-SUM-005, así que el LLM solo puede recibir **texto ya anonimizado**. El "audio directo, una sola
   llamada" de la Constitución no es compatible con esa regla y se descarta.
2. **Hoy no existe transcript** (AUD-024): M3 no graba audio (T08, pendiente de la decisión TS1 y
   del storage). Elegir el LLM no activa M6 por sí solo.

## Decisión
- **Proveedor: OpenAI GPT-4o** (Chat Completions, modelo `gpt-4o`, temperatura 0.3). Lo decide el
  dueño del producto.
- **Qué sale:** solo `PromptResumen.INSTRUCCIONES` (mensaje de sistema) y el transcript
  anonimizado (mensaje de usuario). No salen el id de sesión, la materia, el nivel ni el audio.
- **Activación por config:** `LLM_PROVEEDOR=gpt-4o` + `LLM_API_KEY`. Vacío o cualquier otro valor
  → `ResumenProveedorFailClosed` (un typo nunca manda datos a un proveedor no decidido). Con
  `gpt-4o` pero sin key → también fail-closed, sin llamada de red.
- **Errores** (HTTP, timeout de 90 s, respuesta vacía) → `RuntimeException` → backoff de
  FR-SUM-007, igual que M5.
- **Transcripción: fuera de este ADR.** De dónde sale el texto (y si el audio de una sesión con un
  menor puede salir hacia un tercero para transcribirse) se decide con T08/TS1, no acá.

## Alternativa descartada
**Gemini 2.0 Flash:** más barato (aprox. USD 0,10/0,40 por millón de tokens de entrada/salida,
contra 2,50/10 de GPT-4o; verificar precios vigentes). Se elige GPT-4o por decisión del dueño del
producto. El costo es aceptable para el volumen del piloto: un transcript de 1 hora son ~10-15 mil
tokens, **unos USD 0,03-0,05 por resumen**; 1000 sesiones/mes ≈ USD 30-50, dentro del presupuesto
USD 0-100/mes. Si el volumen crece, cambiar a Gemini es otro bean detrás del mismo puerto (nuevo
ADR).

## Riesgos aceptados
- **Datos de menores hacia un tercero.** Solo sale texto anonimizado; OpenAI no entrena con datos de
  la API por defecto, pero los retiene hasta 30 días para abuso. El control real es el
  anonimizador, y **ADR-M6-02 ya fija que el NER (ADR-M6-01) pasa a ser requisito el día que exista
  transcript real.** Este ADR no cambia eso: T08 no se habilita en producción sin ADR-M6-01.
- **Dependencia de un proveedor pago con key en el entorno:** la key va solo en variables del
  deploy (Coolify), nunca en el repo.

## Implementación
- `resumen/port/ResumenProveedorOpenAi.java` — cliente RestClient.
- `resumen/config/ResumenProveedorConfig.java` — elige el bean por `tinku.resumen.llm.proveedor`.
- `resumen/port/PromptResumen.java` — instrucciones compartidas con lo que se persiste.
- Test: `ResumenProveedorOpenAiTest` (stub HTTP local, no pega contra OpenAI).
