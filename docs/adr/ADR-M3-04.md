# ADR-M3-04 — Grabación de solo audio para el resumen, en el navegador del Tutor

**Estado:** Aceptado — 2026-09-25. Acompaña la enmienda v2.4 del Artículo V de la Constitución.
Implementa T08 (DT5) y habilita T09 (DT3). Cierra AUD-024 (M6 sin fuente de transcript).

## Contexto
El resumen automático pasa a ser un adicional pago (T09). Para resumir hace falta el contenido de la
clase, y hasta ahora nada lo producía: `TranscriptSesionProveedorNoDisponible` devolvía siempre `null`
(AUD-024). La spec T08 proponía grabar con **LiveKit Egress** y guardar el audio en **Garage**
(S3 autoalojado), con el AWS SDK para leerlo (TS1).

Al implementar se verificó el costo de Egress en LiveKit Cloud: el plan gratuito (Build) incluye
**60 minutos de transcoding por mes** (menos de una clase) y cobra el excedente (audio: USD 0,005/min),
además de limitar a 2 egress simultáneos. El dueño del producto pidió no sumar gastos de
infraestructura. Garage sumaba además un servicio más que operar en el VPS.

## Decisión
1. **Graba el navegador del Tutor**, no el servidor. `MediaRecorder` sobre la mezcla del micrófono
   del Tutor y el audio remoto (Web Audio), **solo audio**, Opus a 16 kbps. Al terminar la clase el
   navegador sube el archivo a `POST /api/sesiones/{id}/audio`.
   - 16 kbps alcanza para voz y deja la clase más larga (180 min) en ~21,6 MB, bajo el límite de
     25 MB por archivo de la API de transcripción.
   - Se graba en el Tutor porque siempre es adulto y es quien cierra la clase.
2. **Se guarda en el almacenamiento que ya existe** (`identidad/port/Almacenamiento`, el mismo de
   las credenciales, el CAP y la evidencia del kill-switch). No hace falta Garage ni el SDK de S3:
   **TS1 queda sin objeto**.
3. **Cuándo se graba (las cuatro condiciones del Artículo V), verificado por el backend:**
   - El token de la sala le dice al navegador del Tutor si grabar (`grabarAudioResumen`), y solo
     vale `true` si la Reserva tiene el adicional, el beneficiario **no** es Menor, los dos aceptaron
     la cláusula vigente y el adicional está habilitado.
   - El endpoint de subida **vuelve a controlar lo mismo** (no confía en el cliente): solo el Tutor
     de esa sesión, solo con el adicional, nunca con un Menor, un solo audio por sesión, ≤ 25 MB.
4. **Transcripción:** OpenAI `gpt-4o-mini-transcribe` (misma cuenta que el resumen, ADR-M6-03),
   activa con `LLM_PROVEEDOR=gpt-4o`. Se elige el modelo mini por costo: ~USD 0,003/min, unos
   USD 0,18 por hora de clase, dentro del precio del adicional ($770 ≈ USD 0,50).
5. **Borrado (PT6):** el audio se borra apenas se obtiene el transcript. Un job de Quartz persistido
   por sesión lo borra igual a las **24 hs** del fin de la sesión si sigue ahí, y si el resumen nunca
   se generó lo marca `fallido` (→ reembolso del adicional, T09).
6. **Consentimiento (PT5):** tabla `identidad.aceptaciones_clausula` (quién aceptó qué versión y
   cuándo). La versión vigente sale de configuración. El texto lo redacta la asesoría legal: hasta
   entonces el adicional queda **deshabilitado** por defecto (`TINKU_RESUMEN_ADICIONAL_HABILITADO`).

## Alternativas descartadas
- **LiveKit Egress + Garage (spec T08 original):** costo de Egress por encima de 60 min/mes y un
  servicio más en el VPS. Queda como camino si el volumen justifica pagar: el endpoint de subida y el
  resto del pipeline no cambian, solo quién produce el archivo.
- **Grabar en los dos navegadores:** duplica datos personales sin ganar nada (Artículo V).

## Riesgos aceptados
- **Si el navegador del Tutor se cierra antes de subir el audio**, no hay resumen: a las 24 hs la
  sesión queda `fallida` y se reembolsa el adicional (T09). Es el mismo resultado que un fallo de
  Egress en el diseño original.
- **El audio lo produce el cliente:** un Tutor podría subir otro audio. El perjudicado sería su
  propio alumno (un resumen incorrecto); no expone datos de terceros. Aceptado para el piloto.
- **El audio sale hacia OpenAI** para transcribirse. Solo ocurre entre adultos que aceptaron la
  cláusula; OpenAI no entrena con datos de la API por defecto y los retiene hasta 30 días.

## Efecto sobre ADR-M6-02 (anonimizador)
ADR-M6-02 declaraba que el NER (ADR-M6-01) pasaba a ser requisito cuando existiera transcript real,
porque el riesgo era filtrar datos de un **Menor**. Con esta decisión **nunca hay transcript de una
sesión con Menor**, y el proveedor que transcribe ya recibió el audio completo. El NER sigue siendo
una mejora deseable, pero **deja de ser bloqueante**. El anonimizador actual sigue corriendo antes
del resumen, sin cambios.
