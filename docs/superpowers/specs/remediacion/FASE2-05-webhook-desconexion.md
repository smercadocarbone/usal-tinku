# FASE2-05 — Duración efectiva contra la desconexión real (AUD-029)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** medio (decide reembolso vs. liberación) ·
**Finding:** AUD-029.

## 1. Problema (verificado al 2026-09-22)

`LiveKitWebhookController.recibir` solo procesa `participant_joined`; `participant_left` y
`room_finished` se ignoran. La duración efectiva se calcula en `SesionService` como
`Duration.between(sesion.getInicioReal(), Instant.now())` cuando corre el `CorteAutomaticoJob`
(a fin agendado + 5 min, fila "Tolerancia de gracia al fin de la sesión" de la Tabla de Tiempos).

**Bug monetario:** sesión de 120 min, ambos se desconectan a los 5 min y no vuelven. El job corre
a fin+5 y calcula ~125 min "efectivos" → no es corte antes del 50% → emite `sesion.finalizada` →
M5 libera el escrow al Tutor por una clase de 5 minutos. Además, `finalizada_anticipada` hoy solo
lo asigna `ejecutarNoShow` (`Spec_M3` US-8 y caso borde #6 lo exigen también por desconexión).

## 2. Decisión de diseño (del arquitecto)

**No se inventa ningún plazo nuevo** (Tabla de Tiempos, A3). `Spec_M3` US-8 habla de "agotarse la
tolerancia", pero la Tabla no define una tolerancia de reconexión a mitad de sesión. Por eso:

- El webhook **solo registra hechos** (quién está conectado y desde cuándo el par está roto).
- El `CorteAutomaticoJob` que ya existe (fin + 5 min) **decide** con esos hechos.
- Una clase existe mientras estén **los dos**: Tutor y beneficiario. El "fin efectivo" es el
  instante desde el cual dejó de haber dos participantes conectados **y nadie volvió a
  completar el par**.
- **No** se cierra la sesión apenas salen los dos: un microcorte de red de ambos (US-4,
  degradación) no puede terminar una clase. Si el producto quiere cierre inmediato, necesita una
  fila nueva en la Tabla de Tiempos → **PARAR y preguntar**, fuera de esta tarea.

## 3. Archivos

- **Nuevo:** `V<siguiente>__m3_estado_conexion.sql`
- `aula/model/SesionAprendizaje.java`, `aula/web/LiveKitWebhookController.java`,
  `aula/LiveKitWebhookService.java`, `aula/SesionService.java`
- Tests: `aula/web/LiveKitWebhookIntegracionTest.java`, `aula/web/KillswitchIntegracionTest.java`
  (los de `tM310_corteAutomatico_*` viven ahí) o `SesionesIntegracionTest.java`.

## 4. Pasos

1. **Migración** en `aula.sesiones_aprendizaje`:
   `tutor_conectado BOOLEAN NOT NULL DEFAULT false`,
   `estudiante_conectado BOOLEAN NOT NULL DEFAULT false`,
   `par_roto_at TIMESTAMPTZ NULL` (instante desde el que no están los dos; `NULL` = están los dos
   o todavía no empezó). Campos equivalentes en la entidad (`ddl-auto: validate`: migración
   primero, entidad después, o el contexto no levanta).
2. **Webhook:** el controller despacha también `participant_left` → `webhookService.registrarSalida(sala, identidad)`.
   `room_finished` → `registrarSalaTerminada(sala)` (marca a los dos como desconectados).
   Mantené la verificación de firma **antes** de parsear (ya está).
3. **`registrarJoin`** (existente): además de lo que hace hoy, pone en `true` el flag del rol que
   entró (usá el mismo `mismaPersona` para distinguir Tutor de beneficiario; un tercero no cuenta).
   Si después del join están los dos → `par_roto_at = null`.
4. **`registrarSalida`:** pone en `false` el flag del rol. Si antes estaban los dos → `par_roto_at = now()`.
   Si la sesión ya está en un estado terminal (`finalizada`, `finalizada_anticipada`,
   `interrumpida`) → no-op (un `participant_left` tardío del corte del kill-switch no toca nada).
5. **Cálculo de duración efectiva** (`SesionService`, donde hoy se usa `Instant.now()` en el corte
   automático): `finEfectivo = (par_roto_at != null) ? par_roto_at : now()`. Extraelo a **un solo
   método privado** y usalo en todos los cálculos del corte automático (hoy hay 3 cálculos
   `Duration.between(inicioReal, …)` casi iguales: unificarlos es parte del fix, no refactor de
   oportunidad, porque el bug es justamente que cada uno toma un "fin" distinto).
6. **Estado final** en el corte automático: si `par_roto_at` es anterior al fin agendado
   (`horario + duracionAgendada`) → `finalizada_anticipada`; si no → `finalizada`. El **evento**
   no cambia de regla: `< 50%` → `sesion.interrumpida`, si no → `sesion.finalizada` (A2).

## 5. Tests obligatorios (RED primero)

1. `ambosSeDesconectanALos5min_corteAutomatico_emiteInterrumpidaYFinalizadaAnticipada` — sesión
   de 120 min; joins de los dos con `inicioReal` fijado en el pasado; `participant_left` de los dos
   a los 5 min (fijá `par_roto_at` vía webhook, no directo en la BD); `ejecutarCorteAutomatico` →
   evento `sesion.interrumpida`, estado `finalizada_anticipada`, `duracionEfectivaSegundos ≈ 300`.
   **Es el test que falla antes del fix** (hoy emite `sesion.finalizada`).
2. `seVaUnoYVuelve_elParSeRecompone_noCuentaComoCorte` — left + join del mismo rol → `par_roto_at` nulo.
3. `participantLeftSobreSesionYaCortada_noModificaNada`.
4. `webhookParticipantLeft_sinFirmaValida_401` (regresión de seguridad).
5. Regresión: los `tM310_corteAutomatico_*` existentes siguen verdes sin cambiarlos.

Para firmar webhooks reutilizá el helper `firmar(...)` que ya usan `SesionesIntegracionTest` y
`LiveKitWebhookIntegracionTest`.

## 6. Criterios de aceptación

- Suite completa verde, sin bajas.
- `REGISTRO_FINDINGS.md` AUD-029 → `CERRADO`.
- `Spec_M3`: reemplazar las notas "NO IMPLEMENTADO (AUD-029)" de US-5 y US-8 por lo implementado,
  incluyendo que **no** hay cierre inmediato al salir ambos (y por qué). No borrar el texto de las
  historias.
- `LiveKitWebhookService.mismaPersona` conserva la rama del DNI (se borra en FASE3-03).

## 7. NO tocar

- Nombres de eventos (A2). El umbral del 50% (FR-AULA-005): se mide igual, lo que cambia es el "fin".
- El flujo del kill-switch (`cortar`).
