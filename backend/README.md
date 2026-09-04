# tinku-backend

Monolito modular de Tinku. Ver `Constitucion_Tinku.md` antes de tocar cualquier
cosa acá — en particular el Artículo VIII (por qué es un monolito, no
microservicios) y el Registro de Decisiones Técnicas (stack fijo, salvo ADR).

## Estructura de paquetes (bounded contexts, un paquete = un módulo del Spec)

```
com.tinku.identidad    → M1 — Gestión de Identidad y Perfiles
com.tinku.matching     → M2 — llama internamente al servicio Python (../tinku-matching-service)
com.tinku.aula         → M3 — Aula Virtual (LiveKit, kill-switch)
com.tinku.reservas     → M4 — Reservas y Agenda
com.tinku.pagos        → M5 — Motor de Pagos (MercadoPago)
com.tinku.resumen      → M6 — Resumen Automático
com.tinku.reputacion   → M7 — Calificaciones y Reputación
com.tinku.admin        → M8 — Panel de Administración
com.tinku.seguridad    → M9 — Denuncias y Seguridad
com.tinku.config       → Configuración transversal (Security, Quartz)
com.tinku.shared       → Eventos de dominio y utilidades compartidas
```

## Antes de arrancar a programar features

1. Leer el **Spec** y el **Plan técnico** del módulo en el que vas a trabajar — no hay excusa para no tenerlos, están todos en el repo de documentación.
2. Revisar `Tasks_Tinku_Implementacion.md` y marcar la tarea que estás por empezar.
3. Si tu tarea depende de un ADR pendiente (marcado en el Plan técnico del módulo), resolverlo primero — no improvisar una decisión de stack en medio de una feature.

## Cómo correr localmente

**Requiere JDK 21** (el `pom.xml` apunta a Java 21 y Lombok no procesa
anotaciones en JDKs más nuevos). El wrapper de Maven los deja usar el JDK que
tengas activo, así que hay que pasarlo explícito. En macOS con temurin-21
instalado:

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

> Si te olvidás de ponerlo, `./mvnw` falla temprano con el mensaje del
> enforcer de Maven ("Se requiere Java 21 explícitamente"), no con el error
> confuso de "cannot find symbol" de Lombok.

`spring.profiles.active=dev` queda fijado como **default** en
`application.yml`. Es decir, con JDK 21 activo `./mvnw spring-boot:run` levanta
la app sin más flags. El perfil `dev`:

- usa la BD local (`localhost:5432` + creds de `docker-compose.yml` en la raíz);
- usa el `StubOcrService` (OCR falso) y el JWT secret de desarrollo de
  `application.yml`.

> **IMPORTANTE:** imposible levantar sin perfil. El `StubOcrService` está gated
> a `dev`/`test` y el `TesseractOcrService` (ADR-M1-01) a los demás perfiles.
> Sin perfil, Spring no encuentra ningún `OcrService` y el arranque falla.

```bash
# 0. Asegurarse de usar JDK 21 (ver arriba)
# 1. Levantar Postgres (Postgres 16, ver docker-compose.yml en la raiz del repo)
docker compose up -d db

# 2. (opcional) Levantar el servicio de matching, ver ../matching-service/README.md

# 3. Levantar el backend — ya trae el perfil dev por defecto
./mvnw spring-boot:run
```

Para otro ambiente se sobreescribe el perfil, nunca se edita la app:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

## Profiles

| Perfil | Uso | OcrService |
|---|---|---|
| `dev` (default local) | desarrollo local contra la BD de docker-compose | `StubOcrService` |
| `test` | tests de integración (generalmente vía Testcontainers) | `StubOcrService` |
| (otro, ej. `prod`) | despliegue real | `TesseractOcrService` (requiere Tesseract instalado, ver abajo) |

> El perfil `dev` usa el `StubOcrService` (OCR falso) para poder correr el flujo
> de registro sin depender del binario nativo. El `TesseractOcrService` real
> (ADR-M1-01) se activa únicamente fuera de `dev`/`test`. Sin perfil activo no
> hay ningún `OcrService` y la app no levanta: `spring.profiles.active=dev` es
> el default en `application.yml`.

## OCR — Tesseract (ADR-M1-01)

El OCR de identidad usa **Tesseract vía Tess4J**, in-process (la imagen del DNI
nunca sale del servidor — Artículo V). Requiere el binario nativo de Tesseract
instalado en el entorno y el data de idioma español:

- **Mac:** `brew install tesseract tesseract-lang`
- **Linux / Docker:** `apt-get install -y tesseract-ocr tesseract-ocr-spa`
  (en el `Dockerfile` de despliegue; ver `docs/adr/ADR-M1-01.md`)

El data de idioma español debe estar accesible para Tesseract vía
`TESSDATA_PREFIX` (carpeta que contiene `spa.traineddata`) o la property
`tinku.ocr.tessdata`. Si no está, el `TesseractOcrService` devuelve
"documento ilegible" (reintentos + backoff FR-ID-011), nunca rompe el request.

```bash
# Ejemplo (Mac) tras instalar el paquete:
export TESSDATA_PREFIX="$(brew --prefix tesseract)/share/tessdata"
```

## Quartz (JobStore JDBC persistido)

Los timeouts de negocio (escrow, kill-switch, no-show, etc.) viven en un
scheduler **persistido en PostgreSQL** (Constitución, Artículo IV/X), no en
memoria: deben sobrevivir a un reinicio o redeploy del proceso.

- Config: `spring.quartz.*` en `application.yml` (`job-store-type: jdbc`,
  `scheduler-name: TinkuScheduler`, `jdbc.initialize-schema: never`).
- Las tablas `QRTZ_*` se crean **vía Flyway** (`V3__quartz_tables.sql`, script
  oficial de Quartz para PostgreSQL). **Decisión:** viven en el schema `public`
  con el prefijo `QRTZ_` por defecto — Quartz es infraestructura transversal,
  no un modulo de dominio, y la Constitución (Artículo VIII) reserva schemas
  solo para los 9 bounded contexts de negocio.
- `spring.quartz.properties.org.quartz.jobStore.tablePrefix=QRTZ_` (schema `public`).
- Verificado por `QuartzPersistenciaTest`: un job programado sobrevive a un
  reinicio del scheduler contra la misma base.

## Variables de entorno requeridas (ver application.yml)

`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `MP_ACCESS_TOKEN`, `MATCHING_SERVICE_URL`, y para OCR: `TESSDATA_PREFIX` (carpeta con `spa.traineddata`).

**Nunca commitear valores reales de estas variables.**
