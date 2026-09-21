# Auditoría Técnica Independiente — Tinku

**Fecha:** 2026-09-21
**Commit auditado:** `50b6b4d` (branch `main`, working tree limpio)
**Alcance:** backend Java/Spring Boot, frontend Next.js, matching-service Python, migraciones Flyway, docs (`/docs`), CI, docker-compose.
**Verificación ejecutada:** `JAVA_HOME=<temurin-21> ./mvnw -B test` → **383 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS** (3:09 min, Testcontainers contra Postgres real).

> Nota metodológica: el primer intento de correr la suite falló en el `maven-enforcer-plugin` (JVM por defecto = Temurin 25, el POM exige `[21,22)`). El resultado de arriba es el de la corrida real con JDK 21.

---

## 1. Executive Summary

### Estado general

Tinku es un proyecto **inusualmente disciplinado en su proceso** y **sustancialmente más débil en la ejecución de sus controles de seguridad que lo que su documentación sugiere**.

Lo que hay: 394 archivos Java organizados en 9 paquetes de dominio, 23 migraciones Flyway versionadas, 383 tests que pasan de verdad contra Postgres real, jobs de negocio persistidos en Quartz sin un solo `Thread.sleep`, webhooks con verificación HMAC fail-closed en LiveKit y MercadoPago, puertos fail-closed para el LLM y para reembolsos, y una trazabilidad documental (Constitución → Specs → ADR → Chunks) que es mejor que la de la mayoría de los proyectos comerciales que vi.

Lo que falta: **el control de seguridad central del proyecto —el kill-switch— no funciona en ninguna de sus dos mitades.** La mitad cliente no existe (ya está documentado como T-M3-06). La mitad servidor, que la documentación da por cerrada y testeada, **no cierra la sala de video**: cambia filas en la base de datos y emite un evento, mientras la videollamada con el menor sigue corriendo en LiveKit. Y el único mecanismo de confianza que quedó en pie tras el retiro del CAP (ADR-M1-02) —la Credencial Académica— **se aprueba a ciegas**: no existe ningún endpoint que le permita al Admin ver el documento que está aprobando.

### Principales fortalezas (reales, no de cortesía)

1. **Persistencia de plazos de negocio.** El Artículo X se cumple sin excepciones: escrow, no-show, corte automático, descargo, SLA, reactivación, reintentos de resumen — todos en Quartz JDBC, todos con guards de idempotencia por estado. Es el aspecto mejor ejecutado del proyecto.
2. **Idempotencia y fail-closed en dinero.** `EscrowService` sólo transiciona desde `retenido_escrow`; el webhook reconcilia contra `GET /v1/payments/{id}` y rechaza si el monto no coincide con el precio congelado; `ComisionPlataforma` es un único cálculo compartido. El razonamiento está documentado en el código, no adivinado.
3. **Autorización de Admin.** `AdminModeracionGate` + `AdminActivoAuthorizationManager` es defensa en profundidad real (gate por rol en el controller + filter chain que exige fila activa en `admin.admins`). 18 de 18 endpoints `/api/admin/**` invocan el gate.
4. **Honestidad del tracking de chunks.** `Tasks_Tinku_Chunks.md` marca explícitamente M3-C y M6-D como parciales y dice por qué. Eso es raro y es un activo para la defensa.
5. **La suite de tests corre de verdad.** No son 383 aserciones de humo: `ReservasFlujosIntegracionTest` levanta HTTP + Security + JPA + Flyway + Postgres y ejercita 39 escenarios incluyendo condiciones de carrera con `CountDownLatch`.

### Principales riesgos

| # | Riesgo | Por qué es grave |
|---|--------|------------------|
| 1 | El kill-switch no corta la sala de LiveKit | Artículo II inoperante en el peor escenario del sistema |
| 2 | Tokens de LiveKit con `roomAdmin` y `roomCreate` para participantes | Escalada de privilegios sobre todo el proyecto LiveKit |
| 3 | El DNI del menor se muestra al otro participante | Ley 25.326, dato de menor, visible en pantalla |
| 4 | Perfil `dev` por defecto ⇒ OCR falso | La verificación de identidad devuelve lo que el usuario declaró |
| 5 | Cualquier participante dispara el kill-switch sin evidencia | Sesión gratis + baja del Tutor del marketplace, a pedido |
| 6 | La Credencial Académica se aprueba sin ver el archivo | El único control de confianza vigente decide sin evidencia |
| 7 | Token de reset de contraseña escrito en el log | Toma de cuenta para cualquiera con acceso a logs |
| 8 | Reservas superpuestas posibles vía API | La `EXCLUDE` sólo cubre horarios idénticos |

### Áreas que requieren mayor atención

- **M3 (Aula Virtual):** es el módulo con mayor distancia entre spec e implementación. Toda la mitad de "conectividad y desconexión" (US-4, US-5 server-side, FR-AULA-002/003/010) no existe; el webhook sólo maneja `participant_joined`.
- **Notificaciones:** no existe infraestructura de notificación de ningún tipo. La obligación más importante del Spec M3 US-6 ("se notifica inmediatamente al Adulto Responsable") no está implementada ni tiene un puerto donde vivir.
- **Límites de módulo:** el monolito es modular en el árbol de paquetes y no en el grafo de dependencias. 27 imports directos a repositorios de otros módulos.
- **Superficie pública sin límite de tasa:** registro, login, verificación de DNI (que dispara OCR) y recuperación de contraseña son públicos y no tienen ningún rate limiting.

---

## 2. Architecture Map — la arquitectura REAL

### 2.1 Procesos

```
┌──────────────┐   HTTP/JSON    ┌───────────────────┐
│  Next.js 14  │───────────────▶│  Spring Boot 3.3.4│
│  (frontend)  │  Bearer JWT    │  (monolito, :8080)│
└──────────────┘                └─────────┬─────────┘
       │                                  │
       │ WebRTC                           │ HTTP  ┌────────────────────┐
       ▼                                  ├──────▶│ matching-service   │
┌──────────────┐                          │       │ FastAPI (:8000)    │
│ LiveKit Cloud│◀── Twirp CreateRoom ─────┤       │ SIN AUTENTICACIÓN  │
│              │─── webhook firmado ──────▶│       └─────────┬──────────┘
└──────────────┘                          │                 │
                                          │  JDBC           │ psycopg (RW)
                    ┌─────────────────────▼─────────────────▼──────┐
                    │  PostgreSQL 16 + pgvector                    │
                    │  9 schemas + public (QRTZ_*)                 │
                    └──────────────────────────────────────────────┘
                                          ▲
                                          │ HTTP (webhook firmado + REST)
                                   ┌──────┴───────┐
                                   │ MercadoPago  │
                                   └──────────────┘
```

Dos procesos escriben la misma base. El backend Java es dueño del schema `matching` vía JPA; el proceso Python **también escribe** `matching.perfiles_tutor_matching.embedding` directamente por SQL. El contrato entre ambos no es una API: es el esquema de la tabla.

### 2.2 Grafo real de dependencias entre módulos

Medido sobre `import com.tinku.<modulo>.` en `backend/src/main/java`:

```
identidad   → config, shared
matching    → identidad, shared
reservas    → identidad, shared
aula        → identidad, reservas, pagos
pagos       → identidad, reservas
seguridad   → identidad, reservas, pagos, aula
reputacion  → identidad, reservas, pagos, aula, matching
resumen     → identidad, reservas, pagos, aula, seguridad
admin       → identidad, reservas, pagos, aula, seguridad
shared      → identidad, admin        ◀── ciclo con admin
config      → admin
```

**No es un DAG.** `shared` depende de `admin` (`AdminModeracionGate` → `AdminRepository`) y `admin` depende de `shared`. Lo mismo `shared` ↔ `identidad`.

### 2.3 Capas

Ocho de los nueve módulos usan `model/ repository/ service/ web/` (+ `port/`, `jobs/`, `evento/` donde aplica). **`matching` es la excepción: 40 clases planas en el raíz del paquete**, sin subpaquetes de capa. Entidad, repositorio, servicio, controller y DTO conviven al mismo nivel.

### 2.4 Comunicación entre módulos

Tres mecanismos coexisten, sin una regla que diga cuándo usar cuál:

1. **Puertos con inversión de dependencia** (lo que la Constitución sugiere):
   `reservas.port.TarifaProveedor` ← implementado en `pagos`
   `reservas.port.ReputacionBloqueoProveedor` ← implementado en `reputacion`
   `identidad.port.VerificadorReservasFuturas` ← implementado en `reservas`
   `identidad.port.PerfilMatchingProvider` ← implementado en `matching`
2. **Acceso directo al repositorio del otro módulo** (27 ocurrencias) — ver AUD-019.
3. **Eventos de dominio in-process** (`ApplicationEventPublisher`, listeners síncronos en la transacción del publicador).

Los tres se usan para el mismo tipo de relación. Ejemplo dentro del mismo módulo: `pagos` obtiene la tarifa del Tutor a través de un puerto que `reservas` define, pero lee `reservas.repository.ReservaRepository` directamente.

### 2.5 Propiedad de los eventos (invertida)

| Evento | Lo publica | Vive en el paquete |
|--------|-----------|--------------------|
| `sesion.finalizada`, `sesion.interrumpida`, `sesion.no_show_*`, `sesion.killswitch_*` | `aula` | **`pagos.evento`** |
| `denuncia.registrada` | `seguridad` | **`pagos.evento`** |
| `denuncia.resuelta` | `seguridad` | **`reservas.evento`** |
| `reserva.confirmada/cancelada/reprogramada` | `reservas` | `reservas.evento` ✓ |
| `sancion.aplicada` | `seguridad` | `seguridad.evento` ✓ |

Los eventos viven en el módulo **consumidor**, no en el emisor ni en `shared`. Por eso `aula` importa `pagos`.

### 2.6 Puntos de integración externos

| Integración | Estado real | Fail-closed |
|---|---|---|
| LiveKit (salas + tokens + webhook) | Real, HMAC verificado | Sí al crear sala |
| MercadoPago (preferencia, webhook, refund, release) | Real, HMAC + anti-replay + reconciliación | Sí |
| Tesseract/Tess4J (OCR) | Real sólo en perfil `!dev & !test` | **No — cae a stub** |
| matching-service Python | Real, sin auth | Sí (503 explícito) |
| Proveedor LLM (M6) | **No existe** — puerto fail-closed | Sí |
| Transcript de sesión (M6) | **No existe** — devuelve `null` siempre | Sí |
| Email / SMS / push | **No existe** | n/a |
| Storage de archivos | Filesystem local, URL `file:` | n/a |

---

## 3. Findings

---

**ID:** AUD-001
**Severidad:** CRÍTICA
**Categoría:** Seguridad / Arquitectura / Cumplimiento constitucional

**Problema:**
El kill-switch no termina la videollamada. `SesionService.cortar()` cambia `sesiones_aprendizaje.estado` y `reservas.estado` en la base de datos y emite el evento hacia M5 — pero **nunca le dice nada a LiveKit**. `LiveKitService` sólo expone `crearSala()` y `generarTokenParticipante()`: no hay `DeleteRoom`, ni `RemoveParticipant`, ni `MutePublishedTrack`. La sala sigue viva y los tokens emitidos siguen siendo válidos durante su TTL (3600s por defecto).

**Evidencia:**
- `backend/src/main/java/com/tinku/aula/SesionService.java:597-613` (`cortar()`) — sólo `sesionRepo.save` / `reservaRepo.save`.
- `backend/src/main/java/com/tinku/aula/LiveKitService.java:39` — la única constante de path es `PATH_CREATE_ROOM`.
- `backend/src/main/java/com/tinku/aula/SesionService.java:274-288` (`obtenerToken`) — sólo verifica `livekitRoomId != null` y participación; no mira el estado de la sesión.
- `docs/specs/Spec_M3_Aula_Virtual.md:50` — *"entonces **la sesión se corta para ambos**"*.

**Por qué importa:**
Es el Artículo II de la Constitución, el requisito que el propio AGENTS.md llama "la restricción más importante de todo el proyecto". El corte es lo que protege al menor; hoy el corte es contable, no físico.

**Escenario:**
Sesión con un menor. Se dispara la rama menor. El backend marca la sesión `finalizada`, suspende al Tutor y emite `sesion.killswitch_menor`. El frontend del menor podría desconectarse por su cuenta — pero el del Tutor, que es el actor del que hay que proteger al menor, sigue conectado a una sala viva. Si el menor no cierra la pestaña (o si el cliente del Tutor está modificado), el contenido inapropiado sigue transmitiéndose. Peor: `POST /api/sesiones/{id}/token` sigue devolviendo tokens válidos después del corte.

**Recomendación:**
Agregar `RemoveParticipant` + `DeleteRoom` a `LiveKitService` e invocarlos dentro de `cortar()` **antes** de commitear el estado. Agregar un guard de estado en `obtenerToken()` que rechace sesiones `finalizada`/`interrumpida`. Test: tras el kill-switch, verificar que se llamó al endpoint Twirp de cierre y que `/token` responde 422.

**Confianza:** ALTA

---

**ID:** AUD-002
**Severidad:** CRÍTICA
**Categoría:** Seguridad

**Problema:**
El token de participante de LiveKit se emite con `roomCreate: true` y `roomAdmin: true`. Un participante puede, con su propio token: crear y **borrar cualquier sala del proyecto LiveKit** (incluidas sesiones de otros usuarios en curso), expulsar al otro participante, y mutear sus pistas.

**Evidencia:**
`backend/src/main/java/com/tinku/aula/LiveKitService.java:84`
```java
.claim("video", new VideoClaim(nombreSala, true, true, true))
//                             room,  roomJoin, roomCreate, roomAdmin
```
con `public record VideoClaim(String room, boolean roomJoin, boolean roomCreate, boolean roomAdmin)` (línea 148).

El javadoc del método (líneas 70-74) afirma lo **contrario** de lo que el código hace: *"el participante no puede abrir otra sala con este token"*.

El test tampoco lo cubre: `LiveKitServiceTest` (líneas 81-91) verifica `roomJoin == true` y nunca asserta que `roomCreate`/`roomAdmin` sean `false`.

**Por qué importa:**
Es escalada de privilegios a nivel tenant. El token viaja al navegador del usuario (incluido el de un menor), y es trivialmente extraíble desde DevTools.

**Escenario:**
Un Tutor malicioso extrae su token de `POST /api/sesiones/{id}/token`, y con el SDK de LiveKit llama a `DeleteRoom` sobre las salas de otros Tutores durante sus sesiones, o a `RemoveParticipant` para expulsar al menor de su propia sesión justo antes de que se dispare el no-show. También puede crear salas arbitrarias y consumir la cuota de la cuenta de LiveKit.

**Recomendación:**
`new VideoClaim(nombreSala, true, false, false)`. Agregar aserciones negativas explícitas en `LiveKitServiceTest`. Corregir el javadoc, que hoy documenta un comportamiento que no existe.

**Confianza:** ALTA

---

**ID:** AUD-003
**Severidad:** CRÍTICA
**Categoría:** Seguridad / Privacidad / Ley 25.326

**Problema:**
El DNI del usuario se usa como `identity` del participante en LiveKit, y LiveKit difunde la identity de cada participante a todos los demás en la sala. El frontend lo **renderiza literalmente en pantalla**.

**Evidencia:**
- `backend/src/main/java/com/tinku/aula/SesionService.java:285-287`
  ```java
  String token = liveKitService.generarTokenParticipante(usuario.getDni(), sesion.getLivekitRoomId());
  ```
- `frontend/src/app/aula/[id]/page.tsx` — `etiquetaParticipante()`:
  ```ts
  return identity ? `Participante (DNI ${identity})` : "Participante";
  ```
- Contradicción interna: `backend/.../identidad/dto/TutorPerfilResponse.java:12` documenta *"nunca expone passwordHash ni DNI"*.
- Constitución, NFR de Privacidad: *"cumplimiento de la Ley 25.326, con especial cuidado en datos de menores"*.

**Por qué importa:**
Un Tutor termina con el número de documento de un menor de edad al que conoció en un marketplace. Es exactamente el dato que permite suplantación de identidad, y es un dato de un menor. No hay ADR que registre esta decisión.

**Escenario:**
Adulto Responsable reserva para su hijo de 12 años. El Tutor entra a la sala y la UI le muestra "Participante (DNI 48123456)". El Tutor ahora tiene nombre (del perfil), DNI y horario recurrente del menor.

**Recomendación:**
Usar el `UUID` del usuario como identity (`LiveKitWebhookService.mismaPersona` ya acepta ambos, así que el webhook no se rompe) y agregar un claim `name` con el nombre de pila para la etiqueta de la UI. Auditar el resto del sistema por usos del DNI como identificador transversal, empezando por el `sub` del JWT propio.

**Confianza:** ALTA

---

**ID:** AUD-004
**Severidad:** CRÍTICA
**Categoría:** Configuración / Seguridad

**Problema:**
`application.yml` fija `spring.profiles.active: dev` **dentro del artefacto**, y en el perfil `dev` el servicio de OCR es un stub que devuelve como "extraído del documento" exactamente lo que el usuario declaró en el formulario. No existe ningún `application-prod.yml`, ni perfil `prod`, ni nada en el `Dockerfile` que cambie el perfil.

**Evidencia:**
- `backend/src/main/resources/application.yml:11-12` → `profiles: active: dev`
- `backend/src/main/java/com/tinku/identidad/ocr/StubOcrService.java:24` → `@Profile({"dev","test"})`, devuelve `new ResultadoOcr(true, declarados.dni(), declarados.nombre(), ...)`
- `backend/src/main/java/com/tinku/identidad/ocr/TesseractOcrService.java:33` → `@Profile("!dev & !test")`
- `backend/Dockerfile` → `CMD ["java","-jar","app.jar"]`, sin `SPRING_PROFILES_ACTIVE`
- `docker-compose.yml:45` → `SPRING_PROFILES_ACTIVE: dev`
- `fd . backend/src/main/resources -d 1` → sólo `application.yml`
- Además, en `dev` se activan `AdminSeedRunner` y `TutorSeedRunner`.

**Por qué importa:**
La verificación documental de identidad es el cimiento del Artículo II: es lo que garantiza que el "menor de 12" es un menor de 12 y que el "Tutor" es quien dice ser. Con el stub activo, ese control es un eco del formulario. Cualquiera se registra como cualquiera.

**Escenario:**
Se despliega la imagen tal como está (es lo que docker-compose hace hoy). Un adulto se registra declarando fecha de nacimiento de 2012 y sube cualquier JPG no vacío → queda creado un perfil de MENOR. O un Tutor se registra con nombre y DNI de otra persona. Nada falla, nada se loguea como anómalo.

**Recomendación:**
Quitar `profiles.active` del `application.yml` (dejar que el entorno lo defina y que falte = fallar), o invertir el default a un perfil sin stubs. Agregar un `@PostConstruct` que aborte el arranque si `StubOcrService` está activo y el perfil no es `dev`/`test`. Crear `application-prod.yml` — hoy `SaludInfraestructuraService` ya consulta `environment.acceptsProfiles("prod")` contra un perfil que no existe.

**Confianza:** ALTA

---

**ID:** AUD-005
**Severidad:** CRÍTICA
**Categoría:** Seguridad / Autorización / Negocio

**Problema:**
`POST /api/sesiones/{id}/killswitch` sólo exige ser participante de la Reserva. Cualquiera de los tres (Tutor, beneficiario, pagador) puede dispararlo contra el otro, sin evidencia, sin límite de tasa, y sin revisión previa. En la rama menor, el efecto es inmediato e irreversible sin intervención de un Admin: corte de la sesión, `activo_para_matching = false` sobre el Tutor y **reembolso total** del escrow.

**Evidencia:**
- `backend/src/main/java/com/tinku/aula/SesionService.java:434-466` (`ejecutarKillswitch`) — el único chequeo es `esParticipante(reserva, usuario)`.
- `backend/src/main/java/com/tinku/aula/SesionService.java:474-490` (`ramaMenor`) — suspensión + `SesionKillswitchMenorEvent`.
- `backend/src/main/java/com/tinku/pagos/service/EscrowService.java:213-216` — `onSesionKillswitchMenor` → `reembolsarSiRetenida`, sin ninguna condición sobre el tiempo transcurrido.
- `subirEvidencia` es opcional y posterior (`SesionService.java:570`).
- Sin rate limiting: `rg -i "ratelimit|bucket4j|resilience4j"` sobre `src` y `pom.xml` → 0 resultados.

**Por qué importa:**
Convierte el control de seguridad más sensible del sistema en una palanca de abuso: sesión gratis a demanda + baja de un competidor/Tutor del marketplace. La Alerta entra a una cola con ventana de 12hs; mientras tanto el Tutor está fuera del matching y sin ingresos.

**Escenario:**
Un Adulto Responsable reserva y paga una sesión. A los 55 de 60 minutos, con la clase ya dictada, llama a `POST /api/sesiones/{id}/killswitch` con `detectadoId = tutorId`. `reembolsarSiRetenida` devuelve el 100% del dinero, la sesión se cierra como si hubiera terminado, y el Tutor queda suspendido del matching. Repetible contra varios Tutores.

**Recomendación:**
Este es el costo de diseño de "el clasificador vive en el cliente" (ADR-M3-01) — no es un bug aislado, es una consecuencia que el ADR no evaluó. Opciones a evaluar: exigir la evidencia (clip de 30s) en el mismo request como condición del corte; desacoplar el efecto monetario del corte (retener en `pausado_denuncia` en vez de reembolsar hasta que M9 resuelva); límite de disparos por usuario/ventana; y un contra-efecto explícito cuando M9 resuelve `reactivar` sobre una alerta que resultó falsa. Merece un ADR propio.

**Confianza:** ALTA

---

**ID:** AUD-006
**Severidad:** ALTA
**Categoría:** Backend / Seguridad

**Problema:**
En la rama menor, `ramaMenor()` suspende **siempre al Tutor**, sin importar quién fue el `detectadoId`. El Spec contempla explícitamente que el menor sea quien genere la detección; en ese caso el código registra la Alerta apuntando al menor pero suspende al Tutor. Y como M9 resuelve la Alerta mirando `alerta.getDetectadoId()`, la reactivación nunca alcanza al Tutor suspendido.

**Evidencia:**
- `backend/.../aula/SesionService.java:474-478`
  ```java
  private SesionAprendizaje ramaMenor(SesionAprendizaje sesion, Reserva reserva, UUID detectadoId) {
      Usuario tutor = reserva.getTutor();
      tutor.setActivoParaMatching(false);
  ```
  comparar con `confirmarRamaAdultos` (línea 545), que sí usa `confirmacion.getDetectadoId()`.
- `backend/.../seguridad/service/AlertaSeguridadService.java:100-106` — `REACTIVAR` opera sobre `alerta.getDetectadoId()`.
- `docs/specs/Spec_M3_Aula_Virtual.md:51` — *"Dado que el participante menor es quien genera la detección… se aplica el mismo corte"* (no dice "y se suspende al Tutor").
- Sin cobertura: los 18 tests de `KillswitchIntegracionTest` usan siempre `detectadoId = tutor.getId()`.

**Por qué importa:**
Un Tutor queda fuera del matching por una detección que no generó, y el circuito de apelación de M9 no puede devolverlo: la Alerta apunta al menor, y `REACTIVAR` reactiva al menor. La suspensión del Tutor es permanente salvo intervención manual en base.

**Escenario:**
Menor con la cámara en un contexto inapropiado. El clasificador (cuando exista) dispara con `detectadoId = menorId`. El Tutor, inocente, queda suspendido. El Admin resuelve la Alerta como "reactivar": se reactiva al menor, el Tutor sigue suspendido y nadie lo nota.

**Recomendación:**
Suspender a `detectadoId`, no a `reserva.getTutor()`. Si la intención de producto es que en toda rama menor el Tutor quede en suspensión preventiva (defendible bajo el Artículo II), escribirlo en el Spec y hacer que la resolución de la Alerta contemple a ambos. Agregar el caso `detectadoId = menor` al test.

**Confianza:** ALTA

---

**ID:** AUD-007
**Severidad:** CRÍTICA
**Categoría:** Backend / Negocio / Seguridad del menor

**Problema:**
Tras el retiro del CAP (ADR-M1-02), la Credencial Académica quedó como el único control documental que habilita a un Tutor. **No existe ningún endpoint que permita ver el archivo de la credencial.** El DTO de la cola lo excluye deliberadamente, el archivo se guarda como URI `file:` en el filesystem del servidor, y no hay controller que lo sirva. El Admin aprueba o rechaza conociendo sólo nombre, apellido y tipo de documento.

**Evidencia:**
- `backend/.../admin/web/CredencialColaResponse.java:14` — *"No se expone el `archivoUrl` del documento: la revisión visual del archivo es del frontend interno"* — ese frontend interno no existe.
- `backend/.../identidad/port/AlmacenamientoLocal.java:45` — `return destino.toUri().toString();` → `file:///...`
- `rg "archivoUrl"` sobre `src/main` → 0 ocurrencias en cualquier controller de lectura.
- `frontend/src/components/admin/ColaCredenciales.tsx` — no hay link ni visor.
- `backend/.../identidad/service/CredencialService.java:96-107` — `marcarAprobada` pone `activoParaMatching = true`.
- Constitución, Artículo I (v2.2): *"El mecanismo de confianza vigente hoy es la Credencial Académica del Tutor…"*

**Por qué importa:**
El Artículo I acepta explícitamente el riesgo de un Tutor sin antecedentes verificados **a cambio de** la Credencial Académica. Si la Credencial se aprueba sin ver el documento, el intercambio no se cumplió: no queda ningún control documental efectivo antes de una sesión 1:1 con un menor.

**Escenario:**
Un Tutor sube un JPG en blanco (no hay validación de content-type ni de contenido). Aparece en la cola como "Juan Pérez — TITULO — intento 1". El Admin, sin nada más que decidir, aprueba. `activo_para_matching = true`. El Tutor queda habilitado para sesiones con menores.

**Recomendación:**
Endpoint `GET /api/admin/moderacion/credenciales/{id}/archivo` gateado por `requiereModeracion`, que sirva el byte stream (no la URI), auditado por el interceptor de M8. Validar content-type y tamaño en la subida. Evaluar si `marcarAprobada` debería exigir `estado == PENDIENTE` (ver AUD-033).

**Confianza:** ALTA

---

**ID:** AUD-008
**Severidad:** CRÍTICA
**Categoría:** Seguridad / Observabilidad

**Problema:**
La única implementación de `NotificadorResetPassword` escribe el token de recuperación **en claro** en el log de la aplicación, junto con el DNI del usuario. El endpoint que lo genera (`POST /api/usuarios/recuperar-password`) es público.

**Evidencia:**
`backend/src/main/java/com/tinku/identidad/port/NotificadorResetPasswordLog.java:26-28`
```java
log.info("Recuperación de contraseña solicitada para usuario {} (dni {}). Token: {}",
        usuario.getId(), usuario.getDni(), tokenPlano);
```
`backend/src/main/resources/application.yml` no define ningún `logging.level` restrictivo.
`SecurityConfig.java:101` — la ruta es `permitAll()`.

**Por qué importa:**
Cualquiera con acceso de lectura a los logs (agregador, consola cloud, soporte, un backup, un error de configuración de retención) puede tomar el control de **cualquier** cuenta: Tutor, Adulto Responsable, menor o Admin. Y el atacante controla cuándo se genera el token, porque el endpoint es público y sólo necesita el DNI.

El javadoc lo declara "limitación conocida, no un bug". Es correcto que sea deliberado; no es correcto que sea sólo una limitación de alcance: es un secreto de autenticación persistido en un canal sin control de acceso.

**Escenario:**
Un desarrollador comparte un dump de logs para debuggear un incidente. El dump contiene tokens de reset válidos (TTL 1 hora) y los DNI asociados. Cualquier receptor del dump puede tomar esas cuentas.

**Recomendación:**
Hasta que exista el ADR del proveedor de email: loguear sólo el `usuario.getId()` y escribir el token en un canal restringido (una tabla de "outbox" que sólo un Admin pueda leer, auditada), o no emitirlo en absoluto y dejar el flujo explícitamente deshabilitado. Nunca `log.info` con el material del token.

**Confianza:** ALTA

---

**ID:** AUD-009
**Severidad:** ALTA
**Categoría:** DB / Backend / Concurrencia

**Problema:**
La `EXCLUDE constraint` de FR-RES-007 compara el horario con **igualdad exacta**, no con solapamiento de rangos. Y `ReservaService.crearReserva()` no hace ningún chequeo de solapamiento en aplicación: sólo valida que el instante caiga dentro de una franja activa. El resultado es que dos reservas del mismo Tutor que se solapan pero no empiezan en el mismo instante pasan las dos.

**Evidencia:**
- `backend/src/main/resources/db/migration/V9__m4_reservas.sql:83-87`
  ```sql
  EXCLUDE USING gist (tutor_id WITH =, horario WITH =) WHERE (estado <> 'cancelada')
  ```
- `backend/.../reservas/service/ReservaService.java:341-366` (`crearReserva`) — valida `VENTANA_MINIMA` y `estaDentroDeFranjaActiva`, nada más.
- La tabla `reservas.reservas` **no tiene columna de duración**. La duración efectiva se deriva de la franja: `SesionService.java:126-137` → `duracionFranja = Duration.between(f.getHoraInicio(), f.getHoraFin())`.
- `HorariosDisponiblesService.seSuperponen()` (líneas 81-88) **sí** calcula solapamiento correctamente — pero sólo para pintar la UI, no para autorizar la escritura.
- Sin cobertura: los 3 tests de FR-RES-007 (`ReservasFlujosIntegracionTest:441, 989, 1110`) usan siempre el **mismo** `horario`.
- `docs/Tasks_Tinku_Implementacion.md:115` — T-M4-14 (manejo del 409 en el frontend) figura sin tildar.

**Por qué importa:**
Es una violación de integridad con consecuencias monetarias directas: dos Estudiantes pagan por un horario que un solo Tutor no puede dictar dos veces. El sobreviviente entra al circuito de no-show, reembolso y reputación.

**Escenario:**
Tutor publica franja lunes 10:00–12:00 (120 min, válida por FR-RES-024). Estudiante A reserva 10:00 → sesión agendada 10:00–12:00. Estudiante B llama directo a `POST /api/reservas` con `horario = 11:00` → pasa `estaDentroDeFranjaActiva` (11:00 ∈ [10:00, 12:00)), la `EXCLUDE` no dispara porque 10:00 ≠ 11:00 → 201 Created. El Tutor tiene dos sesiones de 2 horas solapadas, ambas cobradas.

**Recomendación:**
Persistir la duración en `reservas.reservas` (hoy es un dato derivado y mutable — ver AUD-020) y reemplazar la `EXCLUDE` por una sobre `tstzrange(horario, horario + duracion)` con el operador `&&`. Agregar el chequeo equivalente en `crearReserva`/`reprogramar` para dar un 422 con mensaje de negocio antes de llegar a la constraint. Test explícito: 10:00 y 10:30 en la misma franja de 120 min.

**Confianza:** ALTA

---

**ID:** AUD-010
**Severidad:** ALTA
**Categoría:** DB / Pagos / Concurrencia

**Problema:**
`pagos.transacciones` no tiene índice único ni sobre `mp_payment_id` ni sobre `reserva_id`. La idempotencia del webhook de MercadoPago depende exclusivamente de un `findByMpPaymentId(...).isPresent()` en aplicación, que es un clásico check-then-act sin protección de base.

**Evidencia:**
- `backend/src/main/resources/db/migration/V11__m5_pagos.sql:16-32` — sólo `idx_transacciones_reserva` e `idx_transacciones_escrow_liberar`, ambos no únicos.
- `backend/.../pagos/service/EscrowService.java:111-114`
  ```java
  public void procesarPagoAprobado(String mpPaymentId) {
      if (transaccionRepo.findByMpPaymentId(mpPaymentId).isPresent()) { return; }
  ```
- El propio código documenta la consecuencia en `EscrowService.java:333-336`: *"dos filas por reserva_id los rompería a todos con `IncorrectResultSizeDataAccessException`"* — `findByReservaId` devuelve `Optional` y se usa en 6 puntos de `EscrowService` más `DenunciaService.tieneEscrowActivo` y `ResumenService`.

**Por qué importa:**
MercadoPago reintenta agresivamente los webhooks no-2xx, y los reintentos pueden solaparse. Dos hilos que pasan el guard crean dos filas. A partir de ahí, toda consulta `findByReservaId` de ese escrow lanza excepción — es decir, la liberación, el reembolso, la pausa por denuncia y la generación del resumen de esa reserva quedan rotos permanentemente, con el dinero atascado.

**Escenario:**
MP envía la notificación del pago X. Tarda >22s (timeout de MP) y reintenta. Ambas llegan y ambas pasan el guard antes de que la primera commitee. Se crean dos `Transaccion` para la misma reserva. Cuando la sesión finaliza, `onSesionFinalizada` → `findByReservaId` → `IncorrectResultSizeDataAccessException` → el Tutor nunca cobra y el caso no aparece en la cola de `pagos-fallidos` (que filtra por `intentos_liberacion`).

**Recomendación:**
Migración nueva con `UNIQUE (mp_payment_id)` y `UNIQUE (reserva_id)` (esta última evaluando primero el caso del pago tardío de `reembolsarPagoTardio`, que hoy ya está condicionado a que no exista fila previa). Traducir la violación a un no-op idempotente, no a un 5xx.

**Confianza:** ALTA

---

**ID:** AUD-011
**Severidad:** ALTA
**Categoría:** Seguridad / Autorización (BOLA)

**Problema:**
`DenunciaService.presentar()` no verifica ninguna relación entre el denunciante, el denunciado y la sesión denunciada. Cualquier usuario autenticado no-menor puede denunciar a **cualquier** usuario por **cualquier** `sesionId`, incluyendo sesiones en las que no participó. Y una denuncia con `sesionId` cuyo escrow está retenido **pausa el escrow ajeno** de inmediato.

**Evidencia:**
`backend/.../seguridad/service/DenunciaService.java:100-133` — las únicas validaciones son: el denunciante no es MENOR, el `denunciadoId` existe, y la `sesionId` existe. No hay chequeo de participación, ni de que el denunciado tenga relación con la sesión, ni de auto-denuncia, ni de duplicados.
`DenunciaService.java:129-131` → `events.publishEvent(new DenunciaRegistradaEvent(this, reservaId))`
`backend/.../pagos/service/EscrowService.java:231-241` → `onDenunciaRegistrada` → `estado = PAUSADO_DENUNCIA`, `liberarAt = null`, `cancelarLiberacion`.

**Por qué importa:**
Es un BOLA con efecto financiero directo sobre terceros, ejecutable por cualquier cuenta registrada. La pausa del escrow no se levanta sola: requiere que un Admin resuelva el caso.

**Escenario:**
Un Tutor crea una cuenta de adulto. Enumera `sesionId` (UUID, no enumerables trivialmente — pero se obtienen de cualquier reserva propia, y `GET /api/sesiones/por-reserva/{id}` los expone a participantes). Presenta denuncias contra Tutores competidores sobre sus sesiones. Cada denuncia congela el cobro de ese Tutor y arranca un track de 48hs + 5 días hábiles de SLA. Sin rate limiting, es escalable.

**Recomendación:**
Exigir que el denunciante sea participante de la sesión denunciada (o que la denuncia sin `sesionId` sea la única vía "de perfil", y que ahí exista al menos una reserva compartida histórica). Rechazar auto-denuncia. Límite de denuncias abiertas por par denunciante-denunciado. Evaluar si la pausa del escrow debe requerir que el denunciado sea el beneficiario del escrow.

**Confianza:** ALTA

---

**ID:** AUD-012
**Severidad:** ALTA
**Categoría:** Seguridad

**Problema:**
No existe rate limiting ni bloqueo por intentos fallidos en ningún endpoint del sistema. `AuthService.login` compara la contraseña y no registra nada. El sistema tiene un backoff sofisticado para intentos de OCR y de credencial (FR-ID-011/012) — y ninguno para contraseñas.

**Evidencia:**
- `backend/.../identidad/service/AuthService.java:33-54` — sin contador de intentos.
- `rg -i "ratelimit|rate_limit|bucket4j|resilience4j" backend/src backend/pom.xml` → 0 resultados.
- `SecurityConfig.java:98-101` — públicos: `/registro`, `/verificar-dni`, `/login`, `/tutores/registro`, `/tutores/verificar-dni`, `/recuperar-password`, `/resetear-password`.
- El username es el **DNI** (8 dígitos, espacio pequeño y parcialmente predecible por cohorte de edad).
- Política de contraseña: `@Size(min = 8)` y nada más (`RegistroAdultoRequest.java:20`) — sin complejidad, sin lista de contraseñas comunes.

**Por qué importa:**
Combinación de espacio de usuarios pequeño y enumerable (DNI argentino), contraseñas de 8 caracteres sin restricción, y cero limitación de intentos. Además `/verificar-dni` dispara OCR (Tesseract in-process, CPU-bound) sin autenticación: es un vector de DoS de bajo costo.

**Escenario:**
Un atacante itera DNIs de un rango plausible contra `POST /api/usuarios/login` con un diccionario corto. Nada lo frena, nada lo registra, nadie se entera. Cuentas comprometidas incluyen las de menores.

**Recomendación:**
Rate limiting por IP + por DNI en los 7 endpoints públicos (filtro con un bucket en Redis — ya está en el Registro de Decisiones de la Constitución). Bloqueo temporal escalado tras N fallos, reutilizando el patrón que ya existe en `OcrBackoffService`. Política de contraseña mínima. Prioridad especial en `/verificar-dni` por el costo de CPU del OCR.

**Confianza:** ALTA

---

**ID:** AUD-013
**Severidad:** ALTA
**Categoría:** Backend / Autorización

**Problema:**
Dos caminos reactivan una cuenta o el matching sin verificar si existe otra sanción vigente por una causa distinta, revirtiendo silenciosamente una suspensión que no les corresponde levantar.

**Evidencia:**
1. `backend/.../seguridad/service/AlertaSeguridadService.java:103-106`
   ```java
   if (decision == DecisionAlerta.REACTIVAR) {
       detectado.setActivoParaMatching(true);
       detectado.setEstadoCuenta(EstadoCuenta.ACTIVA);
   ```
   Sin consultar `SancionRepository`.
2. `backend/.../identidad/service/CredencialService.java:102-106`
   ```java
   if (!tutor.isActivoParaMatching()) { tutor.setActivoParaMatching(true); ... }
   ```
   Sin mirar `estadoCuenta` ni sanciones vigentes.

**Por qué importa:**
`estado_cuenta = SUSPENDIDA` es el efecto de una sanción definitiva o de un baneo (`SancionListeners.java:82`). Reactivarlo desde otro track significa que un usuario con suspensión definitiva vuelve al sistema por la puerta de al lado.

**Escenario:**
Un Tutor recibe `SUSPENSION_DEFINITIVA` por una denuncia fundada grave. Semanas antes tenía una Alerta de kill-switch abierta y sin resolver. Un Admin barre la cola y la resuelve como "reactivar" (era un falso positivo aislado). El `setEstadoCuenta(ACTIVA)` levanta la suspensión definitiva. El Tutor vuelve a loguearse y a aparecer en el matching.

Variante 2: Tutor suspendido definitivamente sube una credencial nueva; un Admin de Moderación la aprueba; `activoParaMatching = true` lo devuelve al matching con la cuenta suspendida.

**Recomendación:**
Antes de reactivar, consultar si existe una `Sancion` vigente (`SUSPENSION_DEFINITIVA`, `BANEO_AUTORIDADES`, o `SUSPENSION_TEMPORAL` con `vigenteHasta > now`) sobre ese usuario, y si la hay, no tocar `estadoCuenta`/`activoParaMatching`. Test de regresión para ambos caminos.

**Confianza:** ALTA

---

**ID:** AUD-014
**Severidad:** ALTA
**Categoría:** Funcionalidad incompleta / Seguridad del menor

**Problema:**
No existe infraestructura de notificaciones de ningún tipo. La única interfaz de notificación del proyecto es `NotificadorResetPassword`, cuya única implementación escribe en el log. Todo lo que el Spec describe como "se notifica" es, en la implementación, un `log.info`.

**Evidencia:**
- `rg "interface.*Notific|class.*Notific" backend/src/main/java` → sólo `NotificadorResetPassword` y `NotificadorResetPasswordLog`.
- `docs/specs/Spec_M3_Aula_Virtual.md:50` — *"…y **se notifica inmediatamente al Adulto Responsable**"*.
- `backend/.../aula/SesionService.java:474-490` (`ramaMenor`) — no hay ninguna notificación.
- `backend/.../reputacion/jobs/RecordatorioCalificacionJob.java` y `resumen/jobs/RecordatorioResumenJob.java` → `log.info`.
- `backend/.../resumen/service/ResumenService.java:363-366` — `RECORDATORIO_RESUMEN` es un log.
- El descargo de 48hs (FR-SEC-010) corre sin que el denunciado reciba aviso; el endpoint `/api/denuncias/recibidas` existe pero requiere que el usuario entre a mirar.

**Por qué importa:**
"Se notifica inmediatamente al Adulto Responsable" es la obligación más importante hacia la familia en todo el sistema: es lo que convierte un corte técnico en protección real. Hoy el Adulto Responsable no se entera nunca de que la sesión de su hijo fue cortada por contenido inapropiado.

**Escenario:**
Kill-switch rama menor a las 18:40. La sesión se marca finalizada, se reembolsa, se crea la Alerta. El Adulto Responsable ve en su panel una reserva "finalizada" y un reembolso. Nada le dice qué pasó. Se entera, si acaso, por el menor.

**Recomendación:**
Definir el puerto `Notificador` (destinatario, plantilla, payload) ahora, aunque la implementación inicial sea un outbox persistido en base y consultable desde el panel. El ADR del proveedor de email puede esperar; el puerto y los llamadores, no. Mínimo indispensable antes de piloto: kill-switch rama menor → Adulto Responsable, y denuncia registrada → denunciado.

**Confianza:** ALTA

---

**ID:** AUD-015
**Severidad:** ALTA
**Categoría:** Seguridad / Arquitectura

**Problema:**
El `matching-service` de Python no tiene ninguna autenticación, autorización ni restricción de origen, y posee credenciales de escritura directa a la base de datos. `docker-compose.yml` lo publica en el puerto 8000 del host.

**Evidencia:**
- `matching-service/main.py` — ni un middleware, ni un header de API key, ni dependencia de auth en ninguno de los 3 endpoints.
- `matching-service/main.py:145-157` (`guardar_embedding`) — `UPDATE matching.perfiles_tutor_matching SET embedding = ...`
- `docker-compose.yml:34-35` → `ports: - "8000:8000"`
- `matching-service/main.py:238-259` (`POST /recompute-embeddings`) — recorre **todos** los perfiles, embeddea cada uno con el modelo de sentence-transformers y hace un `UPDATE` por perfil, **abriendo una conexión nueva a Postgres por cada uno** (`_conectar()` dentro de `guardar_embedding`).

**Por qué importa:**
Cualquiera que alcance el puerto 8000 puede: agotar CPU y conexiones de la base con `/recompute-embeddings` repetido; y sondear el ranking semántico de cualquier conjunto de `tutor_ids`. No hay pooling ni transacción envolvente, así que un fallo a mitad deja el índice en estado parcial.

**Escenario:**
El servicio se despliega en la misma red que el backend pero sin firewall entre ambos (es lo que hace docker-compose hoy). Un contenedor comprometido —o simplemente el host, donde el puerto está mapeado— dispara `/recompute-embeddings` en loop: N conexiones nuevas por vuelta, el pool de Postgres se agota y el monolito entero deja de responder.

**Recomendación:**
Token compartido en header verificado por un middleware de FastAPI (el backend ya tiene dónde configurarlo: `tinku.matching-service.*`). Quitar el `ports:` del compose y dejarlo sólo en la red interna. Pool de conexiones (`psycopg_pool`) y una sola transacción para el recompute. Y agregar CI para este servicio — hoy `test_main.py` no corre en ningún pipeline (ver AUD-031).

**Confianza:** ALTA

---

**ID:** AUD-016
**Severidad:** MEDIA
**Categoría:** Seguridad / Frontend

**Problema:**
El middleware de Next.js "protege" `/admin/*`, `/cuenta/*`, `/aula/*` verificando únicamente que **exista** una cookie llamada `tinku_jwt`. No valida la firma, ni la expiración, ni el contenido.

**Evidencia:**
`frontend/src/middleware.ts:12-19`
```ts
const token = request.cookies.get(COOKIE_NAME)?.value;
if (!token) { /* redirect a /login */ }
return NextResponse.next();
```
La cookie se escribe sin `httpOnly` y sin `Secure` (`frontend/src/lib/auth.ts:37`).

**Por qué importa:**
Cualquiera puede ejecutar `document.cookie = "tinku_jwt=x"` y acceder al shell del panel de administración. No es una brecha de datos —el backend sigue devolviendo 403 en cada llamada— pero es un control de seguridad que no controla nada, y en un proyecto de tesis un jurado lo va a leer como tal.

**Escenario:**
Usuario cualquiera setea la cookie a mano, entra a `/admin/denuncias` y ve la estructura completa del panel de moderación (aunque sin datos). Filtración de superficie interna y falsa sensación de protección.

**Recomendación:**
O verificar la firma en el middleware (edge-compatible con `jose`, que necesita compartir el secreto), o ser explícito en el código de que es una redirección de UX y no una barrera de seguridad, y documentarlo. La segunda opción es la más honesta con el Artículo VII (simplicidad) siempre que el comentario deje de decir "Protección de rutas desde el server".

**Confianza:** ALTA

---

**ID:** AUD-017
**Severidad:** ALTA
**Categoría:** Backend / DB

**Problema:**
`darDeBajaMenor` hace un `DELETE` físico de la fila de `identidad.usuarios`, pero sólo limpia `autorizaciones_tutor` y `consentimientos_menor`. Cualquier otra tabla con FK hacia ese usuario hace fallar el borrado.

**Evidencia:**
`backend/.../identidad/service/UsuarioService.java:296-298`
```java
autorizacionRepo.deleteByMenorId(menorId);
consentimientoRepo.deleteByMenorId(menorId);
usuarioRepository.delete(menor);
```
FKs que apuntan a `identidad.usuarios(id)` y no se limpian:
- `reservas.reservas.beneficiario_id` (V9:52), `pagador_id`, `tutor_id`
- `reservas.solicitudes_sesion.menor_id` (V9:33)
- `seguridad.denuncias` / `seguridad.sanciones`
- `reputacion.calificaciones.autor_id`
- `pagos.pasarela_estado.updated_by`

El test `UsuarioServiceDarDeBajaTest` es unitario con mocks (4 tests) — nunca ejercita el `DELETE` real contra el esquema.

**Por qué importa:**
La baja de un perfil de menor (FR-ID-014) es exactamente el caso donde el Artículo V y la Ley 25.326 importan más: es el derecho de supresión de datos de un menor. Hoy falla con un `DataIntegrityViolationException` → 500 para cualquier menor que haya tenido una reserva, que es el caso normal.

**Escenario:**
Adulto Responsable pide dar de baja el perfil de su hijo, que tuvo 6 sesiones el año pasado. `confirmarBaja = true`. El `DELETE` viola la FK de `reservas.reservas.beneficiario_id` → 500 sin mensaje útil. El dato del menor queda en el sistema indefinidamente.

**Recomendación:**
Decidir y documentar (merece ADR): borrado físico en cascada, o anonimización (nombre/apellido/dni/fecha_nacimiento reemplazados, fila conservada por integridad contable y de auditoría). La segunda suele ser la correcta cuando hay transacciones financieras de por medio. Test de integración con Testcontainers sobre un menor con historial.

**Confianza:** ALTA

---

**ID:** AUD-018
**Severidad:** ALTA
**Categoría:** Negocio / Seguridad

**Problema:**
El "Modo Bypass" convierte cada `POST` de preferencia de pago en una confirmación de reserva sin dinero. Es un flag global, sin expiración, sin restricción de perfil, activable con un `PATCH` por cualquier Admin de Soporte Financiero.

**Evidencia:**
- `backend/.../pagos/service/PagoService.java:85-87`
  ```java
  if (!pasarela.estaHabilitada()) { return confirmarEnBypass(reserva, comision); }
  ```
- `confirmarEnBypass` (líneas 101-113) → crea la `Transaccion` `en_bypass=true` y llama a `reservaService.confirmarPagoSimulado(...)`, que emite `ReservaConfirmadaEvent` → M3 crea y agenda la Sesión.
- `backend/.../admin/web/ColasFinancieroController.java:164-171` — `PATCH /api/admin/financiero/pasarela`, gate `requiereSoporteFinanciero`.
- `backend/.../pagos/service/EscrowService.java:286-288` — en bypass, el reembolso es sólo cambio de estado local.
- ADR-M5-01 documenta la decisión, pero no acota el ámbito (dev/piloto) ni pone un TTL.

**Por qué importa:**
Un toggle deja el marketplace entero gratis, sin ningún indicador visible para el usuario más allá de `bypass: true` en la respuesta. No hay alerta, ni expiración automática, ni restricción a perfiles no productivos. Es un único punto de fallo de negocio con un único control de acceso.

**Escenario:**
Un Admin de Soporte Financiero activa el bypass para depurar un incidente un viernes y se olvida de apagarlo. Todo el fin de semana las reservas se confirman sin cobro, las sesiones se dictan y los escrows en bypass nunca liberan dinero real al Tutor. El interceptor de auditoría registra el PATCH, pero nadie mira la cola.

**Recomendación:**
Restringir el bypass a perfiles no productivos (`@Profile("!prod")` sobre el toggle), o darle TTL con un job de Quartz que lo reactive, o exigir doble confirmación. Como mínimo: alerta al activarlo y un banner persistente en el panel. Extender ADR-M5-01 con el ámbito y el control compensatorio.

**Confianza:** ALTA

---

**ID:** AUD-019
**Severidad:** ALTA
**Categoría:** Arquitectura

**Problema:**
Los límites de módulo son organizativos, no reales. Hay 27 imports directos a repositorios de otros módulos, y 8 de los 9 módulos manipulan entidades JPA ajenas. Existen puertos (`identidad/port`, `reservas/port`, `pagos/port`, `resumen/port`) que aplican inversión de dependencia correctamente — y conviven con accesos crudos para relaciones del mismo tipo. Además el grafo tiene ciclos.

**Evidencia:**
Accesos directos a repositorios de otro módulo (extracto):
```
resumen     → aula.AlertaSeguridadRepository, aula.SesionAprendizajeRepository,
              reservas.ReservaRepository, seguridad.DenunciaRepository
admin       → aula.AlertaSeguridadRepository, identidad.CredencialAcademicaRepository,
              identidad.UsuarioRepository, pagos.PrecioReferenciaRegionalRepository,
              pagos.TransaccionRepository, reservas.ReservaRepository,
              seguridad.DenunciaRepository
seguridad   → aula.AlertaSeguridadRepository, aula.SesionAprendizajeRepository,
              identidad.UsuarioRepository, pagos.TransaccionRepository
shared      → admin.AdminRepository, identidad.UsuarioRepository
config      → admin.AdminRepository
```
Escrituras cruzadas concretas:
- `seguridad/listeners/SancionListeners.java:82-84` muta `identidad.model.Usuario.estadoCuenta`
- `aula/SesionService.java:611` muta `reservas.model.Reserva.estado`
- `seguridad/service/AlertaSeguridadService.java:104` muta `aula.model.AlertaSeguridad.estado`

Ciclos: `shared ↔ admin`, `shared ↔ identidad`.
Inconsistencia de criterio: `pagos` obtiene la tarifa vía `reservas.port.TarifaProveedor` (correcto) **y** lee `reservas.repository.ReservaRepository` directo.

Caso especial: `AlertaSeguridad` es la entidad central del track de Alertas de M9, y vive en `com.tinku.aula.model`.

**Por qué importa:**
El Artículo VIII exige "límites de dominio claros" y la tesis se defiende sobre esa elección. Hoy no se puede cambiar el esquema de ninguna tabla sin auditar 4 módulos, y el argumento "monolito modular, extraíble a servicios si hace falta" no se sostiene contra el código.

**Escenario:**
Se decide agregar borrado lógico a `reservas.reservas`. Hay que revisar `aula`, `pagos`, `admin`, `reputacion` y `resumen`, que leen el repositorio directo y filtran por `estado` con criterios propios. Cualquiera que se olvide introduce un bug silencioso.

**Recomendación:**
No refactorizar todo. Priorizar por riesgo: (a) romper el ciclo `shared ↔ admin` moviendo `AdminModeracionGate` a `admin` o el `Admin`/`AdminRepository` a `shared`; (b) mover `AlertaSeguridad` a `seguridad`, que es su dueño de negocio; (c) mover cada evento al módulo que lo publica (o a `shared.evento`); (d) documentar en un ADR la regla vigente —"lectura cruzada de repositorios permitida, escritura sólo por puerto o evento", por ejemplo— y hacerla verificable con un test de ArchUnit. Un ADR que reconozca el acoplamiento como deuda aceptada vale más ante un jurado que un refactor a medias.

**Confianza:** ALTA

---

**ID:** AUD-020
**Severidad:** ALTA
**Categoría:** Backend / Modelo de datos

**Problema:**
La duración de una Sesión no se persiste en la Reserva: se deriva en cada lectura de la franja que cubre el horario. Si el Tutor edita o desactiva esa franja, cambia retroactivamente la duración de reservas ya pagadas y confirmadas.

**Evidencia:**
- `reservas.reservas` no tiene columna de duración (V9:49-69).
- `backend/.../reservas/service/FranjaService.java:90-93` — `duracionFranjaQueCubre` = `Duration.between(horaInicio, horaFin)` de la franja activa que cubre el instante.
- `backend/.../aula/SesionService.java:126-137` — `programarSesion` usa esa duración para agendar `CorteAutomaticoJob` y para fijar `duracionAgendadaSegundos`.
- `SesionService.java:155-159` — `reprogramarSesionProgramada` **recalcula** la duración con la franja del nuevo horario.
- `SesionService.java:126-130` — si la franja ya no existe, lanza `IllegalStateException` → 500.
- Consecuencia de negocio: la tarifa es "por sesión" (`TarifaTutor.precioSesion`), así que una franja de 30 min y una de 180 min cuestan lo mismo.

**Por qué importa:**
Un dato con efecto monetario (FR-AULA-005 usa el 50% de la duración agendada para decidir entre reembolso y liberación) depende de una fila que el Tutor puede editar en cualquier momento. Es lo opuesto al criterio de FR-PAG-013, que congela el precio precisamente por esta razón.

**Escenario:**
Reserva confirmada para el lunes 10:00, franja 10:00–12:00, `duracionAgendadaSegundos = 7200`. El Tutor edita la franja a 10:00–10:30 antes de la sesión. `programarSesion` ya corrió, así que el job de corte queda en 12:05 — pero si la Reserva se reprograma, `reprogramarSesionProgramada` recalcula a 1800s, y el umbral del 50% que decide reembolso vs. liberación pasa de 60 min a 15 min sobre la misma plata.

Variante peor: el Tutor desactiva la franja. Si la Reserva se reprograma, `franjaQueCubre` devuelve vacío → `IllegalStateException` → 500 y la reprogramación falla sin mensaje útil.

**Recomendación:**
Congelar `duracion_minutos` en `reservas.reservas` al crear la Reserva, igual que se congela `precio` (FR-PAG-013). Es la misma regla y el mismo motivo. Resuelve además la mitad de AUD-009. Evaluar si la tarifa debe ser por hora en vez de por sesión, o si la franja debe tener duración fija por bloque — hoy el modelo de precio y el de disponibilidad no están alineados.

**Confianza:** ALTA

---

**ID:** AUD-021
**Severidad:** MEDIA
**Categoría:** Seguridad

**Problema:**
`subirEvidencia` acepta cualquier URL http(s) provista por el cliente y la persiste en la Alerta de Seguridad que el Admin va a revisar. Se acepta `http://` explícitamente.

**Evidencia:**
`backend/.../aula/SesionService.java:579-581`
```java
if (!(clipUrl.startsWith("https://") || clipUrl.startsWith("http://"))) {
    throw new EvidenciaInvalidaException("la URL del clip debe ser http(s).");
}
```
No hay allowlist de dominio, ni verificación de que el recurso sea un clip, ni de que lo haya subido Tinku. Constitución, NFR de Seguridad: *"TLS 1.2+"*.

**Por qué importa:**
La "evidencia" del kill-switch es lo que un Admin abre para decidir si sanciona a alguien. Un enlace controlado por el atacante es un vector de phishing dirigido al Admin, y el `http://` viola el NFR de transporte del propio proyecto.

**Escenario:**
El detectado (o cualquier participante) llama a `POST /api/sesiones/{id}/evidencia` con una URL que apunta a una página clonada del panel de Tinku. El Admin de Moderación abre la "evidencia" desde la cola y entrega sus credenciales.

**Recomendación:**
La evidencia debería subirse como archivo a través del puerto `Almacenamiento` (como las credenciales), no como URL declarada. Si se mantiene la URL: exigir `https://` y restringir a un dominio de storage propio configurado.

**Confianza:** ALTA

---

**ID:** AUD-022
**Severidad:** MEDIA
**Categoría:** Arquitectura

**Problema:**
Los eventos de dominio viven en el paquete del módulo **consumidor**, no del emisor. Es la causa directa de que `aula` importe `pagos`.

**Evidencia:**
- `com.tinku.pagos.evento.SesionFinalizadaEvent`, `SesionInterrumpidaEvent`, `SesionNoShow*Event`, `SesionKillswitch*Event` — los publica `aula`.
- `com.tinku.pagos.evento.DenunciaRegistradaEvent` — lo publica `seguridad`.
- `com.tinku.reservas.evento.DenunciaResueltaEvent` — lo publica `seguridad`.
- Contrasta con `com.tinku.seguridad.evento.SancionAplicadaEvent`, que sí está bien ubicado.

**Por qué importa:**
El contrato de un evento es del emisor. Con esta disposición, agregar un segundo consumidor de `sesion.finalizada` obliga a ese módulo a importar `pagos`, que no tiene nada que ver. De hecho ya pasó: `resumen` importa `pagos` sólo para escuchar `SesionFinalizadaEvent`.

**Escenario:**
Se quiere quitar M5 del proyecto para una demo. No se puede: M3, M6 y M9 no compilan sin el paquete `pagos.evento`.

**Recomendación:**
Mover cada evento al módulo que lo publica, o a `com.tinku.shared.evento`. Es un refactor mecánico de bajo riesgo (sólo imports) y mejora directamente el argumento de modularidad de la defensa.

**Confianza:** ALTA

---

**ID:** AUD-023
**Severidad:** MEDIA
**Categoría:** API / Manejo de errores

**Problema:**
`ReservasExceptionHandler` captura **toda** `DataIntegrityViolationException` del paquete `com.tinku.reservas` y la traduce a `409 "El horario ya está reservado para ese Tutor o beneficiario"`.

**Evidencia:**
`backend/.../reservas/web/ReservasExceptionHandler.java:77-81` — sin inspeccionar el nombre de la constraint violada.

**Por qué importa:**
Cualquier violación de FK, de NOT NULL o de CHECK en el módulo reservas se le reporta al usuario y al cliente como un conflicto de horario. Hace que un bug real sea indistinguible de una condición de carrera esperada, y el mensaje engañoso llega al frontend (que lo usa para el flujo de reintento de T-M4-14).

**Escenario:**
Un bug introduce un `tutor_id` inválido en `crearDirecta`. El usuario ve "el horario ya está reservado", reintenta con otro horario, falla igual, y en los logs no queda rastro de la causa real.

**Recomendación:**
Inspeccionar el nombre de la constraint (`ConstraintViolationException.getConstraintName()`) y traducir a 409 sólo `ex_reservas_sin_superposicion_*`. El resto → 500 con log de error.

**Confianza:** ALTA

---

**ID:** AUD-024
**Severidad:** MEDIA
**Categoría:** Documentación vs implementación

**Problema:**
M6 (Resumen Automático) no puede generar un resumen nunca, por dos motivos independientes: no hay proveedor de LLM (documentado y aceptado) **y** no hay proveedor de transcript (no documentado como bloqueante). El bean por defecto de `TranscriptSesionProveedor` devuelve `null` siempre.

**Evidencia:**
- `backend/.../resumen/port/TranscriptSesionProveedorNoDisponible.java` → `return null;` — única implementación.
- `backend/.../resumen/service/ResumenService.java:242-249` — `crudo == null` → `ESTADO_FALLIDO`, se cancela el reintento.
- `docs/specs/Spec_M6_Resumen_Automatico.md:5` — *"**Depende de:** M3 (transcript de la sesión + evento `sesion.finalizada`)"*. M3 no produce transcript: `LiveKitService` no usa Egress, y `sesiones_aprendizaje` no tiene columna de transcript.
- `docs/Tasks_Tinku_Chunks.md` marca M6-D como parcial **sólo por el ADR del LLM**; el transcript no figura como pendiente.

**Por qué importa:**
El chunk registra un bloqueante y omite el otro. En una defensa, "M6 está completo salvo el ADR del proveedor" es refutable en una pregunta: aunque mañana se elija GPT-4o, el módulo sigue sin poder generar nada porque no hay texto de entrada.

**Escenario:**
El jurado pregunta "¿y si mañana firman con OpenAI, M6 funciona?". La respuesta honesta es no: falta LiveKit Egress, storage del audio, y la decisión de retención de ese audio contra el Artículo V — que no está tomada.

**Recomendación:**
Agregar el transcript como pendiente explícito en `Tasks_Tinku_Chunks.md` con el mismo nivel de detalle con el que está T-M3-06. Y anticipar la decisión de retención: grabar audio de sesiones con menores es exactamente lo que el Artículo V restringe, así que el ADR de Egress es más delicado que el del LLM.

**Confianza:** ALTA

---

**ID:** AUD-025
**Severidad:** MEDIA
**Categoría:** Backend / Modelo de datos

**Problema:**
`FranjaService.publicar()` no valida que la nueva franja no se superponga con otra ya publicada del mismo Tutor, y `franjaQueCubre()` resuelve la ambigüedad tomando **la primera** franja que matchea (ordenada por `horaInicio`). El comentario de V9 afirma modelar "la no-superposición de modo de publicación", pero no hay constraint.

**Evidencia:**
- `backend/.../reservas/service/FranjaService.java:40-61` (`publicar`) — valida tipo de usuario, orden de horas y duración 30–180 min. Nada de solapamiento.
- `FranjaService.java:78-84` — `.filter(...).findFirst()`
- `V9__m4_reservas.sql:9-26` — sin constraint de exclusión sobre `franjas_disponibilidad`.
- Además, `franjaQueCubre` carga **todas** las franjas activas del Tutor y filtra en memoria en cada llamada.

**Por qué importa:**
La franja que cubre un horario determina la duración agendada de la Sesión (AUD-020), el job de corte automático, y el umbral del 50% que decide reembolso vs. liberación. Con franjas solapadas, ese valor depende del orden de `horaInicio`, que es arbitrario respecto de la intención del Tutor.

**Escenario:**
Tutor publica lunes 10:00–12:00 y luego lunes 11:00–11:30 (para ofrecer un bloque corto). Una reserva a las 11:15 cae en ambas. `findFirst` devuelve la de 10:00 → la sesión se agenda con 120 min de duración y el corte automático a las 12:05, cuando el Tutor ofrecía 30.

**Recomendación:**
`EXCLUDE` sobre `franjas_disponibilidad` con rango de horas por `(tutor_id, dia_semana)` y por `(tutor_id, fecha_especifica)` — la extensión `btree_gist` ya está instalada por V9. Validación equivalente en `publicar()` con 422. Convertir `franjaQueCubre` en query, no en filtro en memoria.

**Confianza:** ALTA

---

**ID:** AUD-026
**Severidad:** MEDIA
**Categoría:** Frontend / Manejo de errores

**Problema:**
`getCatalogos()` devuelve un fixture local (`catalogoMock`) ante cualquier error que no sea un `ApiError` 4xx distinto de 404 — incluidos fallos de red y caídas del backend.

**Evidencia:**
`frontend/src/lib/api.ts:166-173`
```ts
.catch((err) => {
  if (err instanceof ApiError && err.status !== 404) throw err;
  return catalogoMock;
});
```
Un `TypeError: Failed to fetch` no es `ApiError` → cae al mock.

**Por qué importa:**
Con el backend caído, el usuario ve un catálogo de temas que parece real y elige de él. La búsqueda posterior falla o devuelve nada, sin que el usuario entienda por qué. El comentario del código (*"este fixture solo cubre red caída / endpoint 404"*) confirma que está hecho a propósito, pero el efecto es que una caída del sistema se presenta como datos válidos.

**Escenario:**
El backend se reinicia durante un deploy. Un Adulto Responsable entra a `/buscar`, ve el catálogo completo, arma su búsqueda y obtiene cero resultados. Reporta "no hay Tutores de matemática" en vez de "el sistema está caído".

**Recomendación:**
Acotar el fallback a 404 estricto, o a `NODE_ENV !== 'production'`. Para los demás errores, mostrar el estado de error real.

**Confianza:** ALTA

---

**ID:** AUD-027
**Severidad:** MEDIA
**Categoría:** Seguridad / Sesiones

**Problema:**
Tres debilidades acumuladas del manejo de sesión:
1. El JWT se guarda en `localStorage` **y** en una cookie sin `httpOnly` ni `Secure` → recuperable por cualquier XSS.
2. El `sub` del JWT es el **DNI** — decodificable en base64 por cualquiera que tenga el token.
3. Resetear la contraseña no invalida los JWT ya emitidos: siguen siendo válidos hasta 60 minutos.

**Evidencia:**
- `frontend/src/lib/auth.ts:35-37` — `localStorage.setItem` + `document.cookie = ... SameSite=Lax` sin `httpOnly`/`Secure`.
- `backend/.../config/security/JwtUtil.java:34` — `.subject(dni)`.
- `backend/.../identidad/service/PasswordResetService.java:74-89` — cambia el hash y marca el token; no toca sesiones.
- `JwtAuthenticationFilter` valida contra `UsuarioDetailsService`, que sólo mira `estadoCuenta` — no una versión de credencial.

**Por qué importa:**
El punto 3 es el que más pesa: el flujo de recuperación de contraseña existe precisamente para el caso de cuenta comprometida, y no expulsa al atacante. Combinado con AUD-008 (token en logs), el atacante que tomó la cuenta conserva acceso una hora después de que la víctima "la recupera".

**Escenario:**
Un padre sospecha que la cuenta de su hijo fue comprometida y resetea la contraseña. El atacante, con un JWT emitido 5 minutos antes, sigue operando 55 minutos más: puede ver reservas, entrar al aula y obtener tokens de LiveKit.

**Recomendación:**
Agregar `credentials_version` (o `password_changed_at`) a `usuarios`, incluirlo como claim y verificarlo en `JwtAuthenticationFilter`. Cambiar el `sub` a UUID. Marcar la cookie como `Secure` fuera de dev. El punto 1 (localStorage) es un trade-off defendible para el alcance; documentarlo como tal.

**Confianza:** ALTA

---

**ID:** AUD-028
**Severidad:** MEDIA
**Categoría:** Backend / Negocio

**Problema:**
Cuando el pagador y el beneficiario son personas distintas (Adulto Responsable + menor), **ambos** pueden dejar una calificación pública `estudiante_a_tutor` sobre la misma sesión. La unicidad es `(sesionId, autorId, direccion)`.

**Evidencia:**
- `backend/.../reputacion/service/CalificacionService.java:62-64` — `findBySesionIdAndAutorIdAndDireccion(...)`
- `CalificacionService.java:135-143` (`derivarDireccion`) — `beneficiario` **o** `pagador` → `DIR_ESTUDIANTE_A_TUTOR`.
- `CalificacionService.java:85-87` — `propia()` usa `findBySesionIdAndAutorId`, que devuelve `Optional`: con dos filas para el mismo autor no aplica, pero el modelo de "una calificación por sesión" no se sostiene.

**Por qué importa:**
Una sesión pesa el doble en el promedio del Tutor que otra idéntica con un Estudiante adulto. FR-REP-007 usa un umbral de 5 calificaciones para mostrar el promedio; ese umbral se alcanza con la mitad de sesiones en el caso menor. Sesga la reputación de forma no documentada.

**Escenario:**
Un Adulto Responsable disconforme califica con 1 estrella, y hace que su hijo (que tiene cuenta propia) también califique con 1. La misma sesión aporta dos calificaciones negativas.

**Recomendación:**
Decidir explícitamente: una calificación pública por sesión (unicidad `(sesionId, direccion)`), o una por persona con el sesgo documentado en el Spec de M7. La primera es la que coincide con "el promedio del Tutor refleja sus sesiones".

**Confianza:** MEDIA — depende de una intención de producto que el Spec no fija.

---

**ID:** AUD-029
**Severidad:** MEDIA
**Categoría:** Funcionalidad incompleta / Documentación vs implementación

**Problema:**
El webhook de LiveKit sólo procesa `participant_joined`. No hay manejo de `participant_left`, `room_finished` ni `track_unpublished`. Toda la rama de conectividad y desconexión del Spec M3 queda sin implementación del lado del servidor.

**Evidencia:**
- `backend/.../aula/web/LiveKitWebhookController.java` — `if ("participant_joined".equals(evento.path("event").asText()))` y nada más.
- `docs/specs/Spec_M3_Aula_Virtual.md:44` (US-5) — *"Dado que la sesión se corte por completo (ambas partes desconectadas, sin reconexión) antes de cumplirse el 50%… cuando el sistema lo determine"*.
- `Spec_M3:72` (US-8) — *"…la sesión queda `finalizada_anticipada` cuando la otra parte también salga"*.
- `SesionAprendizaje.ESTADO_FINALIZADA_ANTICIPADA` sólo se asigna en `ejecutarNoShow` (`SesionService.java:257`), nunca por desconexión.
- FR-AULA-002/003 (degradación por conectividad) viven sólo en el cliente (`frontend/src/app/aula/[id]/page.tsx`, `UMBRAL_LECTURAS`).

**Por qué importa:**
El sistema no sabe cuándo termina realmente una sesión: sólo sabe cuándo empieza (primer join) y cuándo vence el horario agendado. La duración efectiva se calcula contra `Instant.now()` en el momento del corte programado, no contra la última desconexión real — de ahí sale el umbral del 50% que decide reembolso vs. liberación.

**Escenario:**
Tutor y Estudiante se conectan a las 10:00 y ambos se caen a las 10:05 sin volver. Nadie presiona "Finalizar". El job de corte corre a las 12:05 y calcula `duracionEfectiva = 10:00 → 12:05 = 125 min` sobre una agendada de 120 → **no** se considera corte antes del 50% → `sesion.finalizada` → el escrow se libera al Tutor por una sesión de 5 minutos.

**Recomendación:**
Manejar `participant_left` y `room_finished` en el webhook, registrando `ultima_desconexion_at`. Calcular la duración efectiva contra ese timestamp, no contra `Instant.now()` del job. Es un bug monetario concreto, no sólo una brecha de cobertura.

**Confianza:** ALTA

---

**ID:** AUD-030
**Severidad:** MEDIA
**Categoría:** Documentación

**Problema:**
`docs/Tasks_Tinku_Implementacion.md` —declarado por AGENTS.md como "la memoria persistente entre sesiones" y por el propio `Tasks_Tinku_Chunks.md` como "la fuente de verdad atómica"— está desactualizado en ambas direcciones y contradice al archivo de chunks.

**Evidencia:**
- `docs/Tasks_Tinku_Implementacion.md:9-17` — **T-000-01 a T-000-09 todas sin tildar**, mientras `Tasks_Tinku_Chunks.md` marca 000-A a 000-E como `[x]` (y el código existe).
- `docs/Tasks_Tinku_Implementacion.md:113` — T-M4-12 sin tildar, pero `HorariosDisponiblesService` + `GET /api/tutores/{id}/horarios` existen y tienen tests (`ReservasFlujosIntegracionTest:569`).
- 14 tareas `[ ]` contra 115 `[x]`; al menos 10 de las 14 son falsos negativos.
- `NOTAS_VERIFICACION.md` sigue documentando como logro el circuito del CAP (US-6, FR-ID-021 a 025), retirado por ADR-M1-02, y describe la suite como "102 tests" cuando hoy son 383.
- El chunk 000-F ("CI mínimo") está marcado `[~]` y sigue parcial: no hay pipeline para `matching-service/`.

**Por qué importa:**
Es el documento que AGENTS.md instruye mantener en cada sesión, y que el archivo de chunks designa como fuente de verdad. Si dos documentos de gobernanza se contradicen, ninguno de los dos sirve como evidencia ante un jurado, y el próximo agente que trabaje sobre el repo no sabe cuál creer.

**Escenario:**
En la defensa se pide evidencia de trazabilidad requisito → tarea → código. Se abre `Tasks_Tinku_Implementacion.md` y toda la Fase 0 aparece sin hacer, en un proyecto que compila, corre y tiene 383 tests verdes.

**Recomendación:**
Reconciliar `Tasks_Tinku_Implementacion.md` contra el código (la dirección que ya se hizo una vez con el archivo de chunks el 2026-09-18) y decidir cuál de los dos es la fuente de verdad — mantener dos checklists sincronizadas a mano no es sostenible por una persona. Marcar `NOTAS_VERIFICACION.md` como registro histórico de un branch cerrado, con fecha, o borrarlo.

**Confianza:** ALTA

---

**ID:** AUD-031
**Severidad:** MEDIA
**Categoría:** Testing / CI

**Problema:**
Dos brechas en la red de seguridad automatizada:
1. **`matching-service/test_main.py` no corre en ningún pipeline.** Los workflows de CI sólo cubren `backend/**` y `frontend/**`, con filtros de path.
2. **Los E2E de Playwright mockean `/api/**` por completo.** No existe ninguna prueba que ejercite frontend contra backend real.

**Evidencia:**
- `.github/workflows/` contiene sólo `ci-backend.yml` (`paths: ["backend/**"]`) y `ci-frontend.yml` (`paths: ["frontend/**"]`).
- `ci-frontend.yml` — *"Los tests E2E mockean `/api/**` — no necesitan el backend Java levantado."*
- `docs/Tasks_Tinku_Chunks.md` — Chunk 000-F marcado `[~]`, reconoce la falta del pipeline de Python.

**Por qué importa:**
El contrato HTTP entre frontend y backend no está verificado en ninguna capa. El propio repo tiene evidencia de que esto muerde: `frontend/src/lib/api.ts:275-281` documenta un caso donde el tipo del frontend decía `"APROBADA"/"RECHAZADA"` y el enum Java serializaba `"APROBADO"/"RECHAZADO"` — nadie lo detectó hasta una revisión manual.

**Escenario:**
Se renombra un campo de un DTO en el backend. CI backend verde (383 tests). CI frontend verde (E2E contra mocks con el nombre viejo). La pantalla se rompe en runtime y nadie se entera hasta abrirla a mano.

**Recomendación:**
Workflow para `matching-service/**` (`uv run pytest`). Un smoke E2E contra el stack real de docker-compose (registro → login → buscar → reservar → pagar en bypass) que corra en CI, aunque sea nocturno. Alternativa más barata: tests de contrato sobre los DTO serializados.

**Confianza:** ALTA

---

**ID:** AUD-032
**Severidad:** BAJA
**Categoría:** Configuración / Dependencias

**Problema:**
Spring Boot 3.3.4 (septiembre 2024), sin escaneo de dependencias ni actualizaciones automáticas.

**Evidencia:**
- `backend/pom.xml:10` → `<version>3.3.4</version>`
- No hay `dependabot.yml`, ni `owasp-dependency-check`, ni paso de `mvn versions:display-dependency-updates` en CI.
- Frontend: Next.js `^14.2.0` (Next 15 lleva más de un año publicado).

**Por qué importa:**
Un año de parches acumulados en la rama 3.3.x, en un sistema con datos de menores. No es explotable per se, pero es higiene que el NFR de Seguridad implica.

**Escenario:**
Una CVE en una transitiva de Spring Boot 3.3.4 queda sin detectar porque nada mira.

**Recomendación:**
Dependabot (gratis en GitHub) o un paso de `dependency-check` en CI. Actualizar a la última 3.3.x/3.4.x. Es de las acciones de mejor relación costo/beneficio del informe.

**Confianza:** ALTA

---

**ID:** AUD-033
**Severidad:** MEDIA
**Categoría:** Backend / Máquina de estados

**Problema:**
`marcarAprobada` y `marcarRechazada` no verifican que la credencial esté en `PENDIENTE`. Un Admin puede aprobar una credencial ya rechazada, o re-rechazar una aprobada, y cada llamada dispara sus efectos colaterales.

**Evidencia:**
`backend/.../identidad/service/CredencialService.java:96-127` — ninguno de los dos métodos consulta el estado previo.
Efectos: `marcarAprobada` → `activoParaMatching = true`; `marcarRechazada` → si `numeroIntento >= 3`, `backoffService.registrarCicloAgotado(...)`, que es acumulativo (24h × 2ⁿ).

**Por qué importa:**
Contrasta con el rigor del resto del sistema, donde cada transición tiene un guard (`EscrowService` sólo desde `RETENIDO_ESCROW`, `DenunciaService.resolver` exige `EN_REVISION`, `ejecutarNoShow` exige `CONFIRMADA`). Acá falta, y el efecto es reactivar el matching de un Tutor rechazado.

**Escenario:**
Un Admin rechaza la credencial de un Tutor. Por error de UI o doble click, hace `POST .../resolver` con `APROBAR` sobre el mismo id. El Tutor queda con `estado = APROBADO` y `activo_para_matching = true`, sin que nadie haya revisado nada nuevo. Variante: rechazar dos veces el intento 3 duplica el backoff.

**Recomendación:**
Guard `if (c.getEstado() != PENDIENTE) throw ...` en ambos métodos, traducido a 422. Añadir el caso al test de la cola de credenciales.

**Confianza:** ALTA

---

**ID:** AUD-034
**Severidad:** BAJA
**Categoría:** Configuración / Observabilidad

**Problema:**
No existe configuración de producción ni observabilidad más allá de logs de texto. Concretamente:
- No hay `application-prod.yml` ni perfil `prod`, pero `SaludInfraestructuraService` lo consulta (`environment.acceptsProfiles("prod")`) → `isTestMode` siempre `true`.
- `JWT_SECRET` tiene un default literal (`CAMBIAR_EN_TODOS_LOS_AMBIENTES...`) que permite arrancar sin secret real, sin fallar.
- `spring.datasource.url` está hardcodeada a `localhost:5432` en `application.yml` (sólo user/password vienen de env).
- No hay Actuator, ni métricas, ni health checks propios, ni correlación de requests.
- `isClustered: false` en Quartz: el sistema no soporta más de una instancia sin duplicar jobs.

**Evidencia:**
`backend/src/main/resources/application.yml:11-19, 43, 65`; `backend/.../admin/service/SaludInfraestructuraService.java:70`; `fd . backend/src/main/resources -d 1` → un solo archivo.

**Por qué importa:**
Es la brecha entre "corre en la máquina del desarrollador" y "corre en algún lado". Para el alcance de piloto y un desarrollador es defendible que no haya Kubernetes ni Prometheus; no lo es que la app arranque en silencio con un secret placeholder.

**Escenario:**
Se despliega a un PaaS. `JWT_SECRET` no se setea (nadie lo nota porque no falla). Todos los tokens del sistema quedan firmados con una clave que está en el repositorio público.

**Recomendación:**
Fallar el arranque si `tinku.jwt.secret` es el placeholder y el perfil no es `dev`/`test`. Crear `application-prod.yml` aunque sea mínimo. Añadir Actuator con `/health` y `/info` (cero costo, ya está en el classpath de Spring Boot). El `isClustered: false` está bien documentado y es coherente con el Artículo VII — dejarlo, pero mencionarlo en la defensa.

**Confianza:** ALTA

---

**ID:** AUD-035
**Severidad:** BAJA
**Categoría:** Deuda técnica / DB

**Problema:**
La migración `V6__m1_certificados_antecedentes_penales.sql` creó tablas del circuito de CAP que ADR-M1-02 retiró. Las tablas quedan en la base sin ningún código que las use, y `V21` limpió sólo las filas QRTZ_* huérfanas.

**Evidencia:**
`backend/src/main/resources/db/migration/V6__m1_certificados_antecedentes_penales.sql` (36 líneas); `rg -i "certificadoantecedentes"` sobre `src/main/java` → 0 clases. `CredencialService.java:28-30` lo reconoce: *"las tablas de la migración V6 quedan en la BD, sin uso"*.

**Por qué importa:**
Tablas fantasma que contradicen el Artículo V (minimización de datos) si alguna vez llegaran a tener filas con documentos de antecedentes penales. Hoy están vacías, así que el riesgo es de mantenibilidad y de claridad ante un jurado que revise el esquema.

**Escenario:**
El jurado abre el esquema y encuentra `certificados_antecedentes_penales` en un sistema cuya Constitución (v2.2) dice explícitamente que el CAP no se pide. Pregunta legítima y evitable.

**Recomendación:**
Migración nueva que dropee las tablas de V6 (nunca editar V6, según AGENTS.md §7), con un comentario que referencie ADR-M1-02. Es una migración de 3 líneas que elimina una pregunta incómoda.

**Confianza:** ALTA

---

**ID:** AUD-036
**Severidad:** BAJA
**Categoría:** Calidad de código / Performance

**Problema:** Varios ítems menores, agrupados porque ninguno justifica un finding propio.

**Evidencia y detalle:**
1. **Doble consulta del usuario por request.** `JwtAuthenticationFilter` llama a `loadUserByUsername(dni)` y luego cada controller llama a `usuarioActual.obtener(authentication)` → `findByDni(...)` otra vez. Dos SELECT por request autenticado. (`JwtAuthenticationFilter.java:44`, `shared/UsuarioActual.java:24`)
2. **`matching` sin capas internas.** 40 clases planas en `com.tinku.matching`, contra `model/repository/service/web` en los otros 8 módulos.
3. **`@Transactional` mezclado.** `jakarta.transaction.Transactional` en `UsuarioService`, `CredencialService`, `PasswordResetService`; `org.springframework...` en el resto. Cambia la semántica disponible (`readOnly`, `propagation`) sin motivo.
4. **`franjaQueCubre` filtra en memoria** todas las franjas activas del Tutor en cada llamada; se invoca por reserva, por sesión y por slot del picker.
5. **`matching-service` abre una conexión por operación**; `/recompute-embeddings` abre N+1 conexiones sin pool ni transacción envolvente.
6. **`Usuario.getEdad()`** es un getter público sin `@JsonIgnore`: se serializa como campo `edad` en cualquier DTO que exponga la entidad (hoy ninguno lo hace, pero la protección es por convención).
7. **`_embedder` lazy sin sincronización** en `matching-service/main.py:163-171` → dos requests concurrentes iniciales pueden cargar el modelo dos veces.

**Por qué importa:** Ninguno rompe nada hoy. El (1) y el (4) son costo constante bajo carga; el (3) es una inconsistencia que un revisor va a preguntar; el (2) debilita el argumento de modularidad.

**Recomendación:** Tratarlos como limpieza oportunista, no como trabajo dedicado. El (1) se resuelve poniendo el `Usuario` (o su id) en el principal del filtro. El (3) es un reemplazo mecánico de imports.

**Confianza:** ALTA

---

## 4. Documentation Drift

Comparación explícita entre lo que la documentación afirma y lo que el código hace.

### 4.1 Funcionalidad documentada, no implementada

| Documento | Afirma | Realidad | Finding |
|---|---|---|---|
| `Spec_M3:50` | "la sesión **se corta para ambos**" | Sólo cambia estado en BD; la sala de LiveKit sigue viva | AUD-001 |
| `Spec_M3:50` | "se notifica inmediatamente al Adulto Responsable" | No existe infraestructura de notificación | AUD-014 |
| `Spec_M3:50` | "el buffer de 30s **se sube y persiste**" | `subirEvidencia` existe; ningún cliente la llama (sin MediaRecorder en `frontend/`) | AUD-014, T-M3-06 |
| `Spec_M3:87` (FR-AULA-010) | "no se habilita la sala si el clasificador no carga" (menor presente) | `obtenerToken` no consulta nada del clasificador | AUD-001 |
| `Spec_M3:44,72` (US-5, US-8) | corte por desconexión → `finalizada_anticipada` / `sesion.interrumpida` | El webhook sólo maneja `participant_joined` | AUD-029 |
| `Spec_M6:5` | "Depende de M3 (**transcript de la sesión**)" | M3 no genera transcript; el puerto devuelve `null` | AUD-024 |
| `V9:11` | "la no-superposición de modo de publicación" | No hay constraint de superposición en `franjas_disponibilidad` | AUD-025 |
| `V9:74-80` | "prevención de reservas superpuestas a nivel de BASE" | La `EXCLUDE` sólo cubre horarios idénticos | AUD-009 |
| `CredencialColaResponse:14` | "la revisión visual del archivo es del frontend interno" | Ese frontend interno no existe, ni el endpoint que lo alimentaría | AUD-007 |
| Constitución, Art. I (v2.2) | "el mecanismo de confianza vigente es la Credencial Académica" | La Credencial se aprueba sin ver el documento | AUD-007 |

### 4.2 Documentación que contradice al código

| Ubicación | Dice | Código |
|---|---|---|
| `LiveKitService.java:73` | "el participante **no puede** abrir otra sala con este token" | `roomCreate: true`, `roomAdmin: true` (línea 84) — AUD-002 |
| `TutorPerfilResponse.java:12` | "nunca expone passwordHash ni DNI" | El DNI viaja como identity de LiveKit y se renderiza en pantalla — AUD-003 |
| `middleware.ts:6` | "Protección de rutas desde el server" | Sólo verifica presencia de cookie — AUD-016 |
| `application.yml:10` | "NUNCA ese set en prod" (sobre el perfil dev) | `profiles.active: dev` está dentro del artefacto y `Dockerfile` no lo cambia — AUD-004 |
| `Tasks_Tinku_Chunks.md` (M6-D) | parcial "sólo por el ADR del LLM" | También falta el transcript, que no figura — AUD-024 |

### 4.3 Contradicciones entre documentos de gobernanza

| Ítem | `Tasks_Tinku_Implementacion.md` | `Tasks_Tinku_Chunks.md` | Código |
|---|---|---|---|
| T-000-01 … T-000-09 | todas `[ ]` | 000-A..E `[x]`, 000-F `[~]` | implementado |
| T-M4-12 | `[ ]` | (dentro de M4, cerrado) | `HorariosDisponiblesService` + test |
| CAP (FR-ID-021..025) | listado como logro en `NOTAS_VERIFICACION.md` | — | retirado por ADR-M1-02; V6 huérfana |
| Suite de tests | "102 tests" (`NOTAS_VERIFICACION.md`) | "320 tests" | **383 tests** (verificado hoy) |

### 4.4 Implementado y no documentado / decidido sin ADR

Decisiones vigentes en el código que AGENTS.md §2 exigiría documentar como ADR y no tienen uno:

| Decisión | Dónde vive | Por qué requiere ADR |
|---|---|---|
| DNI como `sub` del JWT y como identity de LiveKit | `JwtUtil:34`, `SesionService:286` | Artículo V (minimización), dato de menor |
| JWT en `localStorage` + cookie no-httpOnly | `frontend/src/lib/auth.ts` | Decisión de seguridad de sesión |
| Proveedor de email/SMS = log de aplicación | `NotificadorResetPasswordLog` | El javadoc lo reconoce: "decisión de infraestructura con su propio ADR que todavía no se tomó" |
| Storage de credenciales = filesystem local con URI `file:` | `AlmacenamientoLocal` | Citado como "ADR pendiente" en `NOTAS_VERIFICACION.md`, nunca escrito |
| Acceso cruzado a repositorios entre módulos | 27 imports | Desviación del Artículo VIII → AGENTS.md §2 lo exige |
| Eventos alojados en el módulo consumidor | `pagos.evento`, `reservas.evento` | Desviación del Artículo IX |
| Anonimización por regex + diccionario en vez de NER | `AnonimizadorTranscript` | El propio javadoc dice "ADR-M6-01 pendiente" |

---

## 5. Security Review

### 5.1 Resumen por dominio

| Dominio | Estado | Findings |
|---|---|---|
| Autenticación | Débil: sin límite de intentos, DNI como usuario, password sólo `min=8` | AUD-012, AUD-027 |
| Autorización de usuario final | Sólida en reservas/sesiones/calificaciones/resumen (chequeo de participación consistente); **rota en denuncias** | AUD-011 |
| Autorización de Admin | **Fortaleza**: gate por rol + filter chain + auditoría en los 18 endpoints | — |
| Escalada de privilegios | Vía token de LiveKit (`roomAdmin`/`roomCreate`) | AUD-002 |
| IDOR / BOLA | `POST /api/denuncias` contra cualquier usuario y cualquier sesión | AUD-011 |
| Validación de input | Bean Validation consistente en DTOs; falta en URLs de evidencia y en content-type de archivos | AUD-021, AUD-007 |
| Inyección | Sin riesgo detectado: JPA con parámetros, psycopg con placeholders, sin SQL dinámico | — |
| Exposición de información | DNI de menor visible al otro participante; token de reset en logs | AUD-003, AUD-008 |
| Manejo de errores | Bueno en general; una excepción sobre-capturada | AUD-023 |
| Secretos | `JWT_SECRET` con default funcional; `.env` correctamente en `.gitignore` y nunca commiteado (verificado con `git log`) | AUD-034 |
| Tokens / sesiones | Sin revocación tras reset de contraseña; TTL 60 min | AUD-027 |
| CORS | **Correcto**: allowlist explícita, sin comodín, `allowCredentials` coherente | — |
| CSRF | Correctamente deshabilitado (API stateless con Bearer, no cookies de sesión de servidor). **Pero** la cookie `tinku_jwt` con `SameSite=Lax` no se usa para autenticar en el backend, así que no reintroduce el riesgo | — |
| Rate limiting | **Inexistente en todo el sistema** | AUD-012 |
| Endpoints administrativos | Bien protegidos | — |
| Datos sensibles de menores | DNI expuesto; baja de perfil rota; sin notificación al Adulto Responsable | AUD-003, AUD-017, AUD-014 |
| Archivos | Sin validación de content-type ni visor; URL de evidencia arbitraria | AUD-007, AUD-021 |
| Integraciones externas | Webhooks con HMAC fail-closed (**fortaleza real**); matching-service sin auth | AUD-015 |

### 5.2 Lo que está bien hecho y conviene no tocar

- **Verificación de firma de webhooks.** `LiveKitWebhookVerificador` y `MercadoPagoWebhookVerificador` son fail-closed con anti-replay, y tienen tests unitarios dedicados (6 y 9 tests). Sin firma válida → 401, sin excepciones.
- **Reconciliación defensiva del pago.** El webhook no confía en el payload: consulta `GET /v1/payments/{id}` y compara el monto contra el precio congelado antes de crear el escrow.
- **`PasswordResetService`.** Token opaco de 32 bytes de `SecureRandom`, sólo el SHA-256 persistido, un solo uso, TTL de 1h, invalidación de tokens previos, y respuesta idéntica exista o no el DNI. El razonamiento de por qué **no** es un JWT está documentado y es correcto. (El problema es el notificador, no el servicio — AUD-008.)
- **El menor no puede pagar, autorizar Tutores ni denunciar.** Verificado en `ReservaService.exigirCapacidad*`, `AutorizacionService` y `DenunciaService.presentar`, y cubierto por tests (`articuloII_menorNuncaPuedeCrearReservaDirecta_queda403`, `articuloII_menorNuncaReprogramaNiCancela_queda403`).
- **La rama del kill-switch la decide el servidor**, no el request, y hay un test que ejercita el ataque explícito (`tM311_menor_killswitchForzandoRamaAdultos_ejecutaRamaMenor`).

### 5.3 Menores de edad — evaluación específica

El Artículo II es el eje declarado del proyecto. Contra él:

| Control | Estado |
|---|---|
| El menor no se autorregistra | ✅ Implementado y testeado |
| El menor no paga / no autoriza / no denuncia | ✅ Implementado y testeado |
| La rama del kill-switch no la decide el cliente | ✅ Implementado y testeado |
| La rama menor no le pregunta nada al menor | ✅ Implementado y testeado |
| Verificación documental de identidad | ❌ Stub en el perfil por defecto (AUD-004) |
| Verificación del Tutor (Credencial) | ❌ Se aprueba a ciegas (AUD-007) |
| El kill-switch efectivamente corta | ❌ No cierra la sala (AUD-001) |
| Detección de contenido inapropiado | ❌ No existe cliente (T-M3-06, ya documentado) |
| Aviso al Adulto Responsable | ❌ No existe (AUD-014) |
| Minimización de datos del menor | ❌ DNI expuesto al Tutor (AUD-003) |
| Derecho de supresión | ❌ `darDeBajaMenor` rompe con FK (AUD-017) |

Los cuatro controles que funcionan son de **autorización**. Los siete que no funcionan son de **protección efectiva**. El patrón es claro y consistente: lo que se puede verificar con un test de integración sobre HTTP + JPA está hecho y está bien hecho; lo que requiere integración real con un dispositivo, un proveedor externo o un canal de salida, no está.

---

## 6. Testing Gaps

383 tests verdes, con integración real sobre Postgres. La cobertura de reglas de negocio expresables como "request → estado en BD" es genuinamente buena. Lo que falta:

### 6.1 Reglas críticas sin protección

| Regla / componente | Hoy | Riesgo |
|---|---|---|
| El kill-switch cierra la sala de LiveKit | Ningún test lo verifica (porque no ocurre) | AUD-001 |
| El token de LiveKit no otorga privilegios de admin | `LiveKitServiceTest:81-91` verifica `roomJoin`, nunca asserta que `roomAdmin`/`roomCreate` sean `false` | AUD-002 |
| Reservas solapadas con horarios distintos | Los 3 tests de FR-RES-007 usan siempre el **mismo** `horario` | AUD-009 |
| Kill-switch con `detectadoId = menor` | Los 18 tests usan siempre `detectadoId = tutor` | AUD-006 |
| Abuso del kill-switch (sesión gratis) | Sin test | AUD-005 |
| Denuncia por un no-participante | Sin test | AUD-011 |
| Reactivación cruzada de sanciones | Sin test en ninguno de los dos caminos | AUD-013 |
| Baja de un menor **con historial de reservas** | `UsuarioServiceDarDeBajaTest` es unitario con mocks; nunca ejecuta el `DELETE` real | AUD-017 |
| Webhook de MP duplicado concurrente | `PagosWebhookIntegracionTest` prueba la idempotencia **secuencial**, no la concurrente | AUD-010 |
| Aprobar una credencial ya resuelta | Sin test | AUD-033 |
| Duración efectiva tras desconexión temprana | Sin test | AUD-029 |
| Contrato HTTP frontend ↔ backend | Cero cobertura (E2E 100% mockeado) | AUD-031 |
| `matching-service` | `test_main.py` existe y no corre en CI | AUD-031 |

### 6.2 Observación sobre la forma de los tests

El patrón dominante es "camino feliz + las negaciones que el Spec enumera". Funciona muy bien para reglas declarativas (quién puede hacer qué) y falla sistemáticamente para las que dependen de una **dimensión continua** (tiempo, solapamiento, concurrencia real). Los tres tests de superposición de reservas son el ejemplo exacto: los tres prueban la misma condición (`horario` idéntico) con tres envoltorios distintos, y ninguno prueba el solapamiento parcial, que es donde está el bug.

Recomendación transversal: para las reglas con dimensión continua, elegir deliberadamente los valores del **borde y del interior del rango**, no el caso canónico.

---

## 7. Thesis Defense Risks

Preguntas que un jurado puede formular a partir de decisiones reales encontradas en el repositorio, y de dónde sale cada una.

### 7.1 Preguntas sobre las que hoy no hay buena respuesta

**1. "El kill-switch es el corazón de su propuesta de valor. Muéstrenme dónde el código cierra la videollamada."**
Origen: `SesionService.cortar()` y la ausencia de `DeleteRoom` en `LiveKitService`.
Hoy la respuesta honesta es "no lo hace". Requiere AUD-001 resuelto antes de la defensa. No es negociable: es la pregunta que el jurado va a hacer, porque es lo que el título del trabajo promete.

**2. "Retiraron el certificado de antecedentes penales y se apoyaron en la Credencial Académica. ¿Cómo se valida una Credencial?"**
Origen: ADR-M1-02 + `CredencialColaResponse` sin `archivoUrl`.
El ADR-M1-02 es un documento excelente —justifica el retiro, enumera alternativas y declara el riesgo aceptado— pero el control compensatorio sobre el que se apoya no está operativo. Requiere AUD-007.

**3. "¿Qué datos del menor ve el Tutor?"**
Origen: `SesionService:286` + `etiquetaParticipante()`.
Ve el DNI, en pantalla. Requiere AUD-003.

**4. "Su Constitución dice monolito modular con límites de dominio claros. ¿Qué impide que un módulo acceda a los datos de otro?"**
Origen: 27 imports cruzados de repositorios, ciclo `shared ↔ admin`.
Hoy: nada. Hay puertos bien hechos y se usan a veces. No hace falta refactorizar todo antes de la defensa — hace falta **saber la respuesta**: un ADR que reconozca el acoplamiento como deuda medida, con el criterio vigente y el costo de revertirlo, es una respuesta defendible. "No sabía que estaba así" no lo es.

**5. "¿Qué pasa si dos estudiantes reservan horarios solapados del mismo Tutor?"**
Origen: `EXCLUDE ... horario WITH =`.
Se crean las dos. Requiere AUD-009.

**6. "¿Cómo se entera la familia de que hubo un incidente?"**
Origen: ausencia total de notificaciones.
No se entera. Requiere AUD-014, al menos el puerto y el caso del kill-switch.

**7. "El clasificador corre en el dispositivo del usuario. ¿Qué impide que alguien falsifique una detección?"**
Origen: ADR-M3-01 + `ejecutarKillswitch` sin evidencia obligatoria.
Nada. Esta es la pregunta más interesante del proyecto y la que mejor se puede convertir en fortaleza: el ADR-M3-01 eligió on-device por privacidad y costo (correcto), pero no analizó el modelo de amenaza del lado del cliente. Un anexo al ADR que reconozca el trade-off y describa los controles compensatorios es una respuesta más fuerte que cualquier parche.

### 7.2 Decisiones correctas que igual van a ser cuestionadas

Estas están bien tomadas. Lo que falta es la evidencia lista para mostrar.

**8. "¿Por qué Java y Spring Boot, y no Node o Go?"**
La Constitución dice "Decidido, evaluado contra Go y Node.js" — pero no hay ADR con esa evaluación. Es la decisión tecnológica más visible del trabajo y la única sin documento. **Escribir ADR-000-02 retroactivo** con los criterios reales (ecosistema de Quartz persistido, madurez de JPA, familiaridad del desarrollador único, Artículo VII).

**9. "¿Por qué un servicio Python separado si su Artículo VIII prohíbe microservicios?"**
Bien resuelto: es la única excepción, está declarada en el Artículo VIII y justificada por el ecosistema de sentence-transformers. La respuesta existe. Reforzarla mostrando que el servicio no tiene lógica de negocio (`MatchingOrquestador` lo hace explícito). Punto débil a anticipar: el servicio **escribe** en la base compartida (AUD-015).

**10. "¿Qué pasa si crecen a 10.000 usuarios?"**
Límites reales, conocibles hoy: `isClustered: false` (una sola instancia), sin pool en el servicio Python, doble query de usuario por request, `franjaQueCubre` filtrando en memoria, `AnonimizadorTranscript` por regex. La respuesta correcta es la del Artículo VII: estos límites son conscientes y el costo de levantarlos es conocido. Conviene llevar los números, no la intuición.

**11. "¿Por qué eventos en memoria y no un broker?"**
Excelente respuesta disponible: los listeners corren en la transacción del publicador, lo que da atomicidad real sin saga ni compensación — está documentado en `SancionListeners` y en `Plan_M9 §2.5`. Es una de las decisiones mejor razonadas del proyecto; vale la pena presentarla proactivamente.

**12. "¿Cómo garantizan que un timeout de negocio sobreviva a un reinicio?"**
La mejor respuesta del proyecto. `QuartzPersistenciaTest` lo demuestra, y el hallazgo del `driverDelegateClass` de `NOTAS_VERIFICACION.md` es una anécdota de ingeniería genuinamente buena para contar en una defensa.

### 7.3 Riesgo de presentación

El mayor riesgo no es técnico. Es que **la documentación afirma un grado de completitud que el código no tiene**, y el jurado lo va a descubrir leyendo el código, no la documentación. `Tasks_Tinku_Chunks.md` ya hace bien lo correcto con M3-C y M6-D: declara el pendiente y explica por qué. Extender ese mismo criterio de honestidad a los findings de este informe convierte una vulnerabilidad de defensa en una demostración de criterio de ingeniería.

---

## 8. Technical Debt

### 8.1 Deuda crítica — compromete seguridad o integridad

| Ítem | Finding |
|---|---|
| El kill-switch no cierra la sala de video | AUD-001 |
| Tokens de LiveKit con privilegios de administración | AUD-002 |
| DNI de menor expuesto al otro participante | AUD-003 |
| Perfil `dev` por defecto ⇒ OCR falso en cualquier despliegue | AUD-004 |
| Kill-switch disparable sin evidencia, con reembolso y suspensión | AUD-005 |
| Credencial Académica aprobada sin ver el documento | AUD-007 |
| Token de reset de contraseña en los logs | AUD-008 |

### 8.2 Deuda importante — calidad, cumplimiento o corrección

| Ítem | Finding |
|---|---|
| Rama menor suspende al Tutor sin importar el detectado | AUD-006 |
| Reservas solapadas admitidas por la API | AUD-009 |
| Sin unicidad en `transacciones` ⇒ escrow duplicado irrecuperable | AUD-010 |
| Denuncia contra cualquiera ⇒ congelamiento de escrow ajeno | AUD-011 |
| Cero rate limiting en todo el sistema | AUD-012 |
| Reactivación cruzada de sanciones | AUD-013 |
| Sin ninguna infraestructura de notificación | AUD-014 |
| `matching-service` sin autenticación, con escritura a la BD | AUD-015 |
| Baja de menor rota por FK | AUD-017 |
| Modo Bypass sin acotar ni expirar | AUD-018 |
| Límites de módulo inexistentes + ciclos | AUD-019 |
| Duración de sesión derivada y mutable | AUD-020 |
| Desconexiones no procesadas ⇒ duración efectiva errónea | AUD-029 |
| Checklists de gobernanza contradictorias | AUD-030 |
| Sin CI de Python; E2E 100% mockeado | AUD-031 |

### 8.3 Deuda aceptable para el alcance actual

Explícitamente **no** recomiendo tocar estos antes de cerrar el proyecto. Están bien razonados o su costo supera el beneficio en el alcance declarado:

- **`isClustered: false` en Quartz.** Documentado, coherente con un desarrollador y USD 0-100/mes.
- **Anonimización por regex + diccionario de nombres.** El javadoc declara el compromiso y la estrategia fail-safe ("ante la duda, enmascarar de más") es la correcta. El puerto está listo para el NER del ADR-M6-01.
- **JWT en `localStorage`.** Trade-off estándar; merece una línea de documentación, no un refactor.
- **Storage de credenciales en filesystem local.** Correctamente aislado tras el puerto `Almacenamiento`. Reemplazable sin tocar llamadores.
- **Ausencia de proveedor LLM y de transcript en M6.** Ya declarados como pendientes de ADR y no bloquean nada más (salvo actualizar la documentación, AUD-024).
- **`matching` sin subpaquetes de capa.** Cosmético.
- **Doble consulta del usuario por request.** Irrelevante en el volumen de un piloto.
- **Spring Boot 3.3.4.** Actualizar es barato; no hacerlo no bloquea nada (pero sí activar Dependabot).

---

## 9. Recommended Action Plan

Ordenado por impacto técnico y riesgo, no por facilidad.

### P0 — Resolver antes de continuar

Cada uno de estos compromete seguridad, integridad de datos o dinero real. Todos son pequeños en volumen de código.

| # | Acción | Finding | Tamaño |
|---|---|---|---|
| 1 | `VideoClaim(sala, true, **false**, **false**)` + aserciones negativas en `LiveKitServiceTest` | AUD-002 | 1 línea + 1 test |
| 2 | Cerrar la sala de LiveKit en `cortar()` (`RemoveParticipant` + `DeleteRoom`) y rechazar `/token` sobre sesiones cerradas | AUD-001 | ~40 líneas + tests |
| 3 | Identity de LiveKit = UUID, no DNI; claim `name` para la etiqueta de UI | AUD-003 | ~10 líneas backend + 5 frontend |
| 4 | Quitar `profiles.active: dev` del artefacto y abortar el arranque si `StubOcrService` está activo fuera de `dev`/`test` | AUD-004 | ~15 líneas |
| 5 | Dejar de loguear el token de reset (y el DNI) | AUD-008 | 2 líneas |
| 6 | `UNIQUE (mp_payment_id)` + `UNIQUE (reserva_id)` en `pagos.transacciones` (migración nueva) | AUD-010 | 1 migración |
| 7 | Exigir participación en `DenunciaService.presentar` + rechazar auto-denuncia | AUD-011 | ~15 líneas + tests |
| 8 | `ramaMenor` suspende a `detectadoId`, no a `reserva.getTutor()` — o documentar la intención en el Spec y arreglar la resolución de la Alerta | AUD-006 | ~5 líneas + test |
| 9 | Endpoint de visualización del archivo de la Credencial, gateado por `requiereModeracion` | AUD-007 | ~30 líneas + frontend |
| 10 | Guard de sanción vigente antes de reactivar (`AlertaSeguridadService`, `CredencialService`) | AUD-013 | ~15 líneas + tests |
| 11 | Fallar el arranque si `tinku.jwt.secret` es el placeholder fuera de `dev`/`test` | AUD-034 | ~10 líneas |

### P1 — Resolver antes de considerar el proyecto terminado

| # | Acción | Finding |
|---|---|---|
| 12 | Congelar `duracion_minutos` en `reservas.reservas` y reemplazar la `EXCLUDE` por `tstzrange(...) &&`, con chequeo equivalente en aplicación | AUD-009 + AUD-020 |
| 13 | Rate limiting en los 7 endpoints públicos + bloqueo escalado por intentos de login (reutilizar el patrón de `OcrBackoffService`) | AUD-012 |
| 14 | Puerto `Notificador` + outbox persistido; implementar como mínimo kill-switch rama menor → Adulto Responsable | AUD-014 |
| 15 | Autenticación en `matching-service` + quitar `ports:` del compose + pool de conexiones | AUD-015 |
| 16 | Decidir y documentar (ADR) baja de menor: cascada o anonimización; test de integración real | AUD-017 |
| 17 | Manejar `participant_left`/`room_finished` y calcular la duración efectiva contra la última desconexión | AUD-029 |
| 18 | Acotar el Modo Bypass (perfil no productivo, TTL o alerta) y extender ADR-M5-01 | AUD-018 |
| 19 | Reconciliar `Tasks_Tinku_Implementacion.md` con el código y elegir una sola fuente de verdad | AUD-030 |
| 20 | Guards de estado en `marcarAprobada`/`marcarRechazada` | AUD-033 |
| 21 | CI para `matching-service` + un smoke E2E contra el stack real | AUD-031 |
| 22 | Evidencia del kill-switch por upload, no por URL declarada; exigir `https://` si se mantiene | AUD-021 |
| 23 | Escribir los ADR faltantes de §4.4, empezando por Java/Spring (ADR-000-02) y por el acoplamiento entre módulos | AUD-019, §7.2 |
| 24 | Anexo a ADR-M3-01: modelo de amenaza del clasificador on-device y controles compensatorios | AUD-005 |

### P2 — Mejoras recomendables

| # | Acción | Finding |
|---|---|---|
| 25 | Romper el ciclo `shared ↔ admin`; mover `AlertaSeguridad` a `seguridad` | AUD-019 |
| 26 | Mover cada evento al módulo que lo publica (o a `shared.evento`) | AUD-022 |
| 27 | `EXCLUDE` de superposición en `franjas_disponibilidad` + `franjaQueCubre` como query | AUD-025 |
| 28 | Distinguir la constraint violada antes de devolver 409 "horario ocupado" | AUD-023 |
| 29 | `credentials_version` en el JWT para invalidar sesiones al resetear contraseña | AUD-027 |
| 30 | Acotar el fallback a `catalogoMock` a 404 estricto o a no-producción | AUD-026 |
| 31 | Decidir una calificación pública por sesión (o documentar el sesgo) | AUD-028 |
| 32 | Migración que dropee las tablas de CAP (V6) referenciando ADR-M1-02 | AUD-035 |
| 33 | Dependabot + actualización de Spring Boot | AUD-032 |
| 34 | Declarar el transcript de M6 como pendiente explícito en los chunks | AUD-024 |
| 35 | Middleware de Next.js: verificar firma, o renombrarlo honestamente como redirección de UX | AUD-016 |

### P3 — Mejoras opcionales

| # | Acción | Finding |
|---|---|---|
| 36 | Poner el `Usuario` en el principal del filtro para eliminar la doble consulta | AUD-036.1 |
| 37 | Unificar `@Transactional` en el de Spring | AUD-036.3 |
| 38 | Subpaquetes de capa en `matching` | AUD-036.2 |
| 39 | `@JsonIgnore` en `Usuario.getEdad()` | AUD-036.6 |
| 40 | Lock en la carga lazy del embedder de Python | AUD-036.7 |
| 41 | Actuator con `/health` e `/info` | AUD-034 |

---

## Anexo — Cómo se verificó

| Verificación | Comando | Resultado |
|---|---|---|
| Suite de tests backend | `JAVA_HOME=<temurin-21> ./mvnw -B test` | 383 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS (3:09) |
| Grafo de dependencias entre módulos | `rg -o "import com\.tinku\.([a-z]+)\." -r '$1'` por paquete | 11 módulos mapeados; 2 ciclos |
| Accesos cruzados a repositorios | `rg -o "import com\.tinku\.[a-z]+\.(repository\.)?[A-Za-z]*Repository"` | 27 ocurrencias |
| Beans por perfil | `rg "@Profile" src/main` | 5 beans; `StubOcrService` en `dev`/`test` |
| Ausencia de rate limiting | `rg -i "ratelimit\|bucket4j\|resilience4j" src pom.xml` | 0 resultados |
| Ausencia de notificaciones | `rg "interface.*Notific\|class.*Notific" src/main/java` | 1 interfaz, 1 impl (log) |
| Clasificador NSFW en frontend | `frontend/package.json` + `rg -i "nsfw\|tensorflow"` | 0 resultados |
| `.env` no commiteado | `git check-ignore -v .env`; `git log --all -- .env` | Ignorado; sin historial |
| Estado del working tree | `git status` | Limpio en `50b6b4d` |

*Fin del informe.*
