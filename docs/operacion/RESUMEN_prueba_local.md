# Resumen con IA: prueba local sin costo

En producción el resumen usa OpenAI (`gpt-4o-mini-transcribe` + GPT-4o, ADR-M6-03), que es pago.
Para probarlo en local sin pagar se puede apuntar el mismo código a **Groq**, que tiene una API
compatible con la de OpenAI (`/v1/audio/transcriptions` y `/v1/chat/completions`) y un plan
gratuito con límites por minuto y por día, sin tarjeta.

Es solo para una demo o una prueba: el resumen lo escribe **gpt-oss-120b** (el modelo abierto de OpenAI), no GPT-4o, y la
transcripción **Whisper large v3** (la versión turbo entiende peor el español: «11, 2» por «once doceavos»). El pipeline, el prompt, el anonimizador y el borrado
del audio son los mismos que en producción. No usarlo con clases reales ni con menores (el audio
de una clase con un Menor no se graba nunca, de todos modos).

## Pasos

1. Crear una clave en <https://console.groq.com/keys> (cuenta gratuita).
2. Agregar al `.env` de la raíz del repo:

   ```
   LLM_PROVEEDOR=gpt-4o
   LLM_API_KEY=gsk_...
   LLM_BASE_URL=https://api.groq.com/openai
   LLM_MODELO=openai/gpt-oss-120b
   LLM_MODELO_TRANSCRIPCION=whisper-large-v3
   ```

   `LLM_PROVEEDOR=gpt-4o` es la llave que habilita el proveedor (fail-closed sin ella); los
   modelos y la URL son los que cambian. Al arrancar, el backend avisa en el log que está usando
   un modelo distinto de GPT-4o.
3. Levantar el stack con LiveKit local (el audio sale del aula):

   ```
   docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build
   cd frontend && npm run dev
   ```

4. Reservar una clase entre dos adultos del seed con el adicional de resumen, aceptar los dos
   consentimientos, dar la clase unos minutos hablando del tema y terminarla. El resumen aparece
   en la sesión cuando termina el job (unos segundos).

## Para volver a OpenAI

Borrar `LLM_BASE_URL`, `LLM_MODELO` y `LLM_MODELO_TRANSCRIPCION` y poner la clave de OpenAI en
`LLM_API_KEY`.
