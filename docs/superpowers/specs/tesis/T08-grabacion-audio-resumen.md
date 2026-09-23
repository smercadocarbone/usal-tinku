# T08 — Grabación de solo audio para el resumen (DT5)

**Branch:** `tesis/grabacion-audio` · **Riesgo:** **ALTO** (privacidad: graba conversaciones) ·
**Bloqueada por:** T07 mergeada, **FASE2-05** mergeada (las dos tocan el controller del webhook de
LiveKit) y la decisión técnica **TS1** (§3). PT5, PT6 y PT9 están resueltas.

> **Cómo ejecutar esta spec.** Es una **receta**: pasos **en orden**, **un commit por paso**, con su
> **punto de control**. Si un punto de control falla, **PARAR**. Protocolo y guardrails:
> `docs/superpowers/specs/remediacion/00-LEEME-opencode.md` §2 y §3. Nombres verificados contra el
> código al 2026-09-23. Suite (desde `backend/`):
> `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test 2>&1 | grep -E "Tests run:.*Failures|BUILD"`

## 1. Qué se construye y qué NO

**Sí:** grabar **solo audio**, **solo** si la reserva contrató el adicional de resumen (T09), **solo
entre adultos**, con el **consentimiento** de los dos, y **borrarlo** apenas se obtiene el transcript
(máximo 24 hs aunque falle, PT6).
**No, nunca:** video; sesiones con un menor (aunque el adicional figure contratado); guardar el audio
después del transcript; mandar el audio a otro lado que no sea el proveedor de T07.

Decisiones resueltas (2026-09-23):
- **PT6:** borrar al generar el transcript, **máximo 24 hs**. Fila nueva en la Tabla de Tiempos (A3).
- **PT9:** **Garage** autoalojado en el VPS (S3-compatible). **No MinIO** (edición Community archivada en
  02/2026, sin parches).
- **PT5:** el consentimiento es parte de los Términos: el alumno lo acepta **explícitamente** al contratar
  el adicional y el tutor en el onboarding. **No existe** el caso "adicional contratado sin consentimiento".

## 2. ⚠️ Trampas verificadas (leé las seis antes de empezar)

**TG1 — El permiso de grabar NO puede llegar al token del participante.** `aula/LiveKitService.VideoClaim`
es hoy `record VideoClaim(String room, boolean roomJoin, boolean roomCreate, boolean roomAdmin)` y se usa
en el token de participante (`new VideoClaim(nombreSala, true, false, false)`) y en el de servidor
(`new VideoClaim("", false, true, false)`). Egress necesita el grant **`roomRecord`**. Agregá el campo
`boolean roomRecord` al record y pasá **`false` en el token de participante** (AUD-002 existe
justamente por haberle dado de más al participante). `LiveKitServiceTest.tokenDeParticipanteSeVerificaYDeclaraLaSalaCorrecta`
tiene que seguir verde **y** sumá la aserción `video.get("roomRecord") == false`.

**TG2 — Carrera: el resumen se generaría ANTES de que llegue el audio.** Hoy
`resumen/service/ResumenService.onSesionFinalizada` crea la fila y agenda la generación
(`programarReintento(sesionId, now + DEMORA_PRIMERA_GENERACION)`). El audio llega **después**, cuando
LiveKit termina de subirlo y manda el webhook `egress_ended`. Si se genera primero,
`transcriptProveedor.transcript(sesionId)` no tiene audio → el resumen queda `FALLIDO` → T09 **reembolsa
el adicional**. Pasaría **siempre**. Solución (Paso 6): para sesiones con egress, `onSesionFinalizada`
**no** agenda nada; la generación la dispara `egress_ended`.

**TG3 — Leer y borrar en Garage exige firmar requests S3 (SigV4).** A5 prohíbe dependencias sin ADR, y
**implementar SigV4 a mano es la fuente de bugs más probable de toda la tarea**. Es la decisión **TS1**
(§3): **PARAR** hasta que esté resuelta.

**TG4 — El audio puede superar el límite de envío inline de Gemini.** Una clase de 180 min en Opus a
~32 kbps pesa ~40 MB. La API de Gemini acepta ~20 MB de datos inline por request (verificá el número en
la documentación vigente de la API: puede cambiar). Por encima, hay que usar la **Files API** de Gemini
(subir el archivo y referenciarlo). Esto va en `TranscriptSesionProveedorGemini` (T07): si T07 solo
implementó inline, extendelo acá con la Files API y un test que la ejercite con un archivo grande simulado.

**TG5 — `resumen_contratado` lo define T09, pero T08 lo necesita primero.** El orden del LEEME pone T08
antes que T09, y T09 dice que crea `resumen_contratado` y `precio_adicional_resumen` en la Reserva. Para
no bloquearte: **si T09 todavía no está mergeada, en el Paso 1 creá esas dos columnas exactamente como las
define `T09-adicional-resumen.md` §2.1** (mismo nombre, tipo y default), y dejá asentado en el commit que
T09 las reutiliza y no las vuelve a crear.

**TG6 — El webhook de LiveKit lo toca también FASE2-05.** FASE2-05 agrega `participant_left` y
`room_finished` a `aula/web/LiveKitWebhookController`. Por eso T08 va **después** de FASE2-05: agregá
`egress_ended` al mismo despacho, sin reescribir lo que dejó FASE2-05.

## 3. Decisión técnica pendiente (TS1) — PARAR hasta resolverla

**¿Cómo lee y borra el backend los objetos de Garage?**

| Opción | Pros | Contras |
|---|---|---|
| **A (recomendada):** AWS SDK for Java v2, **solo** el módulo `s3` + `url-connection-client` | Firma SigV4 probada; `path-style` y endpoint propio configurables; es el cliente que Garage documenta | Dependencia nueva (A5 → va en **ADR-000-06**, que igual se escribe por Garage) |
| B: SigV4 implementado a mano con `RestClient` | Sin dependencias | ~150 líneas de criptografía propia: canonical request, fechas, headers firmados. Un error sutil = 403 o, peor, borrar el objeto equivocado |

Con A, la configuración mínima: `S3Client.builder().endpointOverride(URI.create(garageEndpoint))
.region(Region.of("garage")).forcePathStyle(true).httpClientBuilder(UrlConnectionHttpClient.builder())`.
Anotá la respuesta del usuario en `docs/superpowers/specs/tesis/00-LEEME-tesis.md` §4 como TS1.

## 4. Pasos

### Paso 0 — Preparación (sin commit)
- `docker info`; último número de migración; suite completa y **anotá N**.
- Confirmá que T07 y FASE2-05 están mergeadas y que TS1 tiene respuesta.

### Paso 1 — Documentos y esquema (sin comportamiento)
Un commit con **todo** esto (el LEEME de la tesis exige que vayan juntos):
1. **Enmienda de la Constitución** (Artículo de minimización) + **`docs/adr/ADR-M3-04.md`**: excepción
   única y acotada a la prohibición de grabar, con las cuatro condiciones de DT5 (solo audio, solo con
   adicional, solo entre adultos, con consentimiento) y el borrado de PT6.
2. **`AGENTS.md` §1.5:** agregar "salvo la excepción de ADR-M3-04 (solo audio, solo con el adicional de
   resumen, solo entre adultos, con consentimiento, borrado al transcribir)". La prohibición sigue
   intacta para video y para todo otro caso. (AGENTS §8: se actualiza en el mismo commit que la enmienda.)
3. **`docs/adr/ADR-000-06.md`:** Garage (motivo, alternativas: Supabase Storage y MinIO descartado por
   abandono; consumo de memoria; exposición solo por HTTPS a través del proxy de Coolify; una clave de
   **solo escritura** en el bucket `audio-resumen` para LiveKit y otra de **lectura y borrado** para el
   backend; sin acceso público) **y** el cliente S3 elegido en TS1.
4. **Tabla de Tiempos:** "Retención máxima del audio del resumen — hasta obtener el transcript, máximo 24
   hs — M3/M6 (ADR-M3-04)".
5. **Migración** `V<n>__m3_egress_audio.sql`:
   ```sql
   -- ADR-M3-04: una sesión con el adicional de resumen puede tener UNA grabación de solo audio.
   ALTER TABLE aula.sesiones_aprendizaje ADD COLUMN egress_id VARCHAR(100);
   ALTER TABLE aula.sesiones_aprendizaje ADD COLUMN audio_objeto VARCHAR(300);   -- clave del objeto en Garage
   ALTER TABLE aula.sesiones_aprendizaje ADD COLUMN audio_borrado_at TIMESTAMPTZ;
   -- Consentimiento expreso (Ley 25.326): quién aceptó qué versión de qué cláusula y cuándo.
   CREATE TABLE identidad.aceptaciones_clausula (
       id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       usuario_id  UUID NOT NULL REFERENCES identidad.usuarios(id),
       clausula    VARCHAR(50) NOT NULL,       -- ej. 'GRABACION_AUDIO_RESUMEN'
       version     VARCHAR(20) NOT NULL,
       aceptada_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       UNIQUE (usuario_id, clausula, version)
   );
   ```
   (+ las columnas de TG5 si T09 no las creó.) Campos correspondientes en `SesionAprendizaje` y una
   entidad + repositorio para `aceptaciones_clausula` (`ddl-auto: validate`).
**Punto de control 1:** suite verde, N igual.
**Commit:** `docs+feat: excepcion acotada de grabacion de audio (ADR-M3-04), Garage (ADR-000-06) y esquema`.

### Paso 2 — Consentimiento (PT5)
- **Servicio** `identidad/service/ConsentimientoService`: `aceptar(usuario, clausula, version)` y
  `haAceptado(usuarioId, clausula, versionVigente)`. La versión vigente sale de configuración
  (`tinku.clausulas.grabacion-audio.version`), nunca hardcodeada en varios lugares.
- **Endpoint** `POST /api/usuarios/me/clausulas/{clausula}` (el tutor la acepta en su onboarding; el
  alumno al contratar).
- **Al contratar el adicional** (donde T09 valida `resumenContratado = true`): exigir que el **alumno**
  (pagador) tenga la cláusula aceptada en la versión vigente (si no → **422**) y que el **tutor** también
  (si no → el adicional **no se ofrece**: 422 con mensaje "Este tutor todavía no habilitó el resumen").
- **La cláusula (texto) la redacta la asesoría legal.** No la inventes: dejá un placeholder visible
  "PENDIENTE DE REVISIÓN LEGAL" y **no** habilites el adicional en `prod` hasta que exista el texto final.
**Tests RED:** `reservaConAdicional_alumnoSinAceptarClausula_422` ·
`reservaConAdicional_tutorSinClausula_422` · `aceptarClausula_registraVersionYFecha` ·
`aceptarClausula_dosVeces_esIdempotente`.
**Commit:** `feat(identidad): consentimiento expreso versionado para la grabacion de audio (PT5)`.

### Paso 3 — Egress en `LiveKitService` (TG1)
- `VideoClaim` suma `boolean roomRecord`. Token de participante → `false`. Token de servidor para egress →
  `roomRecord = true` (podés usar un método privado nuevo `tokenServidorGrabacion()`; el `tokenServidor()`
  actual queda como está para `CreateRoom`/`DeleteRoom`).
- `public String iniciarEgressAudio(String nombreSala, String objeto)` →
  `POST /twirp/livekit.Egress/StartRoomCompositeEgress`, mismo patrón que `crearSala`
  (`RestClient`, `onStatus` que lanza, `RestClientException` traducida). Body (JSON de protobuf; LiveKit
  acepta los nombres en `snake_case`):
  ```json
  { "room_name": "<sala>", "audio_only": true,
    "file_outputs": [ { "file_type": "OGG", "filepath": "<objeto>",
      "s3": { "access_key": "<clave LiveKit>", "secret": "<secreto>", "bucket": "audio-resumen",
              "endpoint": "<https://garage...>", "region": "garage", "force_path_style": true } } ] }
  ```
  Devuelve el `egress_id` de la respuesta. **Verificá los nombres exactos de los campos contra la
  documentación de la API de Egress de LiveKit** antes de escribir el test: si difieren, gana la doc.
  Las credenciales de Garage para LiveKit salen de variables de entorno
  (`tinku.garage.livekit-access-key`, `...-secret`) y **nunca** se loguean.
**Tests RED** (en `LiveKitServiceTest`, con el `HttpServer` stub que ya usa, agregando el contexto
`/twirp/livekit.Egress/StartRoomCompositeEgress`): `iniciarEgress_posteaAudioOnlyConTokenRoomRecord`
(el body tiene `"audio_only":true` y el token de servidor tiene `roomRecord=true`) y la aserción de TG1
sobre el token de participante.
**Commit:** `feat(aula): iniciar egress de solo audio con un token de servidor dedicado (ADR-M3-04)`.

### Paso 4 — Cuándo se inicia (y el doble control de menores)
En `aula/LiveKitWebhookService.registrarJoin`, en el bloque que hoy detecta que **se unieron los dos**
(`if (sesion.getTutorJoinedAt() != null && sesion.getEstudianteJoinedAt() != null)`, donde llama a
`cancelarNoShow`): si la reserva tiene `resumenContratado == true` **y** el beneficiario **no** es
`MENOR` **y** `sesion.getEgressId() == null` → `iniciarEgressAudio(sala, "sesion-" + sesionId + ".ogg")`
y guardar `egressId` y `audioObjeto`.
- **Doble control (A11):** si el beneficiario es `MENOR` y `resumenContratado` es `true` → **no** se
  inicia, log `ERROR` ("adicional de resumen en una sesión con menor: bloqueado").
- **Fail-open del registro, fail-closed de la grabación:** si `iniciarEgressAudio` falla, el join **se
  registra igual** (no se rompe la clase por el resumen), no se reintenta la grabación, y se loguea
  `WARN`. El resumen de esa sesión va a quedar `FALLIDO` y T09 reembolsa el adicional: es lo correcto.
**Tests RED** (integración, `LiveKitService` como `@MockBean`):
`sesionConAdicional_adultos_ambosUnidos_iniciaEgressUnaVez` ·
`sesionConAdicional_beneficiarioMenor_noIniciaEgress` · `sesionSinAdicional_noIniciaEgress` ·
`egressFalla_elJoinSeRegistraIgual` · `joinRepetido_noIniciaUnSegundoEgress`.
**Commit:** `feat(aula): grabar solo audio al unirse ambos, nunca con menores (DT5)`.

### Paso 5 — Cliente de Garage (según TS1)
`resumen/port/AlmacenamientoAudio` (puerto) con `byte[] leer(String objeto)` y `void borrar(String objeto)`,
implementado con el cliente de TS1. Sin logs del contenido. Test contra un stub (si TS1 = A, mockeá el
`S3Client`; no levantes un Garage real en los tests).
**Commit:** `feat(resumen): acceso al audio en Garage por un puerto (ADR-000-06)`.

### Paso 6 — `egress_ended`, transcripción y la carrera de TG2
1. **Controller:** despachar `egress_ended` a `LiveKitWebhookService.registrarFinEgress(egressId, estado)`
   (la firma HMAC ya se valida antes; no la toques).
2. **`ResumenService.onSesionFinalizada`:** si la sesión tiene `egressId != null`, crear la fila
   `PENDIENTE` **sin** agendar la generación (TG2). Si no tiene egress, **nada cambia**.
3. **`registrarFinEgress`:** si terminó bien → `resumenService.programarReintento(sesionId, Instant.now())`
   (reusa el job y el backoff existentes). Si terminó con error → la fila pasa a `FALLIDO` (T09 reembolsa)
   y se agenda el borrado inmediato por si quedó un archivo parcial.
4. **`TranscriptSesionProveedorGemini`** (T07) obtiene el audio con `AlmacenamientoAudio.leer(sesion.getAudioObjeto())`
   y aplica TG4 (Files API si supera el límite inline).
**Tests RED:** `sesionFinalizada_conEgress_noGeneraAntesDelAudio` (TG2) ·
`egressEnded_ok_disparaLaGeneracion` · `egressEnded_error_resumenFallido`.
**Commit:** `feat(resumen): el resumen espera al audio (egress_ended) en vez de adelantarse`.

### Paso 7 — Borrado (PT6), por Quartz (A4)
- Al generarse el transcript con éxito (en `ResumenService.ejecutarGenerar`, **después** de obtener el
  crudo y antes de llamar al proveedor de resumen) → borrar el audio (`AlmacenamientoAudio.borrar`) y
  marcar `audio_borrado_at`.
- **Job de tope:** `PurgaAudioJob`, agendado al iniciar el egress para `inicio + 24 hs` (la fila de la
  Tabla). Si el audio sigue ahí (`audio_borrado_at IS NULL`), lo borra y, si el resumen sigue `PENDIENTE`,
  lo pasa a `FALLIDO`. Idempotente.
**Tests RED:** `transcriptObtenido_borraElAudio` · `purga24hs_borraAunqueElResumenFalle` ·
`purga_esIdempotente`.
**Commit:** `feat(resumen): borrar el audio al transcribir y a las 24 hs como maximo (PT6)`.

### Paso 8 — Arranque seguro y documentación
- `config/ArranqueSeguroValidator`: fuera de `dev`/`test`, si `tinku.resumen.proveedor=gemini`, exigir
  `GEMINI_API_KEY` y las credenciales de Garage (no vacías).
- `REGISTRO_FINDINGS.md`: AUD-024 → `CERRADO` (con T07). Tasks: T-TES-08 en los dos archivos.
- `Spec_M3` y `Spec_M6`: el flujo del audio, quién accede y cuándo se borra.
**Commit:** `docs+feat: T08 cerrada (grabacion de solo audio para el resumen)`.

## 5. Criterios de aceptación
- Suite verde en cada commit. N final ≥ N inicial + tests nuevos.
- **Ningún camino graba video** (`rg -n "audio_only" backend/src/main` muestra `true` en el único request
  de egress) y **ningún camino graba con un menor** (tests de los pasos 4 y 6).
- La cláusula legal existe y está revisada antes de habilitar el adicional en `prod`.

## 6. NO tocar
- El buffer rotativo de 30 s del kill-switch.
- El token de participante más allá de `roomRecord = false` (AUD-002).
- `AnonimizadorTranscript` (ADR-M6-02).
