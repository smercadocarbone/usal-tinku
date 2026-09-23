# Plan de Remediación post-Auditoría — Tinku

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans para implementar este plan tarea por tarea. Los pasos usan checkbox (`- [ ]`) para tracking.

**Goal:** Cerrar los 36 hallazgos de la auditoría independiente del 2026-09-21, empezando por reconciliar la documentación con el sistema real, para que Tinku sea defendible como tesis y seguro para usuarios menores de edad.

**Architecture:** Cinco fases secuenciales con dependencias duras. FASE 0 (documentación) NO toca código de producción y establece el registro de trazabilidad (`docs/auditoria/REGISTRO_FINDINGS.md`) que todas las fases siguientes actualizan. FASE 1 cierra los 11 findings CRÍTICOS de seguridad. FASE 2 cierra integridad de datos y funcionalidad incompleta. FASE 3 son mejoras de arquitectura acotadas. FASE 4 es opcional. Cada fix de código va acompañado de un test de regresión que falla ANTES del fix.

**Tech Stack:** Java 21 + Spring Boot 3.3.4, PostgreSQL 16 + pgvector, Flyway, Quartz JDBC, Next.js 14 + React 18, FastAPI + sentence-transformers, Testcontainers, Playwright.

**Spec:** `docs/auditoria/2026-09-21-auditoria-independiente.md` — el informe completo con los 36 findings (ID, evidencia, escenario, recomendación). **Este plan argumenta desde ese informe: leelo antes de ejecutar cualquier tarea, y releé el finding específico (AUD-XXX) antes de cada tarea que lo referencie.**

---

## Global Constraints

Requisitos del proyecto que aplican a **TODAS** las tareas de este plan. Valores copiados textualmente de `AGENTS.md` y `docs/Constitucion_Tinku.md` v2.2.

- **Java 21 obligatorio.** El `maven-enforcer-plugin` exige `[21,22)`. Correr siempre con `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home`. Con otra JVM el build falla en el enforcer y **no corre ni un solo test** (y si se pipea la salida, el exit code puede dar 0 y parecer éxito — ver Anexo A).
- **Docker corriendo.** La suite de integración usa Testcontainers contra Postgres real. Sin Docker, los tests de integración fallan por timeout de conexión.
- **Comando canónico de verificación:** `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test` desde `backend/`. Baseline verificado el 2026-09-21: **383 tests, 0 failures, 0 errors, 0 skipped**.
- **Una migración Flyway ya aplicada NUNCA se edita** (AGENTS.md §7). V1 a V23 son intocables. Toda corrección de esquema va en una migración **nueva** a partir de V24.
- **Los nombres de eventos de dominio NUNCA se renombran** (AGENTS.md §4): `sesion.finalizada`, `sesion.interrumpida`, `sesion.no_show_estudiante`, `sesion.no_show_tutor`, `sesion.no_show_doble`, `sesion.killswitch_menor`, `sesion.killswitch_adultos`, `denuncia.registrada`, `denuncia.resuelta`.
- **Ningún plazo de negocio se inventa.** Todo número de tiempo sale de `docs/Tabla_Tiempos_Tinku.md`. Si un plazo no está ahí, se para y se pregunta.
- **Ningún timeout de negocio en memoria** (Artículo X). Escrow, kill-switch, cancelaciones, aprobaciones: Quartz persistido. Prohibido `Thread.sleep`, `@Scheduled` sin persistencia o timers en memoria.
- **Prohibido crear un microservicio nuevo** (Artículo VIII). La única excepción ya decidida es `matching-service`.
- **Prohibido introducir un message broker externo** (Artículo IX). Kafka, RabbitMQ, SQS: no.
- **Ante dos soluciones que cumplen el mismo requisito, la más simple** (Artículo VII). Justificar contra 1 desarrollador y USD 0-100/mes, no contra "buena práctica genérica".
- **Commits en formato conventional commits.** Sin `Co-Authored-By` ni ninguna atribución de IA.
- **Un chunk = un branch** (AGENTS.md §7). Convención de branch para este plan: `aud/fase{N}-{slug}` (ej. `aud/fase0-docs`).

---

## GUARDRAILS — leer completo antes de la Tarea 0.1

Esta sección es tan obligatoria como las tareas. Está dividida en dos: lo que **no se toca** y lo que **no se pasa por alto**.

### A. Prohibiciones duras — qué NO se debe tocar

| # | Prohibición | Por qué |
|---|---|---|
| A1 | **No editar `V1__*.sql` … `V23__*.sql`.** Ni para "arreglar un typo". | AGENTS.md §7. Una migración aplicada editada rompe el checksum de Flyway en cualquier entorno que ya la corrió. |
| A2 | **No renombrar eventos de dominio.** | AGENTS.md §4. Hay 9 listeners escuchando por nombre exacto. |
| A3 | **No tocar `docs/Tabla_Tiempos_Tinku.md`.** Es fuente de verdad de plazos; cambiarla cambia reglas de negocio. | AGENTS.md §0. |
| A4 | **No borrar texto histórico de la Constitución, de los ADR ni de los Specs.** El CAP retirado se conserva marcado, no se borra. | Constitución, Artículo XII + precedente de la enmienda v2.1→v2.2. |
| A5 | **No "arreglar" los 27 imports cruzados entre módulos en FASE 0 ni en FASE 1.** Es FASE 3, acotada a 3 movimientos específicos. | AUD-019. Un refactor masivo rompe los 383 tests y hace irrevisable el diff de seguridad. |
| A6 | **No mezclar en un mismo commit un fix de código con la actualización de documentación**, salvo donde este plan lo indique explícitamente. | AGENTS.md §5. Hace imposible revisar el fix de seguridad. |
| A7 | **No agregar dependencias nuevas** (Maven, bun, pip) sin un ADR previo. Incluye librerías de rate limiting, de JWT alternativo, de validación. | AGENTS.md §2. |
| A8 | **No adaptar un test existente para que pase.** Si un fix rompe un test, el test puede estar codificando el bug. Caso real: `LiveKitServiceTest` hoy no asserta `roomAdmin`, y arreglarlo (AUD-002) no debe romperlo — si lo rompe, entender por qué antes de tocarlo. | AUD-002. |
| A9 | **No bajar la severidad de un finding ni cerrarlo "por análisis".** Un finding se cierra con un commit + un test de regresión, o se mueve a "aceptado" con un ADR que lo justifique. | Trazabilidad de tesis. |
| A10 | **No refactorizar de oportunidad.** Si ves código feo al lado del fix, no lo toques. Anotalo en `docs/auditoria/REGISTRO_FINDINGS.md` como observación. | El diff de cada fix P0 tiene que ser revisable de un vistazo. |
| A11 | **No implementar el clasificador NSFW on-device (T-M3-06) dentro de este plan.** Es un chunk propio, con su ADR y su spike ya cerrado. | Alcance. Está fuera de las 5 fases. |
| A12 | **No tocar `frontend/tests/` para hacer pasar un cambio de backend.** Los E2E mockean `/api/**`; si un cambio de contrato los rompe, ese es exactamente el bug de AUD-031 y hay que registrarlo. | AUD-031. |
| A13 | **No crear un `application-prod.yml` con valores reales de secretos.** Solo placeholders por variable de entorno. | Artículo V + higiene. |
| A14 | **No commitear `.env`.** Ya está en `.gitignore` y nunca estuvo en el historial (verificado). Mantenerlo así. | Seguridad. |

### B. Qué NO se debe pasar por alto — obligatorio en cada tarea

| # | Obligación |
|---|---|
| B1 | **Antes de empezar una tarea, releer el finding AUD-XXX completo** en `docs/auditoria/2026-09-21-auditoria-independiente.md`. La sección "Evidencia" tiene los números de línea exactos. |
| B2 | **Todo fix de código de FASE 1 y FASE 2 necesita un test que FALLE antes del fix.** Escribir el test primero, correrlo, ver el rojo, y recién ahí arreglar. Sin el rojo previo no hay prueba de que el test cubra algo. |
| B3 | **Correr la suite completa al cerrar cada tarea**, no solo el test nuevo. Comando canónico arriba. Anotar el conteo de tests en el mensaje del commit o en el resumen. |
| B4 | **Actualizar `docs/auditoria/REGISTRO_FINDINGS.md`** en el mismo commit que cierra un finding: estado + hash del commit + test que lo cubre. |
| B5 | **Tildar la tarea en este plan** (`- [ ]` → `- [x]`) y la tarea correspondiente en `docs/Tasks_Tinku_Implementacion.md` si la hay. |
| B6 | **Si una tarea revela que el finding estaba mal diagnosticado, PARAR y decirlo.** No implementar un fix a un problema que no existe. Registrar la corrección en `REGISTRO_FINDINGS.md` con la evidencia. |
| B7 | **Si una tarea necesita un número de tiempo que no está en `Tabla_Tiempos_Tinku.md`, PARAR y preguntar.** No inventarlo. |
| B8 | **Si una tarea implica una decisión técnica de las listadas en AGENTS.md §2 (proveedor, índice, LLM, o cualquier fila del Registro de Decisiones), PARAR y escribir el ADR primero.** |
| B9 | **Verificar que Docker está corriendo antes de la primera tarea de cada sesión** (`docker info`). Sin Docker, los fallos de Testcontainers se confunden con fallos del fix. |
| B10 | **Nunca declarar "listo" sin haber pegado la salida real del comando de verificación.** Evidencia antes que afirmaciones. |

### C. Decisiones ya tomadas por el usuario — no volver a preguntarlas

Las tareas 1.4, 1.5, 1.7, 1.10, 2.1, 2.2, 2.3, 2.6 y 3.8 tenían "PARAR y preguntar".
El usuario las resolvió el 2026-09-22. El registro completo, con lo que implica cada una,
está en `.superpowers/sdd/decisiones/decisiones-usuario.md`. Si una tarea de este plan
todavía dice "parar y preguntar" sobre algo que ese archivo ya resuelve, gana el archivo.

---

## File Structure

Archivos que este plan crea o modifica, y de qué es responsable cada uno.

### Se crean en FASE 0

| Archivo | Responsabilidad |
|---|---|
| `docs/auditoria/2026-09-21-auditoria-independiente.md` | **Ya creado.** Informe completo, 36 findings. Insumo de todo el plan. Read-only a partir de acá: no se edita, se referencia. |
| `docs/auditoria/REGISTRO_FINDINGS.md` | Tabla viva de estado de cada AUD-XXX. **Único lugar donde se marca un finding como cerrado.** Se actualiza en el mismo commit que el fix. |
| `docs/adr/ADR-000-02.md` | Backend = Java + Spring Boot, evaluado contra Go y Node.js. Retroactivo. |
| `docs/adr/ADR-000-03.md` | Acoplamiento entre módulos: regla vigente + deuda aceptada + costo de revertir. Retroactivo. |
| `docs/adr/ADR-000-04.md` | Quartz no clusterizado: una sola instancia de backend. Retroactivo. |
| `docs/adr/ADR-M1-03.md` | Storage de archivos = filesystem local detrás del puerto `Almacenamiento`. Retroactivo. |
| `docs/adr/ADR-M6-02.md` | Anonimización por regex + diccionario como puente hasta ADR-M6-01. Retroactivo. |

### Se modifican en FASE 0 (solo documentación y comentarios — cero cambio de comportamiento)

| Archivo | Cambio |
|---|---|
| `docs/Tasks_Tinku_Implementacion.md` | Tildar lo que está implementado (verificado una por una), agregar las tareas nuevas AUD-*. |
| `docs/Tasks_Tinku_Chunks.md` | Reabrir chunks que la auditoría reabrió, corregir el conteo de tests, agregar nota de auditoría 2026-09-21. |
| `docs/specs/Spec_M3_Aula_Virtual.md` | Marcar `NO IMPLEMENTADO` los puntos de US-6/US-8/FR-AULA-010 que no existen. |
| `docs/specs/Spec_M6_Resumen_Automatico.md` | Declarar el transcript como bloqueante explícito. |
| `NOTAS_VERIFICACION.md` | Marcar como registro histórico con fecha y alcance. |
| `AGENTS.md` | Sección nueva de auditoría vigente + requisito de Java 21. |
| `backend/src/main/java/com/tinku/aula/LiveKitService.java` | **Solo comentario**: marcador `FIXME AUD-002`. |
| `backend/src/main/java/com/tinku/aula/SesionService.java` | **Solo comentario**: marcadores `FIXME AUD-001`, `FIXME AUD-003`, `FIXME AUD-005`, `FIXME AUD-006`. |
| `backend/src/main/java/com/tinku/admin/web/CredencialColaResponse.java` | **Solo comentario**: marcador `FIXME AUD-007`. |
| `frontend/src/middleware.ts` | **Solo comentario**: marcador `FIXME AUD-016`. |
| `frontend/src/lib/api.ts` | **Solo comentario**: marcador `FIXME AUD-026`. |

### Se tocan en fases posteriores (NO en FASE 0)

`LiveKitService.java`, `SesionService.java`, `ReservaService.java`, `EscrowService.java`, `DenunciaService.java`, `AlertaSeguridadService.java`, `CredencialService.java`, `UsuarioService.java`, `NotificadorResetPasswordLog.java`, `application.yml`, `matching-service/main.py`, migraciones `V24+`.

---

## Mapa de fases y dependencias

```
FASE 0 — Documentación y trazabilidad        [bloquea a todas]
   │
   ├─▶ FASE 1 — P0 Seguridad (11 findings)   [bloquea a FASE 2]
   │      │
   │      └─▶ FASE 2 — P1 Integridad y funcionalidad (13 findings)
   │             │
   │             └─▶ FASE 3 — P2 Arquitectura y calidad (11 findings)
   │                    │
   │                    └─▶ FASE 4 — P3 Opcional (6 ítems)
   │
   └─▶ (fuera de este plan) T-M3-06 — clasificador NSFW on-device
```

**Por qué FASE 0 primero:** sin el `REGISTRO_FINDINGS.md` no hay dónde anotar el cierre de cada fix, y sin la documentación reconciliada cada tarea de FASE 1 arranca leyendo un Spec que afirma que el problema no existe.

---

# FASE 0 — Reconciliar documentación con el sistema real

**Branch:** `aud/fase0-docs`
**Alcance:** documentación y comentarios. **Cero cambios de comportamiento.**
**Criterio de salida de la fase:** `./mvnw -B test` sigue dando **exactamente 383 tests, 0 failures, 0 errors**. Si el número cambió, algo se tocó que no se debía tocar.

> **Advertencia específica de esta fase:** es tentador "arreglar de paso" el código que estás documentando. NO. En FASE 0 el único cambio permitido dentro de un `.java` o `.ts` es agregar un comentario. Si tu diff de esta fase tiene una línea que no empieza con `//`, `*`, `#` o no está dentro de un `.md`, revisala.

---

### Task 0.1: Registro de findings

**Files:**
- Create: `docs/auditoria/REGISTRO_FINDINGS.md`

**Interfaces:**
- Produces: la tabla que **todas** las tareas posteriores de este plan actualizan (guardrail B4). Columnas exactas: `ID | Severidad | Título | Estado | Fase | Commit | Test que lo cubre`.

- [x] **Paso 1: Crear el archivo con las 36 filas**

Sacar ID, severidad y título de la sección "3. Findings" del informe. Estado inicial de todos: `ABIERTO`. Fase según la sección "9. Recommended Action Plan" del informe.

```markdown
# Registro de Findings — Auditoría 2026-09-21

> Fuente: `docs/auditoria/2026-09-21-auditoria-independiente.md`.
> Un finding solo pasa a `CERRADO` con un commit + un test de regresión.
> Un finding solo pasa a `ACEPTADO` con un ADR que lo justifique.
> Estados válidos: ABIERTO | EN CURSO | CERRADO | ACEPTADO | REFUTADO

| ID | Sev | Título | Estado | Fase | Commit | Test |
|----|-----|--------|--------|------|--------|------|
| AUD-001 | CRÍTICA | El kill-switch no cierra la sala de LiveKit | ABIERTO | 1 | — | — |
| AUD-002 | CRÍTICA | Tokens de LiveKit con roomAdmin y roomCreate | ABIERTO | 1 | — | — |
...
```

- [x] **Paso 2: Verificar que están las 36**

Run: `grep -c '^| AUD-' docs/auditoria/REGISTRO_FINDINGS.md`
Expected: `36`

- [x] **Paso 3: Verificar que ningún ID se repite ni falta**

Run: `grep -o '^| AUD-[0-9]*' docs/auditoria/REGISTRO_FINDINGS.md | sort | uniq -d`
Expected: salida vacía (sin duplicados).

Run: `grep -o 'AUD-[0-9]\{3\}' docs/auditoria/2026-09-21-auditoria-independiente.md | sort -u | wc -l`
Expected: `36` — el mismo conjunto que el registro.

- [x] **Paso 4: Commit**

```bash
git add docs/auditoria/
git commit -m "docs(auditoria): informe independiente 2026-09-21 + registro de findings"
```

---

### Task 0.2: Reconciliar `Tasks_Tinku_Implementacion.md` — FASE 0 del proyecto

**Files:**
- Modify: `docs/Tasks_Tinku_Implementacion.md:9-17` (T-000-01 a T-000-09)

**Interfaces:**
- Consumes: nada.
- Produces: el documento que AGENTS.md §0 declara "memoria persistente entre sesiones", ahora coherente con el código.

> **Contexto (AUD-030):** las 9 tareas de FASE 0 están sin tildar, mientras `Tasks_Tinku_Chunks.md` marca los chunks 000-A a 000-E como cerrados y el código existe. Son falsos negativos de tracking.

> **NO tildar en bloque.** Cada una se verifica contra el código antes de tocarla. Si una no se puede verificar, se deja `[ ]` y se anota por qué.

- [x] **Paso 1: Verificar T-000-01 (scaffold + paquetes por módulo)**

Run: `fd -t d -d 1 . backend/src/main/java/com/tinku | sort`
Expected: los 9 paquetes de dominio + `config` + `shared`.
Si coincide → tildar. Si no → dejar abierto y anotar qué falta.

- [x] **Paso 2: Verificar T-000-02 (schemas por módulo)**

Run: `grep -n 'schemas:' backend/src/main/resources/application.yml && cat backend/src/main/resources/db/migration/V1__crear_schemas.sql`
Expected: los 9 schemas declarados en Flyway y creados en V1.

- [x] **Paso 3: Verificar T-000-03 (Quartz persistido)**

Run: `grep -n 'job-store-type' backend/src/main/resources/application.yml && ls backend/src/test/java/com/tinku/config/QuartzPersistenciaTest.java`
Expected: `job-store-type: jdbc` y el test existe.

- [x] **Paso 4: Verificar T-000-04 (eventos de dominio)**

Run: `grep -rl 'ApplicationEventPublisher' backend/src/main/java | head`
Expected: al menos `ReservaService`, `SesionService`, `DenunciaService`.

> El enunciado de T-000-04 pide "un evento de prueba y un listener de prueba". `NOTAS_VERIFICACION.md` menciona un `DomainEventExampleTest` que **hoy no existe** en `src/test`. Tildar la tarea igual (el mecanismo está validado por 9 listeners reales en producción y por los tests de integración de M5/M9), y **anotar esa discrepancia en el mismo renglón** — no borrarla.

- [x] **Paso 5: Verificar T-000-05 (Security + JWT + bcrypt)**

Run: `ls backend/src/test/java/com/tinku/config/security/ && grep -n 'BCryptPasswordEncoder' backend/src/main/java/com/tinku/config/SecurityConfig.java`
Expected: `JwtAuthTest.java`, `SecurityHttpTest.java`, y el bean de bcrypt.

- [x] **Paso 6: Verificar T-000-06 y T-000-07 (cuentas externas)**

Estas dos son **tareas de cuenta externa, no de código**. El código de integración existe (`LiveKitService`, `MercadoPagoClientHttp`) pero este plan **no puede verificar que existan las cuentas**.

Run: `grep -n 'LIVEKIT_API_KEY\|MP_ACCESS_TOKEN' .env.example`

Dejar ambas como `[ ]` con la nota: _"código de integración implementado y testeado contra stub HTTP local; la existencia de la cuenta real no es verificable desde el repo — confirmar manualmente antes de piloto"_. **No tildar algo que no se puede verificar.**

- [x] **Paso 7: Verificar T-000-08 (servicio Python + /health)**

Run: `grep -n 'def health' matching-service/main.py && grep -rn 'MatchingServiceHealthCheck' backend/src/main/java`
Expected: el endpoint existe y el backend lo consume.

- [x] **Paso 8: T-000-09 (CI) — dejar ABIERTA**

Run: `fd . .github/workflows -t f`
Expected: `ci-backend.yml`, `ci-frontend.yml` — **falta `matching-service`** (AUD-031).

Dejar `[ ]` y reescribir el texto de la tarea para que diga exactamente qué falta:
```markdown
- [ ] T-000-09: Configurar pipeline de CI mínimo (build + tests). _(Parcial al 2026-09-21: `ci-backend.yml` y `ci-frontend.yml` existen; **falta pipeline para `matching-service/`** — `test_main.py` no corre en ningún CI. Ver AUD-031, FASE 2.)_
```

- [x] **Paso 9: Verificar que el conteo de tildes cambió como se espera**

Run: `grep -c '^- \[x\]' docs/Tasks_Tinku_Implementacion.md`
Expected: el valor previo (115) + la cantidad de tareas que efectivamente se tildaron en los pasos 1-7. Anotar ambos números.

- [x] **Paso 10: Commit**

```bash
git add docs/Tasks_Tinku_Implementacion.md
git commit -m "docs(tasks): reconciliar FASE 0 con el codigo real (AUD-030)"
```

---

### Task 0.3: Reconciliar `Tasks_Tinku_Implementacion.md` — M4 y conteos

**Files:**
- Modify: `docs/Tasks_Tinku_Implementacion.md:113-116` (T-M4-12 a T-M4-15)

> **Cuidado (AUD-030):** de las 4 tareas de este bloque, **solo T-M4-12 está implementada**. T-M4-13, T-M4-14 y T-M4-15 siguen realmente pendientes. No tildar el bloque entero.

- [x] **Paso 1: Verificar T-M4-12**

Run: `ls backend/src/main/java/com/tinku/reservas/service/HorariosDisponiblesService.java && grep -n 'horarios' backend/src/main/java/com/tinku/reservas/web/FranjaController.java backend/src/main/java/com/tinku/identidad/web/TutorController.java`
Run: `grep -n 'tM412' backend/src/test/java/com/tinku/reservas/web/ReservasFlujosIntegracionTest.java`
Expected: el servicio existe, el endpoint existe, y hay al menos 2 tests `tM412_*`.

Si coincide → tildar T-M4-12 con la nota: _"cerrado; la nota sobre `duracionMinutos` sigue vigente — la fuente del parámetro no está en ningún Spec"_.

- [x] **Paso 2: Verificar T-M4-13 (integración del picker)**

Run: `grep -n 'DynamicTimeSlotPicker' frontend/src/app/reservar/page.tsx`
Si no aparece → **dejar `[ ]`**. Es pendiente real.

- [x] **Paso 3: Verificar T-M4-14 (manejo del 409)**

Run: `grep -n 'isConflictError\|409' frontend/src/app/reservar/page.tsx`
Si no aparece → **dejar `[ ]`**. Agregar referencia cruzada: _"bloqueada por AUD-009: hoy el 409 solo cubre horarios idénticos, no solapados"_.

- [x] **Paso 4: Dejar T-M4-15 abierta** y agregar la referencia a AUD-009 (el test de solapamiento parcial que falta).

- [x] **Paso 5: Agregar el bloque de tareas nuevas de auditoría**

Al final del archivo, una sección nueva. Una línea por cada finding que genera trabajo, con su fase:

```markdown
## FASE AUD — Remediación de la auditoría 2026-09-21

> Plan completo: `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`
> Estado por finding: `docs/auditoria/REGISTRO_FINDINGS.md`

### FASE 1 — P0 Seguridad
- [ ] T-AUD-001: `VideoClaim(sala, true, **false**, **false**)` + aserciones negativas en `LiveKitServiceTest` (AUD-002)
- [ ] T-AUD-002: Cerrar la sala de LiveKit en `cortar()` (`RemoveParticipant` + `DeleteRoom`) y rechazar `/token` sobre sesiones cerradas (AUD-001)
...
```

(La lista completa sale de la sección "9. Recommended Action Plan" del informe — 41 acciones numeradas.)

- [x] **Paso 6: Verificar que no se rompió el formato de checkbox**

Run: `grep -c '^- \[ \]\|^- \[x\]' docs/Tasks_Tinku_Implementacion.md`
Expected: mayor que antes, sin líneas malformadas.

Run: `grep -n '^- \[' docs/Tasks_Tinku_Implementacion.md | grep -v '^\S*:- \[ \]\|^\S*:- \[x\]'`
Expected: salida vacía.

- [x] **Paso 7: Commit**

```bash
git add docs/Tasks_Tinku_Implementacion.md
git commit -m "docs(tasks): reconciliar M4-12..15 y agregar FASE AUD (AUD-030)"
```

---

### Task 0.4: Reconciliar `Tasks_Tinku_Chunks.md`

**Files:**
- Modify: `docs/Tasks_Tinku_Chunks.md`

> Este archivo ya hace bien lo correcto con M3-C y M6-D (declara el pendiente y explica por qué). La tarea es **extender ese mismo criterio** a lo que la auditoría encontró, no reescribirlo.

- [x] **Paso 1: Corregir el conteo de tests**

Buscar `320 tests` y reemplazar por `383 tests (verificado 2026-09-21, JDK 21 + Testcontainers)`.

Run: `grep -n '320 tests' docs/Tasks_Tinku_Chunks.md`

- [x] **Paso 2: Reabrir Chunk M3-C**

Hoy dice `[~]` y solo menciona T-M3-06. Agregar que **T-M3-07 tampoco está completo**: el backend decide la rama correctamente (eso sí está y está testeado) pero **no corta la sala** (AUD-001) y el disparo no exige evidencia (AUD-005).

- [x] **Paso 3: Reabrir Chunk M3-B**

Agregar: _"el webhook de LiveKit solo procesa `participant_joined`; `participant_left` y `room_finished` no se manejan, así que US-5/US-8 (corte por desconexión) no tienen implementación server-side — AUD-029"_.

- [x] **Paso 4: Reabrir Chunk M8-E**

Agregar: _"la resolución de credencial desde la cola está, pero **no existe endpoint para ver el archivo** que se está aprobando — AUD-007"_.

- [x] **Paso 5: Corregir la nota de M6-D**

Hoy atribuye el bloqueo solo al ADR del LLM. Agregar el segundo bloqueante: _"además del ADR del proveedor, **falta el transcript**: `TranscriptSesionProveedorNoDisponible` devuelve `null` siempre y M3 no genera transcript (sin LiveKit Egress). Elegir proveedor de LLM no desbloquea M6 por sí solo — AUD-024"_.

- [x] **Paso 6: Agregar la sección de auditoría al final**

```markdown
## Nota de auditoría — 2026-09-21

Auditoría técnica independiente (segunda opinión, fuera del ciclo de desarrollo asistido).
Informe: `docs/auditoria/2026-09-21-auditoria-independiente.md` — 36 findings.
Registro de estado: `docs/auditoria/REGISTRO_FINDINGS.md`.
Plan de remediación: `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`.

La nota de auditoría del 2026-09-18 afirmaba que "todo lo demás marcado `[ ]` era un falso
negativo de tracking, no trabajo faltante". Esta auditoría corrige esa conclusión: hay 8
findings CRÍTICOS de seguridad y 13 de integridad que no estaban registrados en ningún lado,
y la suite verde (383 tests) no los detecta porque ninguno de ellos es expresable como
"request → estado en BD".

**Verificación de la suite al 2026-09-21:** 383 tests, 0 failures, 0 errors, 0 skipped.
```

- [x] **Paso 7: Commit**

```bash
git add docs/Tasks_Tinku_Chunks.md
git commit -m "docs(chunks): reabrir M3-B/M3-C/M8-E y M6-D con los hallazgos de la auditoria"
```

---

### Task 0.5: Marcar en `Spec_M3` lo que no está implementado

**Files:**
- Modify: `docs/specs/Spec_M3_Aula_Virtual.md`

> **Convención:** el proyecto ya usa el marcador `RETIRADO` en `Spec_M1` para requisitos que salieron de alcance, conservando el texto original. Acá se usa el mismo mecanismo con un marcador distinto: **`NO IMPLEMENTADO (AUD-XXX, 2026-09-21)`**. No se borra ni se reescribe el texto del requisito — se le agrega el marcador. La diferencia importa: `RETIRADO` = decisión de producto; `NO IMPLEMENTADO` = deuda.

- [x] **Paso 1: US-6, línea ~50 — el corte**

Agregar al final del criterio de aceptación:
```
**NO IMPLEMENTADO (AUD-001, 2026-09-21):** el corte cambia el estado en la base
(`sesiones_aprendizaje`, `reservas`) y emite el evento, pero NO cierra la sala de LiveKit
ni revoca los tokens emitidos. Ver FASE 1 del plan de remediación.
```

- [x] **Paso 2: US-6, línea ~50 — la notificación**

```
**NO IMPLEMENTADO (AUD-014, 2026-09-21):** no existe infraestructura de notificación en el
sistema. El Adulto Responsable no recibe ningún aviso. Ver FASE 2.
```

- [x] **Paso 3: US-6, línea ~50 — el buffer de 30s**

```
**NO IMPLEMENTADO (AUD-014/T-M3-06, 2026-09-21):** el endpoint `POST /api/sesiones/{id}/evidencia`
existe pero ningún cliente lo llama — no hay MediaRecorder en `frontend/`.
```

- [x] **Paso 4: US-6, línea ~51 — quién es el detectado**

```
**IMPLEMENTADO CON DESVÍO (AUD-006, 2026-09-21):** el corte se aplica en todos los casos,
correcto. Pero `ramaMenor()` suspende siempre al Tutor, incluso cuando el detectado es el
menor, y la resolución de la Alerta en M9 nunca lo reactiva. Ver FASE 1.
```

- [x] **Paso 5: FR-AULA-010, línea ~87**

```
**NO IMPLEMENTADO (AUD-001, 2026-09-21):** `SesionService.obtenerToken()` no consulta ningún
estado del clasificador; la sala se habilita siempre.
```

- [x] **Paso 6: US-5 (línea ~44) y US-8 (línea ~72) — desconexiones**

```
**NO IMPLEMENTADO (AUD-029, 2026-09-21):** el webhook de LiveKit solo procesa
`participant_joined`. La duración efectiva se calcula contra `Instant.now()` del job de corte,
no contra la última desconexión real, lo que puede liberar el escrow por una sesión de minutos.
El estado `finalizada_anticipada` hoy solo se asigna en `ejecutarNoShow`.
```

- [x] **Paso 7: Verificar que no se rompió nada del texto original**

Run: `git diff --stat docs/specs/Spec_M3_Aula_Virtual.md`
Expected: solo líneas agregadas (`+`), **cero líneas borradas** salvo las que se parten en dos para insertar el marcador.

Run: `git diff docs/specs/Spec_M3_Aula_Virtual.md | grep '^-' | grep -v '^---'`
Expected: salida vacía o solo reformateo evidente. **Si hay texto de requisito borrado, revertir.**

- [x] **Paso 8: Commit**

```bash
git add docs/specs/Spec_M3_Aula_Virtual.md
git commit -m "docs(spec-m3): marcar NO IMPLEMENTADO los puntos de US-5/6/8 y FR-AULA-010"
```

---

### Task 0.6: Declarar el bloqueante real de M6

**Files:**
- Modify: `docs/specs/Spec_M6_Resumen_Automatico.md:5` (línea "Depende de:")

- [x] **Paso 1: Verificar el estado actual del proveedor de transcript**

Run: `cat backend/src/main/java/com/tinku/resumen/port/TranscriptSesionProveedorNoDisponible.java`
Expected: `return null;` como única implementación.

Run: `grep -rn 'Egress\|egress' backend/src/main/java`
Expected: salida vacía — no hay integración de grabación.

- [x] **Paso 2: Agregar la nota de bloqueo**

Debajo de la línea `**Depende de:**`:
```markdown
> **ESTADO AL 2026-09-21 (AUD-024):** M6 no puede generar un resumen por **dos** motivos
> independientes:
> 1. No hay proveedor de LLM (ADR pendiente, ya registrado en T-FIN-03).
> 2. **No hay transcript.** M3 no lo produce: `LiveKitService` no usa Egress y
>    `sesiones_aprendizaje` no tiene columna de transcript. El bean por defecto
>    `TranscriptSesionProveedorNoDisponible` devuelve `null` siempre, y el pipeline marca
>    la fila como `fallido` (caso borde #2).
>
> Resolver el ADR del LLM **no** desbloquea M6 por sí solo.
>
> **Advertencia de alcance:** grabar audio de sesiones donde hay un menor presente es
> exactamente lo que el Artículo V restringe. El ADR de Egress/retención es más delicado que
> el del LLM y debe justificarse contra la Ley 25.326 ANTES de implementarse, no después.
```

- [x] **Paso 3: Commit**

```bash
git add docs/specs/Spec_M6_Resumen_Automatico.md
git commit -m "docs(spec-m6): declarar el transcript como bloqueante y el riesgo del Articulo V"
```

---

### Task 0.7: Marcar `NOTAS_VERIFICACION.md` como histórico

**Files:**
- Modify: `NOTAS_VERIFICACION.md` (encabezado)

> Este archivo documenta el branch `chunk/m1-g`, habla de "102 tests" y presenta como logro el circuito del CAP, que ADR-M1-02 retiró. **No se borra** (guardrail A4): es registro histórico válido y contiene el hallazgo del `driverDelegateClass` de Quartz, que es buen material de defensa.

- [x] **Paso 1: Agregar el bloque de encabezado**

Arriba de todo, antes del `# NOTAS_VERIFICACION`:
```markdown
> **REGISTRO HISTÓRICO — no refleja el estado actual del sistema.**
> Fecha del contenido: cierre del branch `chunk/m1-g`.
> Revisado el 2026-09-21. Desvíos conocidos respecto del estado actual:
> - Dice "102 tests". La suite hoy tiene **383** (verificado 2026-09-21, JDK 21).
> - Presenta el circuito de CAP (US-6, FR-ID-021 a 025) como logro. **El CAP fue retirado
>   por ADR-M1-02**; las tablas de `V6__m1_certificados_antecedentes_penales.sql` quedaron
>   huérfanas en la base (AUD-035).
> - Dice que `/api/admin/**` queda en `authenticated()`. Hoy está cerrado por
>   `AdminActivoAuthorizationManager` + `AdminModeracionGate` (M8).
> - Menciona un `DomainEventExampleTest` que ya no existe en `src/test`.
> - La sección de OCR real (`TesseractOcrServiceRealTest`) SIGUE VIGENTE y es correcta.
>
> El hallazgo del `driverDelegateClass` de Quartz sigue siendo válido y está reflejado en
> `application.yml`.
```

- [x] **Paso 2: Commit**

```bash
git add NOTAS_VERIFICACION.md
git commit -m "docs: marcar NOTAS_VERIFICACION como registro historico con sus desvios"
```

---

### Task 0.8: ADRs retroactivos — solo de decisiones que PERMANECEN

**Files:**
- Create: `docs/adr/ADR-000-02.md`, `docs/adr/ADR-000-03.md`, `docs/adr/ADR-000-04.md`, `docs/adr/ADR-M1-03.md`, `docs/adr/ADR-M6-02.md`

> **Regla que no se puede romper en esta tarea:** solo se escribe ADR de decisiones que **van a seguir vigentes después del plan**. No se escribe ADR de algo que FASE 1 va a revertir.
>
> **ADRs que este plan NO escribe, y por qué:**
> - DNI como `sub` del JWT / identity de LiveKit → **se revierte en FASE 1** (AUD-003/AUD-027). Documentarlo sería justificar un bug.
> - Notificador = log de aplicación → **se reemplaza en FASE 2** (AUD-008/AUD-014). El ADR se escribe cuando se elija proveedor.
> - Middleware de Next.js sin verificar firma → se decide en FASE 3 (AUD-016).
> - JWT en `localStorage` → **sí** se documenta, pero como sección dentro de `ADR-000-04`, no como ADR propio (es un trade-off, no una elección de tecnología).
>
> **Formato:** copiar la estructura de `docs/adr/ADR-M1-02.md`, que es el mejor ADR del repo: Estado / Contexto / Decisión / Alternativas descartadas / Riesgo aceptado / Implementación / Consecuencias / Registro de Decisiones Técnicas. Todos llevan `**Estado:** Aceptado — documentado retroactivamente el 2026-09-21 (decisión ya tomada en el código, recién formalizada)`, que es el mecanismo que AGENTS.md §7 exige para este caso.

- [x] **Paso 1: `ADR-000-02` — Backend Java + Spring Boot**

La Constitución dice "Decidido, evaluado contra Go y Node.js" y no existe el documento con esa evaluación. Es la decisión tecnológica más visible del trabajo y la única sin respaldo escrito (informe §7.2, pregunta 8).

Contenido mínimo obligatorio, argumentado contra **1 desarrollador y USD 0-100/mes** (Artículo VII), no contra "buena práctica":
- Quartz con JobStore JDBC persistido: requisito duro del Artículo X. Comparar la madurez real de esa pieza en Java vs. Go vs. Node.
- JPA/Hibernate + Flyway + `ddl-auto: validate` para 23 migraciones y 9 schemas.
- Spring Security como implementación del NFR-SEC-02 sin escribir auth propia desde cero.
- Transacción del publicador compartida con los listeners de `ApplicationEventPublisher` — es lo que da atomicidad sin saga (`Plan_M9 §2.5`).
- Familiaridad del único desarrollador como criterio explícito y legítimo bajo Artículo VII.
- **Consecuencia honesta que hay que escribir:** el ecosistema de `sentence-transformers` no existe en Java, y eso forzó la única excepción al Artículo VIII (`matching-service`). El ADR debe decirlo, no ocultarlo.

- [x] **Paso 2: `ADR-000-03` — Acoplamiento entre módulos: deuda aceptada**

Este es el ADR que convierte el finding AUD-019 de vulnerabilidad de defensa en decisión de ingeniería. Contenido obligatorio:

- **El estado medido, con números:** 27 imports directos a repositorios de otros módulos; 8 de 9 módulos manipulan entidades JPA ajenas; 2 ciclos (`shared ↔ admin`, `shared ↔ identidad`); los eventos viven en el módulo consumidor.
- **La regla que efectivamente rige hoy** (hay que escribirla, porque hoy no está escrita en ningún lado). Propuesta: _lectura cruzada de repositorios permitida; escritura sobre entidades de otro módulo solo por puerto o por evento_. Si el código real no cumple ni eso, **escribir la regla real, no la deseada**.
- **Qué SÍ se corrige** (FASE 3, acotado a 3 movimientos): romper el ciclo `shared ↔ admin`, mover `AlertaSeguridad` a `seguridad`, mover cada evento al módulo que lo publica.
- **Qué NO se corrige y por qué:** el resto de los accesos cruzados. Costo estimado vs. beneficio contra 1 desarrollador.
- **Cómo se evita que empeore:** un test de ArchUnit que congele el conteo actual. **No agregar la dependencia de ArchUnit en esta tarea** (guardrail A7) — el ADR la propone, FASE 3 decide.

- [x] **Paso 3: `ADR-000-04` — Una sola instancia: Quartz no clusterizado + sesión en el cliente**

Agrupa las decisiones de escala y sesión que permanecen:
- `isClustered: false`: el sistema no soporta más de una instancia sin duplicar jobs. Consciente, coherente con el presupuesto, y el costo de levantarlo es conocido (activar clustering de Quartz + revisar los guards de idempotencia, que ya existen).
- JWT en `localStorage` + cookie espejo no-`httpOnly` para el middleware: trade-off estándar, con la mitigación que FASE 3 agrega (`credentials_version`).
- TTL de 60 minutos sin refresh token.
- **Escribir el límite en números**, no en adjetivos: con qué carga concreta esta decisión deja de servir.

- [x] **Paso 4: `ADR-M1-03` — Storage de archivos: filesystem local tras el puerto `Almacenamiento`**

`NOTAS_VERIFICACION.md` lo cita como "ADR pendiente" y nunca se escribió. La decisión es correcta para el alcance (el puerto aísla bien) y permanece. Debe incluir:
- Por qué el puerto `Almacenamiento` hace que el reemplazo por S3 no toque llamadores.
- **La limitación que hay que declarar:** la URI `file:` no es servible por HTTP, que es la causa raíz de AUD-007. El ADR debe decir que el endpoint de lectura (FASE 1) sirve bytes, no la URI.
- Riesgo aceptado: sin backup ni replicación del directorio.

- [x] **Paso 5: `ADR-M6-02` — Anonimización por regex + diccionario como puente**

El javadoc de `AnonimizadorTranscript` dice "ADR-M6-01 pendiente" y describe la estrategia con precisión. Formalizarla:
- Estrategia fail-safe explícita: _ante la duda, enmascarar de más_ — un falso positivo tapa una palabra, un falso negativo filtra un dato de un menor (Artículo II manda).
- Orden de aplicación de los patrones y **por qué ese orden** (pagos antes que teléfonos, DNI antes que teléfonos, email antes que alias).
- Límite conocido: diccionario cerrado de nombres hispanos; un nombre fuera del diccionario y fuera de un patrón de presentación **no se enmascara**.
- **Riesgo aceptado, escrito:** hoy es inofensivo porque no hay transcript (AUD-024). Cuando M6 se active, este componente pasa a ser un control de privacidad de datos de menores y el ADR-M6-01 (NER real) deja de ser opcional.

- [x] **Paso 6: Verificar que los 5 ADR existen y tienen las secciones obligatorias**

Run: `for f in docs/adr/ADR-000-02.md docs/adr/ADR-000-03.md docs/adr/ADR-000-04.md docs/adr/ADR-M1-03.md docs/adr/ADR-M6-02.md; do echo "== $f"; grep -c '^## ' $f; done`
Expected: cada uno con al menos 5 secciones `##`.

Run: `grep -L 'Estado' docs/adr/ADR-000-0*.md docs/adr/ADR-M1-03.md docs/adr/ADR-M6-02.md`
Expected: salida vacía (todos tienen sección Estado).

- [x] **Paso 7: Actualizar el Registro de Decisiones Técnicas de la Constitución**

En `docs/Constitucion_Tinku.md`, tabla "Registro de Decisiones Técnicas Actuales": agregar la referencia al ADR en las filas que ahora lo tienen (Backend, Scheduler de jobs). **Esto NO es una enmienda** — el propio documento dice que esa tabla se cambia con un ADR normal, sin tocar los Artículos.

> **NO tocar ningún Artículo ni el Historial de Enmiendas.** Si tu diff de `Constitucion_Tinku.md` toca una línea fuera de esa tabla, revertí.

Run: `git diff docs/Constitucion_Tinku.md | grep '^[+-]' | grep -i 'artículo\|articulo\|enmienda'`
Expected: salida vacía.

- [x] **Paso 8: Commit**

```bash
git add docs/adr/ docs/Constitucion_Tinku.md
git commit -m "docs(adr): formalizar retroactivamente 5 decisiones vigentes sin ADR"
```

---

### Task 0.9: Marcadores `FIXME AUD-XXX` en el código que la documentación contradice

**Files:**
- Modify: `backend/src/main/java/com/tinku/aula/LiveKitService.java:70-74`
- Modify: `backend/src/main/java/com/tinku/aula/SesionService.java` (javadoc de `cortar`, `obtenerToken`, `ejecutarKillswitch`, `ramaMenor`)
- Modify: `backend/src/main/java/com/tinku/admin/web/CredencialColaResponse.java:14`
- Modify: `backend/src/main/java/com/tinku/identidad/dto/TutorPerfilResponse.java:12`
- Modify: `frontend/src/middleware.ts:5-9`
- Modify: `frontend/src/lib/api.ts:166-173`

> **El único cambio permitido en esta tarea es agregar líneas de comentario.** Ninguna línea de código ejecutable se toca. El objetivo es que nadie vuelva a leer un javadoc que afirma lo contrario de lo que el código hace (informe §4.2).
>
> **No reescribir el javadoc para que describa el bug.** El texto actual describe el comportamiento *deseado*, que FASE 1 va a implementar. Se le agrega el marcador arriba; el texto queda.

- [x] **Paso 1: `LiveKitService.java` — antes del javadoc de `generarTokenParticipante`**

```java
// FIXME AUD-002 (auditoría 2026-09-21): el javadoc de abajo afirma que el token no permite
// abrir otra sala, pero la línea del claim emite roomCreate=true y roomAdmin=true. El
// comportamiento descrito acá es el DESEADO; el real es el opuesto. Se corrige en FASE 1.
```

- [x] **Paso 2: `SesionService.java` — antes de `cortar(...)`**

```java
// FIXME AUD-001 (auditoría 2026-09-21): este método NO cierra la sala de LiveKit. Solo
// persiste el estado. La sala sigue viva y los tokens emitidos siguen siendo válidos hasta
// su TTL. Spec_M3 US-6 exige "la sesión se corta para ambos". Se corrige en FASE 1.
```

- [x] **Paso 3: `SesionService.java` — antes de `obtenerToken(...)`**

```java
// FIXME AUD-001/AUD-003 (auditoría 2026-09-21): (a) no hay guard de estado — devuelve token
// para una sesión ya cortada por kill-switch; (b) la identidad del participante es el DNI,
// que LiveKit difunde al otro participante y el frontend renderiza en pantalla. Con menores
// esto es un dato sensible bajo Ley 25.326. Se corrige en FASE 1.
```

- [x] **Paso 4: `SesionService.java` — antes de `ejecutarKillswitch(...)`**

```java
// FIXME AUD-005 (auditoría 2026-09-21): el único control es esParticipante(). Cualquiera de
// los tres puede disparar el corte contra otro, sin evidencia y sin límite de tasa; en rama
// menor eso produce reembolso total + suspensión del Tutor. Ver FASE 1 y el anexo pendiente
// a ADR-M3-01 (modelo de amenaza del clasificador on-device).
```

- [x] **Paso 5: `SesionService.java` — antes de `ramaMenor(...)`**

```java
// FIXME AUD-006 (auditoría 2026-09-21): suspende siempre a reserva.getTutor(), ignorando
// detectadoId. Cuando el detectado es el menor (caso previsto en Spec_M3 US-6), el Tutor
// queda suspendido y AlertaSeguridadService.resolver() nunca lo reactiva, porque resuelve
// mirando alerta.getDetectadoId(). Comparar con confirmarRamaAdultos(), que sí usa el
// detectado. Se corrige en FASE 1.
```

- [x] **Paso 6: `CredencialColaResponse.java`**

```java
// FIXME AUD-007 (auditoría 2026-09-21): el javadoc dice que "la revisión visual del archivo
// es del frontend interno". Ese frontend interno NO existe, y tampoco existe ningún endpoint
// que sirva el archivo. Hoy la Credencial Académica —único mecanismo de confianza vigente
// tras ADR-M1-02— se aprueba a ciegas. Se corrige en FASE 1.
```

- [x] **Paso 7: `TutorPerfilResponse.java`**

```java
// FIXME AUD-003 (auditoría 2026-09-21): este DTO no expone el DNI, correcto. Pero el DNI SÍ
// sale del sistema por otra vía: SesionService.obtenerToken() lo usa como identity de LiveKit.
```

- [x] **Paso 8: `frontend/src/middleware.ts`**

```ts
// FIXME AUD-016 (auditoría 2026-09-21): esto NO es una protección de seguridad. Solo verifica
// que la cookie exista: no valida firma ni expiración. Cualquiera puede setear
// document.cookie = "tinku_jwt=x" y cargar el shell de /admin. La autorización real la hace
// el backend en cada request. Se decide en FASE 3: verificar la firma, o renombrar esto
// honestamente como redirección de UX.
```

- [x] **Paso 9: `frontend/src/lib/api.ts` — en `getCatalogos`**

```ts
// FIXME AUD-026 (auditoría 2026-09-21): el catch también atrapa errores de red (un
// TypeError de fetch no es ApiError), así que con el backend caído el usuario ve un
// catálogo falso que parece real. Se acota en FASE 3.
```

- [x] **Paso 10: Verificar que el diff es SOLO comentarios**

Run: `git diff --stat`
Run: `git diff -- '*.java' '*.ts' | grep '^+' | grep -v '^+++' | grep -vE '^\+\s*(//|\*|/\*)'`
Expected: **salida vacía.** Cada línea agregada tiene que ser un comentario. Si aparece algo, sacalo.

Run: `git diff -- '*.java' '*.ts' | grep '^-' | grep -v '^---'`
Expected: **salida vacía.** No se borra ninguna línea existente.

- [x] **Paso 11: Compilar y correr la suite completa**

Run: `cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test`
Expected: `Tests run: 383, Failures: 0, Errors: 0, Skipped: 0` — **exactamente el mismo número.** Un comentario no cambia el conteo. Si cambió, tocaste código.

Run: `cd frontend && bun run lint`
Expected: sin errores.

- [x] **Paso 12: Commit**

```bash
git add backend/src/main/java frontend/src
git commit -m "docs(codigo): marcadores FIXME AUD-* donde el javadoc contradice al codigo"
```

---

### Task 0.10: Actualizar `AGENTS.md`

**Files:**
- Modify: `AGENTS.md`

> `AGENTS.md` se lee automáticamente en cada sesión. Es el lugar donde tiene que vivir lo que ningún agente puede pasar por alto. **No reescribir las secciones existentes** — agregar dos.

- [x] **Paso 1: Agregar el requisito de Java 21 en la sección 5 (Testing)**

```markdown
- **La suite corre SOLO con JDK 21.** El `maven-enforcer-plugin` exige `[21,22)`.
  Comando canónico: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test`
  desde `backend/`. Con otra JVM el build falla en el enforcer y **no corre ni un test** —
  y si pipeás la salida, el exit code puede dar 0 y parecer éxito. Siempre leer la línea
  `Tests run:` antes de afirmar que algo pasa.
- Baseline al 2026-09-21: **383 tests, 0 failures, 0 errors, 0 skipped**. Si tu cambio
  baja ese número, borraste un test.
```

- [x] **Paso 2: Agregar sección 9 — Auditoría vigente**

```markdown
## 9. Auditoría vigente (2026-09-21)

Hay una auditoría técnica independiente con 36 hallazgos (7 CRÍTICA, 1 ya cerrado) — el estado
vigente, fila por fila, está en `REGISTRO_FINDINGS.md`; no repitas estos números de memoria en
otro documento, citá esa tabla. Antes de trabajar sobre M3 (Aula), M5 (Pagos), M9 (Seguridad) o
el registro de identidad, leé el finding que corresponda.

- Informe: `docs/auditoria/2026-09-21-auditoria-independiente.md`
- Estado por finding: `docs/auditoria/REGISTRO_FINDINGS.md` — **se actualiza en el mismo
  commit que cierra un finding.**
- Plan de remediación: `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`

**Reglas que esta auditoría agrega:**
- Un finding se cierra con un commit + un test de regresión que falla ANTES del fix. No se
  cierra "por análisis".
- Si encontrás un `FIXME AUD-XXX` en el código, ese comentario describe un problema conocido
  y su fase de corrección. No lo borres sin cerrar el finding.
- Un javadoc puede estar describiendo el comportamiento DESEADO y no el real. Verificá contra
  el código antes de confiar en un comentario.
```

- [x] **Paso 3: Actualizar la sección 8 (Gobernanza)**

Agregar que `docs/Tasks_Tinku_Implementacion.md` y `docs/Tasks_Tinku_Chunks.md` se actualizan **juntos o no se actualizan** — la divergencia entre los dos fue AUD-030.

- [x] **Paso 4: Commit**

```bash
git add AGENTS.md
git commit -m "docs(agents): requisito de JDK 21 y seccion de auditoria vigente"
```

---

### Task 0.11: Verificación de salida de FASE 0

**Files:** ninguno — es un gate.

> No se abre FASE 1 hasta que todos estos checks pasen. Si alguno falla, arreglalo en esta fase.

- [x] **Paso 1: La suite sigue idéntica**

Run: `cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test 2>&1 | grep 'Tests run:.*Failures' | tail -1`
Expected: `Tests run: 383, Failures: 0, Errors: 0, Skipped: 0`

- [x] **Paso 2: FASE 0 no tocó lógica**

Run: `git diff main...HEAD --stat -- backend/src/main/java frontend/src`
Run: `git diff main...HEAD -- backend/src/main/java frontend/src | grep '^[+-]' | grep -v '^[+-][+-]' | grep -vE '^[+-]\s*(//|\*|/\*)'`
Expected: **salida vacía.**

- [x] **Paso 3: No se editó ninguna migración existente**

Run: `git diff main...HEAD --name-only -- backend/src/main/resources/db/migration/`
Expected: **salida vacía.** (Guardrail A1.)

- [x] **Paso 4: No se tocó la Tabla de Tiempos ni los Artículos de la Constitución**

Run: `git diff main...HEAD --name-only -- docs/Tabla_Tiempos_Tinku.md`
Expected: salida vacía. (Guardrail A3.)

Run: `git diff main...HEAD -- docs/Constitucion_Tinku.md | grep -iE '^[+-].*(artículo|enmienda)'`
Expected: salida vacía. (Guardrail A4.)

- [x] **Paso 5: Los 36 findings están registrados, y solo AUD-030 cerró en esta fase**

Run: `grep -c '^| AUD-' docs/auditoria/REGISTRO_FINDINGS.md`
Expected: `36`

Run: `grep -c 'CERRADO' docs/auditoria/REGISTRO_FINDINGS.md`
Expected: `1`

**AUD-030 (drift de tracking) es el ÚNICO finding que FASE 0 puede cerrar**, porque es el único cuyo fix ES documentación. Marcalo `CERRADO` con el hash del commit de la Task 0.4 y `Test: n/a (documentación)`.

Run: `grep '^| AUD-030' docs/auditoria/REGISTRO_FINDINGS.md`
Expected: la fila en estado `CERRADO` con un hash de commit en la columna Commit.

> Si cerraste cualquier otro finding en FASE 0, lo cerraste sin test de regresión (guardrail A9). Revertí el estado a `ABIERTO`.

- [x] **Paso 6: Frontend limpio**

Run: `cd frontend && bun run lint`
Expected: sin errores.

- [ ] **Paso 7: Resumen de fase**

Escribir en el PR/resumen: qué tareas de `Tasks_Tinku_Implementacion.md` se tildaron y con qué evidencia, cuáles quedaron abiertas y por qué, los 5 ADR creados, y el conteo de tests antes/después (debe ser el mismo).

---

# FASE 1 — P0 Seguridad

**Branch:** `aud/fase1-p0-seguridad`
**Precondición:** FASE 0 cerrada, `REGISTRO_FINDINGS.md` existe.
**Criterio de salida:** los 11 findings de la fase en `CERRADO`, cada uno con su test de regresión, y la suite en **≥ 383 + los tests nuevos**, 0 failures.

> **Esta fase toca código de seguridad. Tres reglas específicas:**
>
> 1. **Un commit por finding.** Nada de "fix de seguridad varios". El diff de cada uno tiene que ser revisable solo.
> 2. **El test de regresión se escribe primero y se lo ve fallar.** Si escribís el fix primero, no tenés prueba de que el test cubra algo (guardrail B2).
> 3. **Al terminar la fase, correr `/security-review` sobre el diff completo de la rama** antes de mergear.
>
> **Orden sugerido:** 1.1 y 1.2 primero (son de 1 y 2 líneas, cierran los dos findings más graves de escalada). Después el resto. **1.9 es la más grande** y conviene dejarla para cuando el resto esté verde.

---

### Task 1.1: Quitar `roomCreate` y `roomAdmin` del token de participante — AUD-002

**Files:**
- Modify: `backend/src/main/java/com/tinku/aula/LiveKitService.java:84`
- Test: `backend/src/test/java/com/tinku/aula/LiveKitServiceTest.java:81-91`

**Interfaces:**
- Consumes: `VideoClaim(String room, boolean roomJoin, boolean roomCreate, boolean roomAdmin)` — ya existe, línea 148.
- Produces: nada nuevo. El contrato del método no cambia.

**Cambio concreto:** `new VideoClaim(nombreSala, true, true, true)` → `new VideoClaim(nombreSala, true, false, false)`.

**Test obligatorio (en `LiveKitServiceTest`, extendiendo el test existente `generarTokenParticipante...`):** assert explícito de que `video.get("roomCreate")` es `false` y `video.get("roomAdmin")` es `false`. Debe fallar antes del cambio.

**NO tocar:** el token de servidor (`tokenServidor()`, línea 125) — `roomCreate: true` ahí es correcto, lo necesita `CreateRoom`. Y el test `crearSalaPosteaAlTwirp...` que lo verifica.

**Al cerrar:** quitar el `FIXME AUD-002` de la Task 0.9 y corregir el javadoc, que ahora sí describe el comportamiento real.

---

### Task 1.2: Dejar de loguear el token de reset — AUD-008

**Files:**
- Modify: `backend/src/main/java/com/tinku/identidad/port/NotificadorResetPasswordLog.java:26-28`

**Cambio concreto:** el log deja de incluir `tokenPlano` y `usuario.getDni()`. Queda solo `usuario.getId()`.

**Decisión de alcance que hay que tomar acá** (y escribirla en el javadoc): sin el token en el log, el flujo de recuperación queda **inoperable** hasta que exista un canal real (FASE 2, AUD-014). Eso es correcto y es lo que fail-closed significa: mejor un flujo que no funciona que un flujo que regala cuentas.

**Test obligatorio:** unit test que capture el `Logger` (Logback `ListAppender`) y verifique que la salida **no contiene** el token plano ni el DNI. Debe fallar antes.

**NO tocar:** `PasswordResetService`. El servicio está bien hecho (token opaco, SHA-256, un solo uso, TTL 1h, respuesta idéntica exista o no el DNI). El problema es solo el notificador.

---

### Task 1.3: Identity de LiveKit = UUID, no DNI — AUD-003

**Files:**
- Modify: `backend/src/main/java/com/tinku/aula/SesionService.java:285-287`
- Modify: `backend/src/main/java/com/tinku/aula/LiveKitService.java:75-87` (agregar claim `name`)
- Modify: `frontend/src/app/aula/[id]/page.tsx` (`etiquetaParticipante`)
- Test: `backend/src/test/java/com/tinku/aula/web/SesionesIntegracionTest.java`

**Cambio concreto:**
1. `generarTokenParticipante(usuario.getId().toString(), ...)` en vez de `usuario.getDni()`.
2. Agregar al token un claim `name` con `usuario.getNombre()` (solo el nombre de pila, **no** apellido — minimización, Artículo V).
3. El frontend usa ese `name` para la etiqueta y **deja de renderizar el identity**.

**Por qué el webhook no se rompe:** `LiveKitWebhookService.mismaPersona()` (línea 81) ya acepta ambos: `usuario.getId().toString().equals(identity) || usuario.getDni().equals(identity)`. **No borrar la rama del DNI en esta tarea** — hay sesiones en curso con tokens viejos. Anotarla como limpieza de FASE 3.

**Test obligatorio:** (a) el token emitido tiene `sub` = UUID y no el DNI; (b) el webhook sigue registrando el join correctamente con identity = UUID. Ambos deben fallar antes del cambio (el primero) o pasar ya (el segundo, es regresión).

**NO tocar en esta tarea:** el `sub` del JWT propio de Tinku (`JwtUtil:34`). Cambiarlo rompe `UsuarioDetailsService`, `AdminModeracionGate`, `UsuarioActual` y todos los tests que generan tokens. Es FASE 3 (AUD-027), junto con `credentials_version`.

---

### Task 1.4: Cerrar la sala de LiveKit en el corte + guard en `/token` — AUD-001

**Files:**
- Modify: `backend/src/main/java/com/tinku/aula/LiveKitService.java` (métodos nuevos)
- Modify: `backend/src/main/java/com/tinku/aula/SesionService.java` (`cortar`, `obtenerToken`)
- Test: `backend/src/test/java/com/tinku/aula/LiveKitServiceTest.java`, `KillswitchIntegracionTest.java`

**Cambio concreto:**
1. `LiveKitService.eliminarSala(String nombreSala)` → `POST /twirp/livekit.RoomService/DeleteRoom` con el token de servidor. Mismo patrón que `crearSala`: `RestClient`, `onStatus` que lanza, y `RestClientException` traducida.
2. `LiveKitService.expulsarParticipante(String sala, String identity)` → `POST /twirp/livekit.RoomService/RemoveParticipant`.
3. `SesionService.cortar()` los invoca **antes** de persistir el estado.
4. `SesionService.obtenerToken()` rechaza sesiones en estado `finalizada` o `interrumpida` con una excepción nueva → 422.

**Decisión de diseño (D4, 2026-09-22):** si LiveKit falla al cerrar la sala, **NO** se aborta la transacción del corte. `SesionService.cortar()` intenta `RemoveParticipant` + `DeleteRoom`; si falla, persiste igual el estado (sesión cortada, Alerta, escrow en pausa por D3, evento) y agenda un job de Quartz que reintenta el cierre de sala con el mismo patrón de backoff de `LiberacionEscrowService` (5/15/60min, ya en `Tabla_Tiempos_Tinku.md`).

**Fundamento:** esto es una **desviación consciente** del estilo fail-closed que el resto del módulo usa (`programarSiFalta` aborta la confirmación de la Reserva si Quartz falla) y hay que justificarla explícitamente en el ADR. La razón: LiveKit separa plano de control y plano de medios — su API puede estar caída mientras la videollamada sigue viva. Fail-closed acá (abortar el corte si LiveKit no contesta) produce el resultado opuesto al deseado: el menor queda expuesto y la sesión no se corta en ningún lado, ni siquiera en la BD.

**Riesgo aceptado:** entre el disparo y el reintento exitoso, la sala sigue abierta. Se mitiga con la desconexión local del frontend, que no es garantía (un cliente modificado la ignora). El cierre pasa de "nunca" a "apenas LiveKit conteste".

**Test obligatorio:** (a) unit sobre `LiveKitService` con stub HTTP local verificando que se postea a `DeleteRoom` con el token de servidor — mismo patrón que `crearSalaPosteaAlTwirp`; (b) integración en `KillswitchIntegracionTest`: tras el kill-switch rama menor, `POST /api/sesiones/{id}/token` responde 422.

**NO tocar:** el orden de emisión de eventos. `cortar()` no emite evento a propósito (cada rama emite el suyo con el nombre exacto). Guardrail A2.

---

### Task 1.5: `ramaMenor` suspende al detectado — AUD-006

**Files:**
- Modify: `backend/src/main/java/com/tinku/aula/SesionService.java:474-490`
- Test: `backend/src/test/java/com/tinku/aula/web/KillswitchIntegracionTest.java`

**Cambio concreto:** suspender al usuario de `detectadoId`, no a `reserva.getTutor()`. Comparar con `confirmarRamaAdultos()` (línea 545), que ya lo hace bien — usar ese mismo código.

**Decisión de producto (D2, 2026-09-22):** solo se suspende al detectado, igual que la rama adultos. `Spec_M3:51` exige el corte incondicional, pero era mudo sobre a quién se suspende — hay que escribirlo en `Spec_M3` US-6.

**Esto resuelve, gratis, un bug aparte:** `AlertaSeguridadService.resolver()` ya opera sobre `alerta.getDetectadoId()`, y con esta decisión ese id pasa a coincidir siempre con quien fue suspendido. Eso cierra por sí solo el bug de "el Tutor queda suspendido para siempre" cuando el detectado era el menor — **no hay que tocar M9**.

**Riesgo aceptado a declarar en el ADR:** cuando el detectado es el menor, el Tutor NO queda en suspensión preventiva, así que durante la ventana de 12hs hasta la resolución del Admin puede tomar otra sesión con otro menor. Se acepta a cambio de no penalizar a un Tutor por una detección que no generó, y porque la revisión humana ocurre igual (la Alerta se crea siempre y entra a la cola de moderación).

**Test obligatorio:** el caso que hoy no existe — kill-switch rama menor con `detectadoId = menorId`, y verificar (a) a quién se suspende y (b) que `AlertaSeguridadService.resolver(REACTIVAR)` revierte efectivamente esa suspensión. Los 18 tests actuales usan siempre `detectadoId = tutor`.

---

### Task 1.6: Perfil `dev` fuera del artefacto + fail-fast de OCR y JWT — AUD-004 + AUD-034

**Files:**
- Modify: `backend/src/main/resources/application.yml:11-12`
- Create: `backend/src/main/resources/application-prod.yml`
- Create: `backend/src/main/java/com/tinku/config/ArranqueSeguroValidator.java`
- Modify: `backend/Dockerfile`, `docker-compose.yml`
- Test: nuevo test de contexto

**Cambio concreto:**
1. Quitar `spring.profiles.active: dev` de `application.yml`. El perfil pasa a venir del entorno.
2. `application-dev.yml` nuevo con lo que hoy es implícito de dev (datasource local incluida — hoy `spring.datasource.url` está hardcodeada a `localhost:5432` en el yml base).
3. `application-prod.yml` con **solo placeholders de variables de entorno**, ningún valor (guardrail A13).
4. `ArranqueSeguroValidator` (`@Component`, `@PostConstruct` o `ApplicationRunner`) que aborta el arranque si:
   - `OcrService` inyectado es `StubOcrService` y el perfil activo no incluye `dev` ni `test`;
   - `tinku.jwt.secret` es igual al placeholder `CAMBIAR_EN_TODOS_LOS_AMBIENTES_AL_AGREGAR_UN_SECRET_REAL` y el perfil no es `dev`/`test`.
5. `Dockerfile` y `docker-compose.yml`: el perfil se pasa explícito.

**Test obligatorio:** test de contexto que levanta con perfil `prod` + `StubOcrService` forzado y verifica que el arranque falla con mensaje claro. Y otro que verifica que con `dev` levanta normal.

**NO tocar:** los `@Profile` de `StubOcrService` (`{"dev","test"}`) y `TesseractOcrService` (`"!dev & !test"`). Están bien. El problema es el default, no el gating.

**Ojo:** `SaludInfraestructuraService:70` ya consulta `environment.acceptsProfiles("prod")` contra un perfil que hoy no existe. Al crear el perfil, ese `isTestMode` empieza a devolver `false` en prod — verificar que `AdminPanelIntegracionTest` sigue verde.

---

### Task 1.7: Exigir participación y rechazar auto-denuncia — AUD-011

**Files:**
- Modify: `backend/src/main/java/com/tinku/seguridad/service/DenunciaService.java:100-133`
- Create: excepción nueva en `com.tinku.seguridad`
- Modify: `backend/src/main/java/com/tinku/seguridad/web/SeguridadExceptionHandler.java`
- Test: `backend/src/test/java/com/tinku/seguridad/DenunciasModeracionIntegracionTest.java`

**Cambio concreto:** cuando la denuncia trae `sesionId`, verificar que el denunciante es participante de la Reserva de esa Sesión (tutor, beneficiario o pagador) **y** que el denunciado también lo es. Rechazar `denunciadoId == denunciante.getId()`.

**Cuidado con el caso legítimo del Artículo II:** el Adulto Responsable denuncia **en nombre de** su menor. Si el menor es el beneficiario y el AR es el pagador, el AR **sí** es participante — el `esParticipante` de `SesionService:615` ya cubre exactamente ese caso. Reutilizar ese criterio, no escribir uno nuevo.

**Decisión de alcance (D5, 2026-09-22):** la denuncia **con** `sesionId` exige participación de denunciante y denunciado (lo implementado arriba). La denuncia **de perfil** (sin `sesionId`) queda abierta a cualquier usuario no-menor, sin restricción de relación.

**Dato verificado que acota el riesgo:** `presentar()` solo emite `DenunciaRegistradaEvent` si `reservaId != null && tieneEscrowActivo(reservaId)`, y el `reservaId` sale del `sesionId`. Es decir, **una denuncia de perfil no congela el escrow de nadie** — el vector financiero de AUD-011 es exclusivo de las denuncias con sesión, que es justo lo que esta decisión cierra.

**Riesgo aceptado:** la denuncia de perfil sigue pudiendo spamear la cola de moderación y arrancar el reloj de 48hs de descargo sobre alguien inocente. Se mitiga con el rate limiting de FASE 2 (D8), no con un chequeo de vínculo.

**Hueco del Spec a cerrar:** `Spec_M9` NO define la denuncia de perfil en ningún lado — FR-SEC-001 solo dice quién puede denunciar. El concepto vive solo en el código y en el frontend. Hay que escribirlo.

**Test obligatorio:** tres casos — (a) un tercero no participante denuncia una sesión ajena → 403; (b) verificar que el escrow de esa sesión **NO** quedó en `PAUSADO_DENUNCIA`; (c) el AR denunciando la sesión de su menor sigue funcionando (regresión).

---

### Task 1.8: Guard de sanción vigente antes de reactivar — AUD-013

**Files:**
- Modify: `backend/src/main/java/com/tinku/seguridad/service/AlertaSeguridadService.java:103-106`
- Modify: `backend/src/main/java/com/tinku/identidad/service/CredencialService.java:96-107`
- Test: `DenunciasModeracionIntegracionTest.java`, `IdentidadFlujosIntegracionTest.java`

**Cambio concreto:** antes de `setEstadoCuenta(ACTIVA)` o `setActivoParaMatching(true)`, consultar si el usuario tiene una `Sancion` vigente: `SUSPENSION_DEFINITIVA`, `BANEO_AUTORIDADES`, o `SUSPENSION_TEMPORAL` con `vigenteHasta > now()`. Si la hay, **no tocar** el estado de cuenta ni el flag.

**Problema de acoplamiento a resolver bien:** `CredencialService` vive en `identidad` y `SancionRepository` en `seguridad`. **No importar el repositorio directo** — eso empeora AUD-019. Definir un puerto en `identidad.port` (ej. `VerificadorSancionVigente`) implementado en `seguridad`, con el mismo patrón que `VerificadorReservasFuturas` ya usa.

**Test obligatorio:** (a) Tutor con `SUSPENSION_DEFINITIVA` + Alerta pendiente → resolver `REACTIVAR` → la cuenta sigue `SUSPENDIDA`; (b) Tutor suspendido definitivamente sube credencial → Admin aprueba → `activoParaMatching` sigue `false`.

**Incluir acá también AUD-033** (mismo archivo, mismo commit lógico): guard `if (c.getEstado() != PENDIENTE) throw` en `marcarAprobada` y `marcarRechazada`. Hoy se puede aprobar una credencial ya rechazada, y re-rechazar el intento 3 duplica el backoff.

---

### Task 1.9: Endpoint de visualización de la Credencial Académica — AUD-007

**Files:**
- Modify: `backend/src/main/java/com/tinku/identidad/port/Almacenamiento.java` (método de lectura)
- Modify: `backend/src/main/java/com/tinku/identidad/port/AlmacenamientoLocal.java`
- Modify: `backend/src/main/java/com/tinku/admin/web/ColasModeracionController.java`
- Modify: `backend/src/main/java/com/tinku/identidad/web/TutorController.java:108-117` (validación de subida)
- Modify: `frontend/src/components/admin/ColaCredenciales.tsx`
- Test: `AdminPanelIntegracionTest.java`

**Esta es la tarea más grande de FASE 1.** Es la que restituye el control de confianza del Artículo I v2.2.

**Cambio concreto:**
1. `Almacenamiento.leer(String url)` → `byte[]`, con validación de que la ruta resuelta **cae dentro del directorio configurado** (defensa contra path traversal — hoy `guardar` sanea el nombre, pero `leer` recibiría una URL de la base).
2. `GET /api/admin/moderacion/credenciales/{id}/archivo` gateado por `gate.requiereModeracion(authentication)`, devolviendo el byte stream con el `Content-Type` correcto. **Queda bajo `/api/admin/**`, así que el `AuditoriaInterceptor` lo registra automáticamente** — verificar que la fila de auditoría se crea.
3. Validación en la subida (`TutorController:115`): content-type en allowlist (`image/jpeg`, `image/png`, `application/pdf`) y tamaño máximo. Configurar `spring.servlet.multipart.max-file-size` explícitamente en vez de depender del default implícito de 1MB.
4. El frontend agrega el visor en la cola.

**NO exponer `archivoUrl` en `CredencialColaResponse`.** La decisión de no filtrar la ruta del filesystem es correcta (minimización). El endpoint sirve **bytes**, no la URI. Ese es exactamente el punto que `ADR-M1-03` (Task 0.8) declara.

**Test obligatorio:** (a) Admin de Moderación descarga el archivo → 200 + bytes correctos; (b) Admin de Soporte Financiero → 403; (c) usuario común → 403; (d) el `AuditoriaInterceptor` registró la lectura; (e) subida de un content-type fuera de la allowlist → 422.

---

### Task 1.10: Acotar el disparo del kill-switch — AUD-005

**Files:**
- Modify: `backend/src/main/java/com/tinku/aula/SesionService.java:434-466`
- Create: `docs/adr/ADR-M3-02.md` (anexo a ADR-M3-01: modelo de amenaza del clasificador on-device)
- Test: `KillswitchIntegracionTest.java`

> **Decisión de producto (D3, 2026-09-22): opción 2.** El corte sigue siendo inmediato e incondicional (Artículo II intacto). Lo que se desacopla es la plata: el escrow pasa a `PAUSADO_DENUNCIA` en vez de reembolsarse automáticamente. M9 decide el destino del dinero al resolver la Alerta.
>
> **Qué elimina:** el premio instantáneo. Hoy se puede tomar 55 de 60 minutos y disparar el kill-switch para cobrar el 100% — `reembolsarSiRetenida` no mira el tiempo transcurrido.
>
> **Escribir `ADR-M3-02` primero, con la decisión y el riesgo aceptado. Recién después implementar.** Eso no cambia: el ADR es más valioso para la defensa que el parche (informe §7.1, pregunta 7).

**Implementación concreta (D3):** `EscrowService.onSesionKillswitchMenor` y `onSesionKillswitchAdultos` dejan de llamar a `reembolsarSiRetenida` y pasan la transacción al estado `PAUSADO_DENUNCIA` (ya existe, ya está en el CHECK de V11 y ya está testeado por la vía de `denuncia.registrada`). `AlertaSeguridadService.resolver()` pasa a resolver también el escrow: `REACTIVAR` (falso positivo) → reembolso total al Estudiante; `SANCIONAR` → reembolso total al Estudiante. **Ojo: en ambos casos el Estudiante cobra — lo que cambia es que ya no es automático ni instantáneo.** `SesionService.cortar()` NO cambia — el Artículo II queda intacto.

**NO cambiar:** que el corte sea inmediato. El Artículo II no se negocia. Lo que se desacopla es la plata, no la protección.

**Test obligatorio:** (a) tras el kill-switch, la transacción queda `PAUSADO_DENUNCIA` y **no** reembolsada; (b) al resolver la Alerta, el reembolso se ejecuta; (c) el corte sigue siendo inmediato.

---

### Task 1.11: Unicidad en `pagos.transacciones` — AUD-010

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__m5_unicidad_transacciones.sql`
- Modify: `backend/src/main/java/com/tinku/pagos/service/EscrowService.java:110-144`
- Test: `backend/src/test/java/com/tinku/pagos/web/PagosWebhookIntegracionTest.java`

**Cambio concreto:**
1. Migración **V24** (nunca editar V11, guardrail A1): `CREATE UNIQUE INDEX uq_transacciones_mp_payment ON pagos.transacciones(mp_payment_id);` y `CREATE UNIQUE INDEX uq_transacciones_reserva ON pagos.transacciones(reserva_id);`
2. En `procesarPagoAprobado`, capturar `DataIntegrityViolationException` sobre esos índices y tratarla como **no-op idempotente** (el otro hilo ganó), devolviendo 2xx. **Nunca un 5xx**, porque MercadoPago reintenta y se entra en loop.

**ANTES de escribir la migración, verificar que no hay datos que la violen:**
Run: `SELECT mp_payment_id, count(*) FROM pagos.transacciones GROUP BY 1 HAVING count(*)>1;` y lo mismo por `reserva_id`.
Si hay duplicados en algún entorno, la migración necesita un paso de limpieza previo. **En una base vacía de dev no vas a ver nada — eso no prueba que producción esté limpia.**

**Cuidado con `reembolsarPagoTardio`** (`EscrowService:337-369`): hoy ya está condicionado a `!existsByReservaId`, así que el índice único sobre `reserva_id` no debería romperlo. **Verificarlo con el test existente antes de asumirlo.** El propio javadoc de ese método (líneas 347-359) dice que la idempotencia real "queda pendiente de una migración dedicada" — esta es esa migración.

**Test obligatorio:** dos webhooks concurrentes con el mismo `mpPaymentId` (patrón `CountDownLatch` de `ReservasFlujosIntegracionTest:1110`) → una sola fila, ambas respuestas 2xx.

---

# FASE 2 — P1 Integridad de datos y funcionalidad incompleta

> **Actualización 2026-09-22 — no ejecutes FASE 2, 3 ni 4 desde este documento.** Tras cerrar FASE 1
> se reescribieron como specs autocontenidas, verificadas contra el código actual y con las decisiones
> pendientes explicitadas: `docs/superpowers/specs/remediacion/00-LEEME-opencode.md`. Lo que sigue
> queda como registro histórico del plan original.

**Branch:** `aud/fase2-p1-integridad`
**Precondición:** FASE 1 cerrada y mergeada.
**Criterio de salida:** 13 findings en `CERRADO` o `ACEPTADO` con ADR.

> **Regla de esta fase:** acá hay dos tareas (2.1 y 2.3) que cambian el **modelo de datos** y una (2.2) que agrega una **dependencia de infraestructura**. Las tres necesitan su propio plan detallado antes de ejecutarse. El resto se puede ejecutar directo desde esta especificación.

---

### Task 2.1: Modelo de disponibilidad por bloques de 30' + tarifa por hora — AUD-009 + AUD-020

> **Requiere plan detallado propio.** Es la tarea de mayor riesgo de todo el documento: cambia el modelo entero, no solo el bug puntual. Toca el modelo de datos de `reservas`, el agendamiento de M3, la tarifa de M5-H y el umbral del 50% que decide reembolso vs. liberación.

**Decisión de producto (D6, 2026-09-22) — modelo resultante:**
- El átomo de disponibilidad es de **30 minutos**. El Tutor los posiciona libremente donde puede.
- Una franja 10:00–12:00 son **4 unidades** de 30', no una sesión de 120 min.
- El **Estudiante** elige cuántas unidades **consecutivas** reserva (1h = 2 unidades, o más).
- El Tutor puede **recomendar** una duración, sin imponerla.
- **Tarifa:** precio por hora × fracción. `TarifaTutor.precioSesion` pasa a `precioHora`; una reserva de 30' cuesta la mitad. `precio = precioHora × unidades / 2`, `BigDecimal` con scale 2 y HALF_UP, congelado al crear la Reserva (FR-PAG-013 no cambia).

**Esto resuelve la contradicción raíz** que la auditoría encontró: hoy `FranjaService` + `SesionService` tratan la franja como UNA sesión (la duración agendada es la de la franja completa) mientras `HorariosDisponiblesService` la trata como contenedor de bloques. Los dos no pueden ser ciertos, y de ahí salen AUD-009 y AUD-020.

**Files:** `V25__m4_duracion_reserva.sql` (nueva), `Reserva.java`, `ReservaService.java`, `SesionService.java`, `HorariosDisponiblesService.java`, `TarifaTutor.java`, `PagoService.java` (`actualizarTarifaTutor`), DTO de tarifa, `frontend` (`cuenta/precio`, `DynamicTimeSlotPicker`), `ReservasFlujosIntegracionTest.java`

**Cambio concreto (todos los "Implica" de D6):**
1. **V25**: `ALTER TABLE reservas.reservas ADD COLUMN duracion_minutos INT NOT NULL` (múltiplo de 30), con backfill desde la franja. **NUNCA editar V9** (guardrail A1) — es migración nueva.
2. Reemplazar la `EXCLUDE` de V9 por solapamiento de rangos: `EXCLUDE USING gist (tutor_id WITH =, tstzrange(horario, horario + duracion) WITH &&)`. Ahora es **crítico**: con bloques de 30', dos reservas contiguas del mismo Tutor son el caso NORMAL y hay que distinguirlas del solapamiento real. `btree_gist` ya está instalada por V9.
3. `ReservaService.crearReserva` congela `duracionMinutos` (igual que ya congela `precio`, FR-PAG-013 — es la misma regla y el mismo motivo).
4. `SesionService.programarSesion` usa `reserva.getDuracionMinutos()` en vez de recalcular desde la franja. Esto arregla de paso el `IllegalStateException` → 500 cuando el Tutor borró la franja, y el umbral del 50% de FR-AULA-005 pasa a ser estable.
5. `TarifaTutor`: `precioSesion` → `precioHora`. Toca M5-H, `PagoService.actualizarTarifaTutor`, el DTO y la UI de `cuenta/precio`.
6. `precios_referencia_regional.valor_sugerido` (M5-E) pasa a estar declarado en la misma unidad (por hora). Hoy no declara unidad ninguna.
7. FR-RES-024 (franjas de 30 a 180 min) sigue valiendo como límite del contenedor.

**Lo que VALIDA:** `HorariosDisponiblesService` y el `DynamicTimeSlotPicker` quedan correctos, y el parámetro `duracionMinutos` de T-M4-12 —que la propia tarea marcaba como "no está definido en ningún Spec, confirmar con producto antes de inventar"— por fin tiene fuente: es la selección del Estudiante, en múltiplos de 30.

**Nota de scope:** "el tutor recomienda una hora" es un campo nuevo (duración recomendada, en el perfil del Tutor o por materia) que no está en ningún Spec. El Artículo VI obliga a decidir aparte si entra al alcance del MVP o queda fuera — no está resuelto por D6, hay que decidirlo antes de implementarlo.

**Test obligatorio:** el que hoy no existe — franja 10:00–12:00, reserva A a las 10:00 (2 unidades), reserva B a las 11:00 → la segunda debe dar 409/422. Los 3 tests actuales de FR-RES-007 usan siempre el mismo `horario`.

**NO tocar:** V9. Guardrail A1.

---

### Task 2.2: Rate limiting y bloqueo por intentos — AUD-012

> **Requiere ADR previo** (guardrail A7): agrega una dependencia.

**Files:** `docs/adr/ADR-000-05.md`, filtro nuevo en `com.tinku.config.security`, `SecurityConfig.java`

**Alcance:** los 7 endpoints `permitAll()` de `SecurityConfig:98-107` — `/registro`, `/verificar-dni`, `/login`, `/tutores/registro`, `/tutores/verificar-dni`, `/recuperar-password`, `/resetear-password` — más los dos webhooks (que ya están protegidos por firma, pero no contra inundación).

**Prioridad dentro de la tarea:** `/verificar-dni` primero. Es público, no autenticado, y dispara Tesseract in-process (CPU-bound): es el DoS más barato del sistema.

**Decisión (D8, 2026-09-22): bucket en memoria del proceso.** Sin Redis, sin tabla en Postgres.

**Fundamento:** `ADR-000-04` (ya escrito en FASE 0) formaliza que el sistema corre en una sola instancia (`isClustered: false`), así que un contador compartido no compra nada hoy. Artículo VII: la más simple que cumple el requisito. Y no mete una dependencia de red en el camino crítico del login — si Redis se cayera, el login no se cae con él.

**Modo de falla aceptado:** un reinicio del proceso resetea los contadores. Benigno.

**ADR:** si se implementa a mano con `ConcurrentHashMap` + ventana deslizante, **no hay dependencia nueva y no hace falta ADR**. Si se usa una librería, sí (guardrail A7) — evaluar cuál sale más barato antes de decidir.

**Revisión futura a dejar escrita:** el día que `ADR-000-04` se revise por escalar a N≥2 instancias, este bucket deja de servir junto con el scheduler. Van atados.

**Bloqueo por intentos de login:** reutilizar el patrón que ya existe y funciona en `OcrBackoffService` / `CredencialBackoffService`, no inventar uno nuevo.

**También acá:** política mínima de contraseña. Hoy es `@Size(min = 8)` y nada más, con el DNI como username.

---

### Task 2.3: Puerto `Notificador` + outbox — AUD-014 (y desbloquea AUD-008)

> **Requiere plan detallado propio.** Es funcionalidad nueva, no un fix.

**Files:** `com.tinku.shared.notificacion` (puerto), implementación outbox, `V26__notificaciones_outbox.sql`, llamadores en `SesionService` y `DenunciaService`

**El puerto se define ahora aunque el proveedor no exista.** El ADR del proveedor de email puede esperar; el puerto y los llamadores, no. Implementación inicial: tabla outbox persistida, consultable desde el panel de Admin.

**Decisión de alcance (D2-bis, 2026-09-22):** el Adulto Responsable recibe **aviso inmediato** cuando se dispara el kill-switch, y el **clip recién si M9 resuelve fundado** la Alerta. Si M9 resuelve falso positivo, el AR nunca lo ve.

**Los tres motivos por los que se acotó así:**
1. El buffer de 30s es de una videollamada 1:1: contiene a los DOS participantes. Entregarlo al AR es entregarle video del Tutor a un tercero sin su consentimiento — el AR tiene base legítima sobre la imagen de su hijo, no sobre la del Tutor.
2. La retención de ese clip hoy está justificada contra la Ley 25.326 por UN solo propósito: evidencia para M9 (BR-KS-01/02, 30 días desde la resolución). Enviarlo al AR sería un segundo propósito, y el Artículo V obliga a justificar toda nueva necesidad de retención **antes** de implementarla — acá no está justificado.
3. Si la Alerta se resuelve como falso positivo, ya no hay vuelta atrás: un clip que no debía existir ya estaría en manos de un tercero.

**Implica:**
- El aviso es inmediato e incondicional. No depende de nada nuevo.
- El clip queda reservado a M9 hasta la resolución del Admin; con `REACTIVAR` (falso positivo) el AR nunca lo ve, con `SANCIONAR` se le da acceso.
- No agrega un propósito de retención nuevo: usa el que BR-KS-02 ya justifica.
- Depende de T-M3-06 para que el clip exista siquiera (hoy ningún cliente lo produce).
- Hueco del Spec a cerrar: `Spec_M3` US-6 dice "se notifica inmediatamente al Adulto Responsable" pero no dice QUÉ se le notifica ni qué puede ver después. Hay que escribirlo.

**Mínimo indispensable antes de piloto, en este orden:**
1. Kill-switch rama menor → Adulto Responsable. Es la obligación más importante del sistema hacia la familia (`Spec_M3:50`) y hoy no existe.
2. Denuncia registrada → denunciado (el plazo de descargo de 48hs, FR-SEC-010, corre hoy en silencio).
3. Reset de contraseña (desbloquea el flujo que la Task 1.2 dejó inoperable).

**NO implementar** recordatorios de calificación ni de resumen en esta tarea. Ya son `log.info` y no son urgentes.

**Decisión de ubicación:** el puerto va en `shared`, no en un módulo de dominio — lo consumen M3, M9 y M1. **Cuidado:** `shared` ya tiene los ciclos de AUD-019; no agregar dependencias de `shared` hacia módulos de dominio (ver ADR-000-03).

---

### Task 2.4: Autenticación y pooling en `matching-service` — AUD-015

**Files:** `matching-service/main.py`, `docker-compose.yml`, `MatchingServiceClient.java`, `application.yml`

**Cambio concreto:**
1. Middleware de FastAPI que exige un header con token compartido. Configurable desde `tinku.matching-service.*` en el backend.
2. **Quitar `ports: - "8000:8000"` de `docker-compose.yml`.** El servicio solo se alcanza desde la red interna.
3. `psycopg_pool` en vez de `_conectar()` por operación. `/recompute-embeddings` hoy abre **una conexión nueva por perfil**.
4. `/recompute-embeddings` en una sola transacción, o con commit por lote.
5. Lock en la carga lazy de `_embedder` (AUD-036.7).

**NO agregar:** autenticación por usuario, OAuth, ni nada que exceda un token compartido entre dos procesos propios. Artículo VII.

---

### Task 2.5: Manejar `participant_left` y `room_finished` — AUD-029

**Files:** `LiveKitWebhookController.java`, `LiveKitWebhookService.java`, `V27__m3_desconexion.sql`, `SesionService.java`

**Cambio concreto:** registrar `ultima_desconexion_at` en `sesiones_aprendizaje` y calcular la duración efectiva contra ese timestamp en vez de `Instant.now()` del job de corte.

**El bug monetario concreto que esto arregla:** ambos se desconectan a los 5 minutos de una sesión de 120. Hoy el job corre a fin+5 y calcula `10:00 → 12:05 = 125 min` sobre 120 agendados → **no** es corte antes del 50% → `sesion.finalizada` → se libera el escrow al Tutor por una sesión de 5 minutos.

**También acá:** implementar `finalizada_anticipada` por desconexión (`Spec_M3:72`), que hoy solo se asigna en `ejecutarNoShow`.

**NO tocar:** los nombres de los eventos emitidos. Guardrail A2.

---

### Task 2.6: Baja de menor — AUD-017

> **Decisión (D7, 2026-09-22): anonimización.** La fila sobrevive, los datos personales se borran. Con transacciones financieras de por medio (`reservas`, `pagos.transacciones`) y con historial de sanciones de M9, la anonimización conserva integridad contable y de auditoría y satisface el derecho de supresión de la Ley 25.326.

**Files:** `docs/adr/ADR-M1-04.md`, `UsuarioService.java:277-299`, migración si hace falta, `UsuarioServiceDarDeBajaTest` + test de integración nuevo

**Implementación concreta:**
- `UsuarioService.darDeBajaMenor()` deja de hacer `usuarioRepository.delete(menor)`. En su lugar reemplaza `nombre`, `apellido`, `dni`, `fechaNacimiento` y `email` por valores anónimos, y marca la fila como dada de baja (columna nueva o `estado_cuenta` nuevo valor).
- El `dni` anónimo tiene que seguir cumpliendo el **UNIQUE de V2** — usar algo derivado del `id`, no un valor fijo ni un random que pueda colisionar.
- La cuenta no puede volver a loguearse: `UsuarioDetailsService` ya rechaza lo que no está `ACTIVA`, así que alcanza con el estado.
- Se conserva la integridad referencial de `reservas`, `solicitudes_sesion`, `denuncias`, `sanciones`, `calificaciones` y `pasarela_estado` — hoy cualquiera de esas FK hace fallar el `DELETE` con un 500 para todo menor que tuvo una reserva.
- `autorizaciones_tutor` y `consentimientos_menor` se siguen borrando (ya se borran hoy).

**Fundamento legal:** la Ley 25.326 da derecho de supresión **con excepciones donde la retención es legalmente exigible**. Acá hay transacciones de MercadoPago y hay historial de sanciones de M9: si ese menor estuvo en un incidente de seguridad, borrar la evidencia no es defendible. Requiere `ADR-M1-04` con esta justificación.

**FKs que hoy no se limpian y hacen fallar el `DELETE`:** `reservas.reservas` (beneficiario_id, pagador_id, tutor_id), `reservas.solicitudes_sesion.menor_id`, `seguridad.denuncias`, `seguridad.sanciones`, `reputacion.calificaciones.autor_id`, `pagos.pasarela_estado.updated_by`.

**Test obligatorio:** de **integración** con Testcontainers sobre un menor con historial real de reservas. El test actual (`UsuarioServiceDarDeBajaTest`) es unitario con mocks y por eso nunca ejecutó el DELETE contra el esquema — por eso el bug sobrevivió.

---

### Task 2.7: Acotar el Modo Bypass — AUD-018

**Files:** `PasarelaService.java`, `ColasFinancieroController.java`, `docs/adr/ADR-M5-01.md` (extender)

**Cambio concreto:** restringir el toggle a perfiles no productivos, **o** darle TTL con un job de Quartz que lo reactive, **o** exigir doble confirmación. Mínimo indispensable: alerta al activarlo y banner persistente en el panel.

**Extender ADR-M5-01** con el ámbito y el control compensatorio. El ADR documenta bien la decisión pero no acota el alcance ni pone expiración.

---

### Task 2.8: Guards de estado en credenciales — AUD-033

Ya incluida en la Task 1.8. **Verificar que quedó cerrada ahí** y marcarla en el registro.

---

### Task 2.9: CI de Python + smoke E2E real — AUD-031

**Files:** `.github/workflows/ci-matching.yml` (nuevo), `.github/workflows/ci-e2e.yml` (nuevo)

**Cambio concreto:**
1. Workflow para `matching-service/**` que corra `pytest`. Hoy `test_main.py` no corre en ningún lado.
2. Smoke E2E contra el stack real de `docker-compose`: registro → login → buscar → reservar → pagar en bypass. Aunque sea nocturno.

**Por qué importa y no es opcional:** el contrato HTTP entre frontend y backend no está verificado en ninguna capa. El propio repo tiene la evidencia en `frontend/src/lib/api.ts:275-281` — el tipo decía `"APROBADA"` y el enum Java serializaba `"APROBADO"`, y nadie lo detectó hasta una revisión manual.

**Alternativa más barata si el E2E real resulta caro:** tests de contrato sobre los DTO serializados.

---

### Task 2.10: Evidencia del kill-switch por upload — AUD-021

**Files:** `SesionService.java:570-590`, `Almacenamiento.java`, `SesionController.java`

**Cambio concreto:** la evidencia se sube como archivo por el puerto `Almacenamiento` (igual que las credenciales), no como URL declarada por el cliente. Si se mantiene la URL: exigir `https://` y restringir a un dominio de storage propio configurado.

**Por qué:** la "evidencia" es lo que un Admin abre para decidir si sanciona a alguien. Un enlace controlado por el atacante es phishing dirigido al Admin. Y aceptar `http://` viola el NFR de TLS 1.2+ del propio proyecto.

**Depende de:** la Task 1.9, que ya agrega lectura al puerto `Almacenamiento`.

---

### Task 2.11: Reconciliar tracking (continuo) — AUD-030

Ya cerrada en FASE 0. **Mantenerla cerrada:** cada tarea de FASE 2 tilda en los dos documentos, juntos (guardrail B5 + AGENTS.md §8 actualizado en Task 0.10).

---

### Task 2.12: ADRs faltantes de la lista §4.4 — AUD-019 y §7.2

Los que FASE 0 no escribió porque dependían de decisiones de fases posteriores. Al cerrar FASE 2, revisar §4.4 del informe y escribir los que quedaron: proveedor de notificación (tras Task 2.3), rate limiting (tras Task 2.2), baja de menor (tras Task 2.6).

---

### Task 2.13: Anexo de modelo de amenaza a ADR-M3-01 — AUD-005

Ya iniciado en la Task 1.10 (`ADR-M3-02`). **Cerrarlo formalmente** con el resultado de lo que se implementó.

---

# FASE 3 — P2 Arquitectura y calidad

**Branch:** `aud/fase3-p2-calidad`

> **Regla de esta fase:** son 11 ítems de bajo riesgo individual, pero 3 de ellos tocan la estructura de paquetes. **Hacer esos 3 primero, en commits separados, y correr la suite completa entre cada uno.** Un refactor de imports que rompe 383 tests a la vez es irrevisable.

| # | Acción | Finding | Nota de ejecución |
|---|---|---|---|
| 3.1 | Romper el ciclo `shared ↔ admin` | AUD-019 | Mover `AdminModeracionGate` a `admin`, o `Admin`/`AdminRepository` a `shared`. Elegir con ADR-000-03 en la mano. Commit propio. |
| 3.2 | Mover `AlertaSeguridad` de `aula.model` a `seguridad.model` | AUD-019 | Es la entidad central del track de Alertas de M9. **Migración de datos NO hace falta** — la tabla no se mueve de schema, solo la clase Java. Verificar `@Table(schema=...)`. Commit propio. |
| 3.3 | Mover cada evento al módulo que lo publica | AUD-022 | Refactor mecánico de imports. **Los nombres de los eventos NO cambian** (guardrail A2) — solo el paquete. Commit propio. |
| 3.4 | `EXCLUDE` de superposición en `franjas_disponibilidad` + `franjaQueCubre` como query | AUD-025 | Migración V28. Hoy `findFirst()` resuelve la ambigüedad por orden arbitrario. |
| 3.5 | Distinguir la constraint violada antes de devolver 409 | AUD-023 | `ConstraintViolationException.getConstraintName()`. Solo `ex_reservas_sin_superposicion_*` → 409; el resto → 500 con log. |
| 3.6 | `credentials_version` en el JWT + `sub` = UUID | AUD-027 | Invalida sesiones al resetear contraseña. **Toca `JwtUtil`, `UsuarioDetailsService`, `AdminModeracionGate`, `UsuarioActual` y todos los tests que generan tokens.** Plan detallado propio. |
| 3.7 | Acotar el fallback a `catalogoMock` | AUD-026 | 404 estricto, o `NODE_ENV !== 'production'`. |
| 3.8 | Una calificación pública por sesión | AUD-028 | **Decisión (D9, 2026-09-22): califica solo el pagador.** `derivarDireccion()` (línea ~135) hoy mapea `beneficiario` **O** `pagador` → `DIR_ESTUDIANTE_A_TUTOR`; pasa a mapear solo `pagador` (el beneficiario que no es pagador cae en `CalificacionNoPermitidaException` → 403). Para un Estudiante adulto reservando para sí mismo no cambia nada (`pagador == beneficiario`). Argumento de defensa: coherente con el patrón del Artículo II que ya rige M1/M4/M9 — el menor no paga, no autoriza Tutores, no denuncia; el AR lo hace en su nombre. Resuelve que hoy una sesión con menor pese el doble en el promedio del Tutor. Revisar el frontend para que no ofrezca calificar a un menor. Hueco del Spec a cerrar: `Spec_M7` no dice quién califica cuando pagador != beneficiario. |
| 3.9 | Migración que dropee las tablas de CAP (V6) | AUD-035 | Migración **V29**, nunca editar V6 (guardrail A1). Comentario que referencie ADR-M1-02. |
| 3.10 | Dependabot + actualizar Spring Boot | AUD-032 | Mejor relación costo/beneficio del informe. |
| 3.11 | Middleware de Next.js: verificar firma o renombrar | AUD-016 | Si se verifica: `jose`, edge-compatible, comparte el secreto. Si no: renombrar el comentario y ser honesto. Ambas son válidas. |
| 3.12 | Borrar la rama DNI de `LiveKitWebhookService.mismaPersona()` | AUD-003 (limpieza) | Desde Task 1.3 el identity de LiveKit es el UUID. La rama `usuario.getDni().equals(identity)` solo sobrevive para tokens emitidos antes del deploy de 1.3 (TTL de `tinku.livekit.token-ttl-segundos`, default 1h). Borrarla cuando ese TTL haya vencido en todos los entornos. |

---

# FASE 4 — P3 Opcional

**Branch:** `aud/fase4-p3-opcional`

| # | Acción | Finding |
|---|---|---|
| 4.1 | Poner el `Usuario` en el principal del filtro (elimina la doble consulta por request) | AUD-036.1 |
| 4.2 | Unificar `@Transactional` en el de Spring (hoy hay `jakarta` en 3 servicios de `identidad`) | AUD-036.3 |
| 4.3 | Subpaquetes de capa en `matching` (40 clases planas) | AUD-036.2 |
| 4.4 | `@JsonIgnore` en `Usuario.getEdad()` | AUD-036.6 |
| 4.5 | Limpiar la rama del DNI en `LiveKitWebhookService.mismaPersona()` (la dejó viva la Task 1.3) | AUD-003 |
| 4.6 | Actuator con `/health` e `/info` | AUD-034 |

---

## Deuda que este plan decide NO tocar

Lista explícita, para que nadie la "arregle de paso". Todas están justificadas en §8.3 del informe.

- **`isClustered: false` en Quartz.** Documentado en ADR-000-04. Coherente con 1 desarrollador y USD 0-100/mes.
- **Anonimización por regex + diccionario.** Documentada en ADR-M6-02. La estrategia fail-safe es la correcta y el puerto está listo para el NER del ADR-M6-01.
- **JWT en `localStorage`.** Trade-off estándar, documentado en ADR-000-04. La mitigación es 3.6, no un refactor.
- **Storage en filesystem local.** Documentado en ADR-M1-03. Aislado tras el puerto.
- **Ausencia de proveedor LLM.** Ya declarado pendiente. No bloquea nada más.
- **T-M3-06 (clasificador NSFW on-device).** Chunk propio, fuera de este plan (guardrail A11).
- **Los 24 accesos cruzados a repositorios que FASE 3 no toca.** ADR-000-03 los declara deuda aceptada con su costo.

---

## Anexo A — Trampas conocidas del entorno

Cosas que ya hicieron perder tiempo una vez y van a volver a hacerlo.

1. **El enforcer de Maven y el exit code.** `./mvnw test` con JDK ≠ 21 falla en `maven-enforcer-plugin` y **no corre ningún test**. Si la salida se pipea (`| tail`), el exit code puede ser 0 y parecer éxito. **Leer siempre la línea `Tests run:`.** Si no aparece, no corrió nada.
2. **Testcontainers necesita Docker.** Sin el daemon, los fallos son timeouts de conexión a Postgres que parecen bugs del código. `docker info` antes de empezar.
3. **`driverDelegateClass` de Quartz.** Las tablas `QRTZ_*` de V3 usan `BYTEA`; el delegate por defecto espera un large object (OID) y tira `Bad value for type long`. Ya está resuelto en `application.yml` con `PostgreSQLDelegate`. **No lo quites.**
4. **`open-in-view: false`.** Las entidades lazy no se pueden navegar fuera de la transacción. Si aparece un `LazyInitializationException` en una tarea nueva, la solución es un `join fetch` en el repositorio, no activar open-in-view.
5. **`ddl-auto: validate`.** Si agregás un campo a una `@Entity` sin su migración, el contexto **no levanta** y todos los tests de integración fallan de golpe. Migración primero, entidad después.
6. **Los E2E de Playwright mockean `/api/**`.** Que pasen no dice nada sobre el backend (AUD-031).
7. **`spring.servlet.multipart.max-file-size` no está configurado.** Hoy rige el default implícito de Spring Boot (1MB). La Task 1.9 lo hace explícito.

---

## Anexo B — Comandos de verificación

```bash
# Suite completa del backend (el único comando que cuenta)
cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test

# Solo la línea que importa
... ./mvnw -B test 2>&1 | grep 'Tests run:.*Failures' | tail -1

# Frontend
cd frontend && bun run lint && bun run test:e2e

# matching-service (hoy sin CI — AUD-031)
cd matching-service && uv run pytest

# Grafo de dependencias entre módulos (para verificar FASE 3)
cd backend/src/main/java/com/tinku && for m in identidad matching aula reservas pagos resumen reputacion admin seguridad shared config; do \
  printf "%-12s -> " "$m"; \
  rg --no-filename -o "import com\.tinku\.([a-z]+)\." -r '$1' $m 2>/dev/null | sort -u | grep -v "^$m\$" | tr '\n' ' '; echo; done

# Accesos cruzados a repositorios (baseline: 27)
cd backend/src/main/java/com/tinku && for m in identidad matching aula reservas pagos resumen reputacion admin seguridad shared config; do \
  rg --no-filename -o "import com\.tinku\.[a-z]+\.(repository\.)?[A-Za-z]*Repository" $m 2>/dev/null | grep -v "com.tinku.$m\."; done | wc -l

# Estado de los findings
grep -c 'CERRADO' docs/auditoria/REGISTRO_FINDINGS.md
grep -c 'ABIERTO' docs/auditoria/REGISTRO_FINDINGS.md
```

---

## Anexo C — Qué hacer si algo no cierra

| Situación | Qué hacer |
|---|---|
| El finding estaba mal diagnosticado | **PARAR.** No implementar un fix a un problema que no existe. Marcar `REFUTADO` en el registro con la evidencia concreta que lo refuta. Avisar. |
| El fix rompe tests existentes | **No adaptar el test** (guardrail A8). Entender primero si el test codificaba el bug. Si lo codificaba, cambiarlo y **explicar por qué** en el mensaje del commit. |
| La tarea necesita un plazo que no está en `Tabla_Tiempos_Tinku.md` | **PARAR y preguntar** (guardrail B7). No inventar el número. |
| La tarea implica elegir proveedor/tecnología | **PARAR y escribir el ADR primero** (guardrail B8). |
| La tarea es más grande de lo que el plan dice | **PARAR.** Pedir que se expanda a plan detallado propio. No improvisar sobre la marcha en código de seguridad. |
| La suite baja de 383 tests | Borraste un test. Revertir y entender qué pasó. |
