# T07 — Proveedor de LLM: Gemini 3.5 Flash-Lite (DT4, cierra T-FIN-03)

**Branch:** `tesis/proveedor-llm` · **Riesgo:** medio (datos personales) · **Bloqueada por:** — (PT7 ya resuelta)

## 1. Problema (verificado al 2026-09-23)

M6 no genera resúmenes (AUD-024): `ResumenProveedor` solo tiene la implementación
`ResumenProveedorFailClosed` y `TranscriptSesionProveedor` solo `TranscriptSesionProveedorNoDisponible`.
El ADR del proveedor está pendiente desde el inicio (T-FIN-03), y los dos candidatos que nombra la
Constitución ya no sirven: **Gemini 2.0 Flash fue dado de baja el 01/06/2026** y GPT-4o cuesta
unas cinco veces más por sesión. La tesis (Cap. 5) adopta **Gemini 3.5 Flash-Lite**: USD 0,30 por
millón de tokens de entrada (audio incluido, 32 tokens/segundo) y USD 2,50 por millón de salida.

## 2. Decisión resuelta (PT7 — 2026-09-23): **dos llamadas**

¿Una llamada (audio → resumen) o dos (audio → transcript; transcript anonimizado → resumen)?
**Recomendación: dos.** `ResumenService` ya anonimiza el transcript antes de armar el prompt;
con una sola llamada esa anonimización no puede ocurrir antes de que el audio salga. El costo de
la segunda llamada (texto) es despreciable frente al del audio.

## 3. Implementación (con la recomendación)

1. **ADR-M6-03** — Proveedor de LLM: Gemini 3.5 Flash-Lite, pipeline de dos llamadas, costo por
   sesión (USD 0,033 para 51 min), alternativas descartadas (Gemini 2.0 Flash dado de baja;
   Gemini 3.6 Flash, USD 0,079 y precio duplicado desde 01/2027; GPT-4o) y riesgo de
   discontinuación (riesgo R-06 de la tesis: el puerto permite cambiar de proveedor).
2. Actualizar la fila "LLM" del Registro de Decisiones de la Constitución y la lista de
   `AGENTS.md` §2 (hoy dice "GPT-4o vs. Gemini 2.0 Flash").
3. `GeminiCliente` (paquete `resumen`) con **`RestClient`**, como `LiveKitService` — **sin SDK
   nuevo** (A5). API key por variable de entorno `GEMINI_API_KEY`; nunca en el repo.
4. `TranscriptSesionProveedorGemini implements TranscriptSesionProveedor`: recibe el audio
   (lo provee T08) y devuelve el transcript.
5. `ResumenProveedorGemini implements ResumenProveedor`: recibe **solo**
   `transcriptAnonimizado` (el campo `audioBase64` del record queda sin usar; documentarlo).
6. Los beans reales se activan con una property `tinku.resumen.proveedor=gemini`; sin ella siguen
   los fail-closed actuales (tests y dev no llaman a Google).
7. Reintentos: el backoff de FR-SUM-007 ya existe en `ResumenService`; no duplicarlo.

## 4. Tests (RED primero)

1. `GeminiCliente` contra un servidor HTTP simulado (`MockRestServiceServer`): request bien
   formado, modelo `gemini-3.5-flash-lite`, API key en el header.
2. `ResumenProveedorGemini_envíaSoloTranscriptAnonimizado` (el request nunca contiene el crudo).
3. `sinPropertyGemini_siguenLosFailClosed` (regresión).
4. Error 5xx del proveedor → `RuntimeException` → el backoff existente (sin tests nuevos de retry).

## 5. Criterios de aceptación

- Suite verde. AUD-024 → `CERRADO` **solo** cuando T08 provea el audio (si no, queda `EN CURSO`).
- ADR-M6-03 + Constitución + AGENTS.md en el mismo commit.

## 6. NO tocar

- `AnonimizadorTranscript` (ADR-M6-02): se usa tal cual.
