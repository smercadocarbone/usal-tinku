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

> **IMPORTANTE:** imposible levantar sin perfil. El `OcrService` real
> (ADR-M1-01) no existe todavía; el único bean concreto (`StubOcrService`)
> está gated a los perfiles `dev`/`test`. Sin `dev`, Spring no encuentra
> ningún `OcrService` y el arranque falla.

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
| (otro, ej. `prod`) | despliegue real — requiere la implementación OCR real (ADR-M1-01) y secrets por entorno | ninguno por ahora |

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

`DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `MP_ACCESS_TOKEN`, `MATCHING_SERVICE_URL`.

**Nunca commitear valores reales de estas variables.**
