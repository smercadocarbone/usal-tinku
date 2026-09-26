# Tinku

Marketplace de tutorías académicas online para Argentina: Estudiantes (o sus
Adultos Responsables) buscan un Tutor, reservan, pagan y toman la clase en un
aula virtual dentro de la plataforma.

> **La restricción que gobierna todo el proyecto:** hay **menores de edad como
> usuarios reales** del sistema. La seguridad del menor prevalece sobre
> cualquier feature o métrica de negocio (Constitución, Artículo II). Un menor
> tiene cuenta y sesión propias, pero no puede pagar, autorizar Tutores nuevos
> ni presentar Denuncias — eso lo hace su Adulto Responsable. Ningún flujo con
> un menor depende de que el propio menor confirme o niegue algo en tiempo real.

Contexto del equipo: 1 desarrollador, presupuesto de infraestructura
USD 0-100/mes durante desarrollo. Cada decisión técnica se justifica contra ese
contexto, no contra "buenas prácticas" de otra escala (Artículo VII).

---

## Antes de tocar código

1. [`AGENTS.md`](AGENTS.md) — reglas operativas no negociables (arquitectura,
   ADRs, seguridad del menor, eventos, testing, control de versiones).
2. [`docs/Constitucion_Tinku.md`](docs/Constitucion_Tinku.md) — principios que
   gobiernan toda decisión.
3. El Spec y el Plan **del módulo en el que vas a trabajar** (no los nueve):
   [`docs/specs/`](docs/specs) y [`docs/plan/`](docs/plan).
4. [`docs/auditoria/REGISTRO_FINDINGS.md`](docs/auditoria/REGISTRO_FINDINGS.md)
   — si vas a tocar Aula (M3), Pagos (M5), Seguridad (M9) o el registro de
   identidad, leé el finding que corresponda antes de empezar.

---

## Arquitectura

**Monolito modular** en Java + Spring Boot (Artículo VIII, ADR-000-02): un
paquete por módulo bajo `com.tinku.*`, una sola base PostgreSQL con **un schema
por módulo** (Flyway los crea y versiona).

| # | Paquete | Módulo | Propósito |
|---|---------|--------|-----------|
| M1 | `identidad` | Identidad y Perfiles | Registro con OCR de DNI, cuentas de menores vinculadas a su Adulto Responsable, Credencial Académica del Tutor, login JWT |
| M2 | `matching` | Motor de Matching | Búsqueda de Tutores; filtra en Java y delega el ranking semántico al servicio Python |
| M3 | `aula` | Aula Virtual | Salas de LiveKit, no-show, corte automático y kill-switch (ramas menor / ambos adultos) |
| M4 | `reservas` | Reservas y Agenda | Franjas de disponibilidad, reservas, solicitudes del menor aprobadas por el Adulto Responsable |
| M5 | `pagos` | Motor de Pagos | MercadoPago (preferencia + webhook), escrow y su liberación, Modo Bypass |
| M6 | `resumen` | Resumen Automático | Resumen de sesión vía LLM detrás de un puerto fail-closed (proveedor pendiente de ADR) |
| M7 | `reputacion` | Calificaciones y Reputación | Calificación post-sesión y reputación del Tutor |
| M8 | `admin` | Panel de Administración | Backoffice: alertas, denuncias, credenciales, pagos, precios, tickets, salud de infraestructura |
| M9 | `seguridad` | Denuncias y Seguridad | Denuncias, Alertas de Seguridad, suspensiones, descargos y reactivaciones |

Además: `com.tinku.config` (Security, CORS, Quartz) y `com.tinku.shared`
(utilidades compartidas).

Reglas estructurales (detalle en `AGENTS.md` §1 y Constitución, Artículos VIII-X):

- **Única excepción al monolito: `matching-service/`**, un proceso Python
  (FastAPI + sentence-transformers) que solo computa embeddings y rankea por
  similitud. El índice vive en **pgvector** dentro de la misma base
  (ADR-M2-01). No conoce reglas de negocio de otros módulos. No se repite este
  patrón en otro módulo sin ADR.
- **Comunicación entre módulos:** llamada síncrona in-process, o **eventos de
  dominio en memoria** vía `ApplicationEventPublisher` para efectos
  secundarios con varias reacciones. Los nombres de evento son los de los
  Specs (`sesion.finalizada`, `sesion.killswitch_menor`,
  `denuncia.registrada`, …) y no se renombran. **No hay message broker** y no
  se introduce uno.
- **Ningún timeout de negocio en memoria:** escrow, no-show, kill-switch,
  expiraciones, SLA de resolución, etc. son jobs de **Quartz con JobStore JDBC
  persistido en PostgreSQL** (tablas `QRTZ_*` en el schema `public`,
  ADR-000-01), instancia única sin clustering (ADR-000-04).
- **Minimización de datos** (Artículo V): no se persiste video.
- Los límites entre módulos hoy son **organizativos** más que reales (hay
  imports cruzados; deuda medida en ADR-000-03 y seguida en la auditoría).

---

## Stack (versiones reales del repo)

| Pieza | Tecnología | Fuente |
|-------|-----------|--------|
| Backend | Java 21, Spring Boot 3.3.4 (web, data-jpa, security, validation, quartz), jjwt 0.12.6, Tesseract CLI (ADR-M1-08), Lombok | `backend/pom.xml` |
| Base de datos | PostgreSQL 16 con pgvector (`pgvector/pgvector:pg16`), migraciones Flyway | `docker-compose.yml`, `backend/src/main/resources/db/migration/` |
| Tests backend | JUnit (spring-boot-starter-test), spring-security-test, Testcontainers 1.21.4 (Postgres real) | `backend/pom.xml` |
| Matching | Python, FastAPI, uvicorn, pydantic, sentence-transformers (`paraphrase-multilingual-MiniLM-L12-v2`, 384 dims), psycopg 3, pgvector; pytest, ruff; imagen `uv` sobre Python 3.12 | `matching-service/requirements.txt`, `matching-service/Dockerfile` |
| Frontend | Next.js ^14.2 (App Router), React ^18.3, TypeScript ^5.5, Tailwind CSS ^4.3, `livekit-client` ^2.22, lucide-react | `frontend/package.json` |
| Tooling frontend | Bun (lockfile `bun.lock`), oxlint, Playwright ^1.63 + `@axe-core/playwright` | `frontend/package.json` |
| Videollamada | LiveKit Cloud | `application.yml` (`tinku.livekit.*`) |
| Pagos | MercadoPago | `application.yml` (`tinku.mercadopago.*`) |
| OCR | Programa `tesseract` del sistema, local (ADR-M1-01, ADR-M1-08) | `backend/Dockerfile`, `identidad/ocr/` |

---

## Estructura del repo

```
tinku/
├── backend/             Spring Boot, monolito modular (com.tinku.*) — ver backend/README.md
├── matching-service/    Python/FastAPI, único proceso separado (M2) — ver matching-service/README.md
├── frontend/            Next.js (src/app, src/components, src/lib) + tests E2E en tests/
├── docs/
│   ├── Constitucion_Tinku.md
│   ├── Tabla_Tiempos_Tinku.md
│   ├── Tasks_Tinku_Implementacion.md / Tasks_Tinku_Chunks.md
│   ├── specs/           Spec_M1..M9 (el QUÉ)
│   ├── plan/            Plan_M1..M9 (el CÓMO)
│   ├── adr/             decisiones técnicas
│   ├── auditoria/       informe independiente + registro de findings
│   └── superpowers/     plan de remediación y otros planes/specs de trabajo
├── scripts/             semillas de datos para dev (seed-*.sh)
├── .github/workflows/   CI de backend y frontend (con path filters)
├── docker-compose.yml   stack local completo
├── Makefile             atajos de desarrollo (`make help`)
└── .env.example         nombres de las variables de entorno
```

---

## Levantar en local

Requisitos: Docker. Para correr piezas en el host además: JDK 21 (temurin),
`uv`, Bun, y Tesseract si querés el OCR real.

### Stack completo (dockerizado)

```bash
cp .env.example .env     # o `make setup`; completá lo que necesites
make up                  # = docker compose up -d --build
```

| Servicio | Puerto |
|----------|--------|
| `db` (Postgres 16 + pgvector) | 5432 |
| `matching` (FastAPI) | 8000 (solo `127.0.0.1`, AUD-015) |
| `backend` (Spring Boot) | 8080 |
| `frontend` (Next.js) | 3000 |

> Auditoría AUD-015: `/match` y `/recompute-embeddings` exigen el header
> `X-Matching-Token`; sin `TINKU_MATCHING_TOKEN` configurado el servicio responde
> `503` (fail-closed). Seteá el mismo valor en `TINKU_MATCHING_TOKEN` y
> `MATCHING_SERVICE_TOKEN` en tu `.env` para que el stack funcione end-to-end.

Otros targets útiles: `make down`, `make logs`, `make ps`, `make db-reset`
(borra el volumen). Para trabajar una pieza con hot reload en el host:
`make backend`, `make matching`, `make frontend`. Detalle de cada una en
[`backend/README.md`](backend/README.md) y
[`matching-service/README.md`](matching-service/README.md).

### Datos de prueba

En perfil `dev` el backend siembra Tutores de ejemplo al arrancar
(`TutorSeedRunner`) y promueve a Admin a usuarios ya registrados
(`AdminSeedRunner`, nunca inventa cuentas). Para el resto, con el stack
arriba, en este orden:

```bash
scripts/seed-usuarios.sh    # adultos, un menor con su Adulto Responsable y tutores de prueba
scripts/seed-matching.sh    # aprueba credenciales, carga temas y embeddings
scripts/seed-reservas.sh    # franjas, autorizaciones y reservas en pendiente_pago
scripts/seed-sesion.sh      # una sesión lista para el aula (requiere LiveKit configurado)
```

### Perfiles

**El artefacto no trae perfil por defecto** (AUD-004): el perfil viene
siempre de `SPRING_PROFILES_ACTIVE`.

| Perfil | Quién lo usa | Qué implica |
|--------|--------------|-------------|
| `dev` | `make backend`, `docker-compose.yml` | BD local (`application-dev.yml`) y `StubOcrService`, que **no hace OCR real** (hace eco de lo declarado) |
| `test` | la suite de tests | Mismo stub |
| `prod` | la imagen Docker del backend si nadie lo sobreescribe | `TesseractOcrService` (necesita Tesseract y `spa.traineddata`) y `application-prod.yml`, que **no tiene defaults**: faltando una variable, no arranca |

`ArranqueSeguroValidator` aborta el arranque fuera de `dev`/`test` (y siempre
que `prod` esté activo, aunque se le sume `dev`) si el OCR activo es el stub o
si `JWT_SECRET` está vacío o es el placeholder del repo. Por eso
`./mvnw spring-boot:run` sin perfil **no levanta**, a propósito: usá
`make backend` o `-Dspring-boot.run.profiles=dev`.

### Variables de entorno

Nombres tal como los leen `backend/src/main/resources/application*.yml`; los
valores **nunca** se commitean (`.env` está en `.gitignore`).

| Área | Variables | Comportamiento si faltan |
|------|-----------|--------------------------|
| Base de datos | `SPRING_DATASOURCE_URL`, `DB_USER`, `DB_PASSWORD` | `dev`: BD de docker-compose. `prod`: **no arranca** |
| Auth | `JWT_SECRET` | `dev`/`test`: placeholder aceptado. Fuera de ahí: **no arranca** (AUD-034). Desde FASE3-03 el `sub` del JWT es el UUID del usuario y lleva `cv`: **tras ese deploy todos los usuarios vuelven a iniciar sesión una vez** |
| CORS | `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` |
| LiveKit (M3) | `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `LIVEKIT_TOKEN_TTL_SEGUNDOS` | El backend arranca; falla con mensaje claro recién al usar el aula |
| MercadoPago (M5) | `MP_ACCESS_TOKEN`, `MP_BASE_URL`, `MP_NOTIFICATION_URL`, `MP_WEBHOOK_SECRET` | Sin `MP_WEBHOOK_SECRET` el webhook rechaza todo (fail-closed) |
| OCR (M1) | `TESSDATA_PREFIX`, `OCR_IDIOMA`, `OCR_BACKOFF_HORAS`, `CREDENCIAL_BACKOFF_HORAS` | Solo aplica fuera de `dev`/`test` |
| Almacenamiento | `ALMACENAMIENTO_DIRECTORIO` | Directorio temporal del sistema (ADR-M1-03) |
| Reservas (M4) | `RESERVAS_TARIFA_STUB` | — |
| Pagos (M5) | `TARIFA_PISO_HORA_ARS` | 6140 (piso por hora, T06; revisión mensual) |
| Rate limit detrás de proxy | `RATE_LIMIT_HEADER_IP` | Vacío = IP del socket. En producción (Cloudflare Tunnel) `CF-Connecting-IP`: solo si el backend no es alcanzable por otro camino |
| Email / avisos | `RESEND_API_KEY`, `EMAIL_REMITENTE`, `APP_URL_PUBLICA` | Sin key/remitente: sin email (avisos solo en la bandeja in-app; reset de contraseña no llega). `APP_URL_PUBLICA`: `prod` **no arranca** sin ella |
| Resumen (M6) | `LLM_PROVEEDOR=gpt-4o`, `LLM_API_KEY` | GPT-4o (ADR-M6-03). Vacío = fail-closed, no sale nada hacia ningún modelo |
| Matching | `MATCHING_SERVICE_URL`, `MATCHING_SERVICE_TOKEN` (backend); `TINKU_PG_HOST`, `TINKU_PG_PORT`, `TINKU_PG_DBNAME`, `TINKU_PG_USER`, `TINKU_PG_PASSWORD`, `TINKU_MATCHING_TOKEN` (servicio Python) | Backend: `http://localhost:8000`. **AUD-015:** `TINKU_MATCHING_TOKEN`/`MATCHING_SERVICE_TOKEN` son el mismo token compartido; vacío = fail-closed (503) en `/match` y `/recompute-embeddings`. Fuera de dev/test el backend **no arranca** sin él |
| Frontend | `NEXT_PUBLIC_API_URL` (build arg en Docker), `NEXT_PUBLIC_SITE_URL` | — |
| Spring | `SPRING_PROFILES_ACTIVE` | Sin perfil no arranca con la config por defecto (ver Perfiles). La imagen Docker usa `prod` |

> Ojo: `docker-compose.yml` solo le pasa al contenedor `backend` un
> subconjunto de estas variables (BD, JWT, CORS, LiveKit, `MP_ACCESS_TOKEN`,
> `RESERVAS_TARIFA_STUB`, matching). Las demás (webhook de MercadoPago, LLM,
> OCR…) hay que agregarlas al servicio si las necesitás en Docker.

---

## Producción

Todo corre en un VPS con Coolify y se despliega solo al mergear a `main` (tests → imágenes en GHCR →
webhook de Coolify). Decisión: `docs/adr/ADR-000-07.md`. Paso a paso, variables, vuelta atrás y
backups: `docs/operacion/RUNBOOK_produccion.md`.

## Tests

### Backend — solo JDK 21

Desde `backend/`, con Docker corriendo (Testcontainers levanta Postgres):

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B test
```

- El `maven-enforcer-plugin` exige Java `[21,22)`. **Con cualquier otra JVM el
  build falla en el enforcer y no corre ni un test.**
- Si pipeás la salida, el exit code puede dar 0 y parecer éxito. **Leé siempre
  la línea `Tests run:`** antes de afirmar que algo pasa. El baseline vigente
  está en `AGENTS.md` §5: si tu cambio baja ese número, borraste un test.
- Atajo equivalente: `make backend-test` (acepta `TEST_FLAGS="-Dtest=..."`).

### Matching service

```bash
make matching-test          # = python -m pytest test_main.py -q (embedder y repo falsos)
cd matching-service && ruff check . && ruff format . --check
```

### Frontend

Desde `frontend/`:

```bash
bun install
bun run lint                # oxlint
bunx playwright install chromium
bun run test:e2e            # Playwright; levanta el dev server solo
```

Los E2E **mockean `/api/**`** con `page.route()`: verifican el frontend
(contratos, navegación, estados de carga/error, accesibilidad con axe), no
reemplazan los tests de integración del backend.

### CI

- `ci-backend.yml`: en cambios bajo `backend/`, JDK 21 temurin + `mvn -B verify`.
- `ci-frontend.yml`: en cambios bajo `frontend/`, Bun + lint + Playwright (Chromium).
- `matching-service/` no tiene pipeline de CI todavía.

---

## Documentación clave

| Documento | Para qué |
|-----------|----------|
| [`docs/Constitucion_Tinku.md`](docs/Constitucion_Tinku.md) | Principios y Registro de Decisiones Técnicas |
| [`docs/specs/`](docs/specs) | Spec funcional de cada módulo (historias, FR/BR, casos borde) |
| [`docs/plan/`](docs/plan) | Plan técnico de cada módulo (modelo de datos, contratos de API) |
| [`docs/Tabla_Tiempos_Tinku.md`](docs/Tabla_Tiempos_Tinku.md) | **Única fuente de verdad para cualquier plazo.** Si un número de tiempo no está ahí, no se inventa |
| [`docs/Tasks_Tinku_Implementacion.md`](docs/Tasks_Tinku_Implementacion.md) | Checklist atómico de tareas — la fuente confiable sobre qué está hecho |
| [`docs/Tasks_Tinku_Chunks.md`](docs/Tasks_Tinku_Chunks.md) | Las mismas tareas agrupadas en chunks (se actualiza junto con el anterior) |
| [`docs/adr/`](docs/adr) | ADRs: Quartz en `public`, Java/Spring, acoplamiento aceptado, instancia única, OCR, retiro del CAP, storage local, pgvector, clasificador del kill-switch y su modelo de amenaza, cierre de la sala de LiveKit en el corte, Modo Bypass, anonimización de transcript, salud de infraestructura |

Si el Registro de Decisiones Técnicas de la Constitución y el código no
coinciden, manda el ADR más reciente y lo que efectivamente está en el código
(por ejemplo, el índice de matching es pgvector por ADR-M2-01).

---

## Estado actual y auditoría

Los nueve módulos tienen código de producción y tests de integración. Hay, sin
embargo, una **auditoría técnica independiente abierta (2026-09-21) con
hallazgos de severidad crítica** que se están remediando por fases, empezando
por los de seguridad. Tinku **no está listo para uso real con menores** hasta
cerrar esos hallazgos.

- Estado vigente, finding por finding:
  [`docs/auditoria/REGISTRO_FINDINGS.md`](docs/auditoria/REGISTRO_FINDINGS.md)
  (citá esa tabla; no copies sus números a otros documentos).
- Informe completo:
  [`docs/auditoria/2026-09-21-auditoria-independiente.md`](docs/auditoria/2026-09-21-auditoria-independiente.md)
- Plan de remediación:
  [`docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`](docs/superpowers/plans/2026-09-21-remediacion-auditoria.md)

Un finding se cierra solo con commit + test de regresión que falla antes del
fix. Los comentarios `FIXME AUD-XXX` en el código marcan problemas conocidos:
no se borran sin cerrar el finding.

### Limitaciones conocidas (verificadas en el código)

- **El clasificador on-device del kill-switch no está integrado en el cliente**
  (T-M3-06 abierta). ADR-M3-01 eligió NSFWJS + TensorFlow.js, pero el frontend
  no tiene esa dependencia: el backend del kill-switch existe, la mitad que
  corre en el navegador y dispara el corte no.
- **No hay canal real de notificaciones** (AUD-014). La única implementación
  (`NotificadorResetPasswordLog`) ya no loguea el token ni el DNI (AUD-008),
  así que **"olvidé mi contraseña" hoy no funciona**: es fail-closed a
  propósito hasta que exista un canal real. Elegir proveedor de email/SMS
  requiere ADR. Tampoco se avisa al Adulto Responsable de un kill-switch.
- **El kill-switch corta al instante, pero la plata espera** (ADR-M3-02): el
  escrow queda en pausa hasta que un Admin resuelve la Alerta, y recién ahí se
  reembolsa al Estudiante.
- **M6 no genera resúmenes reales:** el proveedor de LLM (GPT-4o vs. Gemini
  2.0 Flash) sigue pendiente de ADR y el puerto `ResumenProveedor` es
  fail-closed.
- **Reserva con picker de horarios sin integrar en `/reservar`**
  (T-M4-13 a T-M4-15): el endpoint de horarios existe, la UI todavía usa el
  selector anterior.
- **MercadoPago en sandbox**; el paso a productivo depende de infraestructura
  pendiente. Existe un Modo Bypass (ADR-M5-01) para operar sin cobro real.
- **Almacenamiento de archivos en filesystem local** (ADR-M1-03), sin storage
  externo.
