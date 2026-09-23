# Specs de remediación — FASE 2, 3 y 4 (para ejecutar con opencode)

> **Leé este archivo completo antes de abrir cualquier spec de tarea.** Cada spec
> (`FASE2-*.md`, `FASE3-*.md`, `FASE4-*.md`) asume que ya conocés el protocolo de abajo.
> `AGENTS.md` se carga solo en cada sesión y **gana sobre cualquier spec** si se contradicen:
> en ese caso, parás y preguntás.

Estado al escribir estas specs (2026-09-22): FASE 0 y FASE 1 están cerradas y mergeadas en
`main`. Suite: **426 tests, 0 failures, 0 errors, 0 skipped**. Última migración aplicada:
**V24**. El estado vigente de cada finding está en `docs/auditoria/REGISTRO_FINDINGS.md`.

---

## 1. Cómo se usa cada spec

Una spec = una tarea = un branch = uno o más commits. El prompt a opencode es siempre:

> "Implementá `docs/superpowers/specs/remediacion/<archivo>.md` siguiendo
> `00-LEEME-opencode.md`. Parate en cada punto marcado **PARAR**."

Orden recomendado (hay dependencias reales, no lo cambies sin motivo):

| # | Spec | Depende de | Riesgo |
|---|------|-----------|--------|
| 1 | `FASE2-10-colision-alerta-denuncia.md` | — | Medio (dinero) |
| 2 | `FASE2-05-webhook-desconexion.md` | — | Medio (dinero) |
| 3 | `FASE2-06-baja-menor-anonimizacion.md` | — | Medio (datos de menores) |
| 4 | `FASE2-02-rate-limiting.md` | **P4** | Bajo |
| 5 | `FASE2-04-matching-auth-pool.md` | — | Bajo |
| 6 | `FASE2-07-modo-bypass.md` | **P2** | Bajo |
| 7 | `FASE2-09-evidencia-upload.md` | — | Bajo |
| 8 | `FASE2-03-notificaciones-outbox.md` | (email: **P5**) | Medio (funcionalidad nueva) |
| 9 | `FASE2-01-disponibilidad-bloques-30.md` | **P1** | **ALTO** (modelo de datos) |
| 10 | `FASE2-08-ci-matching-e2e.md` | **P3** | Bajo |
| 11 | `FASE3-01-limites-modulo.md` | FASE 2 mergeada | Medio (refactor) |
| 12 | `FASE3-02-integridad-esquema.md` (sin la parte C, cancelada) | FASE2-01 | Medio |
| 13 | `FASE3-03-jwt-uuid-credentials-version.md` | — | **ALTO** (auth) |
| 14 | `FASE3-04-frontend-honestidad.md` | **P6** | Bajo |
| 15 | `FASE3-05-calificacion-pagador.md` | — | Bajo |
| 16 | `FASE3-06-dependencias.md` | — | Bajo |
| 17 | `FASE4-01-calidad.md` | FASE 3 mergeada | Bajo |

Branches: todas las tareas de una fase van en su branch de fase —
`aud/fase2-p1-integridad`, `aud/fase3-p2-calidad`, `aud/fase4-p3-opcional` — con **un commit
por tarea como mínimo**. Si una tarea es grande (FASE2-01, FASE3-03), sub-branch
`aud/fase2-<slug>` que se mergea a la de fase.

---

### Convivencia con las specs de la tesis

Hay un segundo paquete, `docs/superpowers/specs/tesis/` (T01–T14), con decisiones de producto de la
tesis y un piloto que arranca el **27/10/2026**. Los dos paquetes comparten protocolo y guardrails.
El orden combinado recomendado está en `docs/superpowers/specs/ORDEN-GENERAL.md`. Choques ya
resueltos: FASE3-02 **no** dropea las tablas del CAP (la tesis las reusa), y el ADR de la baja de
menor usa el **siguiente** número `ADR-M1` libre (el `ADR-M1-04` es del CAP).

---

## 2. Protocolo obligatorio por tarea (no saltear ningún paso)

1. **Docker corriendo:** `docker info`. Sin Docker, Testcontainers falla por timeout y parece
   un bug del código.
2. **Leer el finding completo** en `docs/auditoria/2026-09-21-auditoria-independiente.md`
   (buscá el `AUD-XXX` de la spec). Los números de línea del informe están **corridos**:
   ubicá el código por **nombre de clase/método** con `rg`, nunca por número de línea.
3. **Verificar que el problema sigue existiendo.** Si el código ya no hace lo que la spec
   describe, **PARAR** y reportarlo. No implementes un fix para un problema que no existe.
4. **Test primero (RED).** Escribí el test de regresión, corrélo, y **pegá la salida del
   fallo**. Si el test pasa sin tocar producción, el test no cubre nada: reescribilo.
   Si la API nueva no existe, creá esqueletos vacíos para que compile y el rojo sea de
   comportamiento, no de compilación.
5. **Fix mínimo (GREEN).** Solo lo que hace pasar el test. Nada de refactor de oportunidad.
6. **Suite completa**, siempre, con el único comando que cuenta (desde `backend/`):
   ```bash
   JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test
   ```
   Leé la línea `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`. **Si no aparece, no corrió
   nada** (el enforcer exige JDK 21 y, si pipeás la salida, el exit code puede dar 0). N nunca
   puede bajar respecto del anterior.
7. **Documentación en el mismo commit que cierra el finding:**
   - `docs/auditoria/REGISTRO_FINDINGS.md`: estado `CERRADO` (o `ACEPTADO` con ADR), hash,
     test. El hash de tu propio commit no lo conocés: poné `(este commit)` y reemplazalo por el
     hash real en el commit siguiente.
   - `docs/Tasks_Tinku_Implementacion.md` **y** `docs/Tasks_Tinku_Chunks.md` juntos, o ninguno
     (AGENTS §8).
   - El Spec del módulo si cambia un comportamiento documentado.
8. **Commit** en conventional commits, en castellano, **sin** `Co-Authored-By` ni ninguna
   atribución de IA. El cuerpo explica el bug, el fix, el RED visto y la línea `Tests run:`.
9. **Nunca** `git commit --amend`, `push --force`, ni reescribir historia compartida.

---

## 3. Guardrails — lo que NO se toca (violar cualquiera = revertir la tarea)

| # | Regla |
|---|-------|
| A1 | **Nunca editar una migración aplicada** (`V1`…`V24`). Toda corrección es una migración **nueva**. Usá el siguiente número libre al momento de implementar (`fd -e sql . backend/src/main/resources/db/migration`), no el que sugiera una spec si ya está tomado. |
| A2 | **Nunca renombrar eventos de dominio**: `sesion.finalizada`, `sesion.interrumpida`, `sesion.no_show_estudiante`, `sesion.no_show_tutor`, `sesion.no_show_doble`, `sesion.killswitch_menor`, `sesion.killswitch_adultos`, `denuncia.registrada`, `denuncia.resuelta`, `alerta.resuelta`. Un evento nuevo se documenta en el Spec del módulo consumidor. |
| A3 | **Ningún plazo inventado.** Todo número de tiempo sale de `docs/Tabla_Tiempos_Tinku.md`. Si no está, **PARAR**. Agregar una fila solo si la spec lo indica explícitamente. |
| A4 | **Ningún timeout de negocio en memoria.** Nada de `Thread.sleep`, `@Scheduled` o timers para plata o seguridad: Quartz persistido. |
| A5 | **Sin dependencias nuevas** (Maven, bun, pip) **sin un ADR previo** en `docs/adr/`. |
| A6 | **Sin microservicios ni brokers nuevos** (Artículos VIII y IX). |
| A7 | **No adaptar un test existente para que pase.** Si tu fix rompe un test, primero entendé si el test codificaba el bug. Si lo codificaba, cambialo y explicá por qué en el commit. Si no, tu fix está mal. |
| A8 | **No borrar un `FIXME AUD-XXX`** sin cerrar ese finding. |
| A9 | **No tocar `frontend/tests/`** para hacer pasar un cambio de backend. |
| A10 | **Ante dos soluciones que cumplen, la más simple** — justificada contra 1 desarrollador y USD 0-100/mes. |
| A11 | **Seguridad del menor (AGENTS §3):** ante la duda de si hay un menor, tratalo como si lo hubiera. |
| A12 | **Un javadoc puede describir lo deseado y no lo real.** Verificá contra el código. |

---

## 4. Decisiones ya tomadas por el usuario (no volver a preguntar)

Resumen operativo. El registro original vive en `.superpowers/sdd/decisiones/` (fuera del repo).

| ID | Tema | Decisión |
|----|------|----------|
| D2 | Kill-switch rama menor | Se suspende **solo al detectado**. *(Implementado, FASE 1)* |
| D2-bis | Qué recibe el Adulto Responsable tras un kill-switch | **Aviso inmediato**; el **clip solo si M9 resuelve `SANCIONAR`**. Con `REACTIVAR` el AR nunca lo ve. |
| D3 | Plata del kill-switch | Escrow en pausa; se reembolsa al resolver la Alerta. *(Implementado, ADR-M3-02)* |
| D4 | LiveKit caído en el corte | Fail-open con reintento persistido. *(Implementado, ADR-M3-03)* |
| D5 | Alcance de denuncias | Con sesión exige participación; de perfil abierta. *(Implementado)* |
| D6 | Disponibilidad y tarifa | Bloques de **30 min**; el Estudiante elige unidades **consecutivas**; tarifa **por hora** (`precio = precioHora × unidades / 2`, HALF_UP, scale 2, congelado al reservar). |
| D7 | Baja de menor | **Anonimización**, no DELETE físico. |
| D8 | Rate limiting | **Bucket en memoria del proceso** (sin Redis, sin tabla). |
| D9 | Quién califica al Tutor | **Solo el pagador.** |

---

## 5. Decisiones PENDIENTES — la spec que las necesita se frena hasta resolverlas

Si una spec dice **PARAR (Pn)** y esta tabla no tiene la respuesta completada, **no avances**:
preguntale al usuario. La recomendación es del arquitecto; la decisión es del usuario.

| ID | Pregunta | Recomendación | Respuesta del usuario |
|----|----------|---------------|------------------------|
| P1 | ¿"El Tutor recomienda una duración" entra al MVP? (campo nuevo, no está en ningún Spec — Artículo VI) | **Fuera del MVP.** D6 funciona sin él; se agrega después como mejora. | _pendiente_ |
| P2 | ¿Cómo se acota el Modo Bypass? | **Solo habilitable fuera de `prod`** + banner persistente en el panel + log de auditoría. Es la más simple y cierra el riesgo real (marketplace gratis en producción). | _pendiente_ |
| P3 | ¿Smoke E2E contra el stack real o tests de contrato de DTOs? | **Tests de contrato** (baratos, corren en cada PR) + smoke E2E **nocturno y opcional**. | _pendiente_ |
| P4 | Valores de rate limit y de bloqueo por intentos de login (no hay filas en la Tabla de Tiempos) | 20 req/min por IP en públicos; 5 req/min en `/verificar-dni`; 120 req/min en webhooks; bloqueo tras 5 logins fallidos por DNI: 15 min, duplicando hasta 24 hs. Se agregan a la Tabla. | _pendiente_ |
| P5 | ¿Qué proveedor de email se usa? (desbloquea "olvidé mi contraseña"; requiere ADR) | **Resend** o **Brevo** en su plan gratuito: API HTTP simple, sin SDK obligatorio, entran en USD 0/mes con el volumen de un piloto. Hasta decidirlo, FASE2-03 entrega la bandeja in-app. | _pendiente_ |
| P6 | Middleware de Next.js: ¿verificar la firma del JWT o renombrarlo honestamente? | **Renombrar y documentar** que es UX, no seguridad. Verificar la firma exige compartir el secreto del JWT con el runtime edge del frontend y una dependencia nueva (`jose` + ADR), para proteger algo que el backend ya protege. | _pendiente_ |

---

## 6. Cómo reportar al terminar cada tarea

Un bloque corto, siempre con evidencia:

```
Tarea: <spec>
Finding(s): AUD-XXX → CERRADO / ACEPTADO (ADR-...)
RED: <línea del fallo antes del fix>
Suite: Tests run: N, Failures: 0, Errors: 0, Skipped: 0
Commits: <hash> <mensaje>
Pendiente / riesgos: <lo que no se hizo y por qué>
```
