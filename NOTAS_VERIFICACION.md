# NOTAS_VERIFICACION — branch `chunk/m1-g`

Chunk M1-G: **T-M1-13** — tests de integración de TODAS las Historias de
Usuario del Spec de M1, de punta a punta sobre el stack real (HTTP + Spring
Security + JPA + Flyway + PostgreSQL **via Testcontainers**, Docker corriendo).
Este branch NACE de `chunk/m1-f`. NO fue mergeado a main y NO marca ningún
chunk/tarea como cerrado en `Tasks_Tinku_Chunks.md`/`Tasks_Tinku_Implementacion.md`.

## Qué se verificó (y quedó VERDE)

`mvnw test` completo: **100 tests, 0 failures, 0 errors** (BUILD SUCCESS).
Incluye los 11 tests de integración nuevos de `IdentidadFlujosIntegracionTest`
más la suite previa (`TinkuApplicationTests`, `QuartzPersistenciaTest`,
`SecurityHttpTest`, `JwtAuthTest`, `DomainEventExampleTest`, `UsuarioServiceTest`
y todos los unit tests de M1-C/D/E/F).

Cobertura de punta a punta (T-M1-13), por Historia de Usuario:

| US  | Test de integración | Assert clave |
|-----|--------------------|--------------|
| US-1 | adulto se registra + loguea | 201, tipo ADULTO, estado ACTIVA, capacidades |
| US-1 | rechazo por DNI duplicado (contra el DNI EXTRAÍDO, no el declarado) | 409 |
| US-1 | rechazo por edad < 18 (fecha extraída del documento) | 403 |
| US-2 | tutor se registra | 201, tipo TUTOR |
| US-3 | menor se registra por SU Adulto Responsable autenticado | 201, tipo MENOR, AR linkeado, no-adulto-responsable |
| US-4 | credencial: carga → 3 rechazos → backoff escalado → carga responde 429 | intentos 1/2/3, luego 429 |
| US-5 | Adulto Responsable autoriza un Tutor para su menor | 201 |
| US-6 | CAP aprobado habilita `activo_para_matching` | FR-ID-025 |
| US-6 | CAP rechazado por BR-CAP-01 (3er intento) dispara backoff | 429 + matching inactivo |
| US-6 | CAP `en_revision_legal` (BR-CAP-02) NO se auto-resuelve | estado persistente + matching inactivo |
| US-6 | CAP vencido a los 12 meses suspende matching, NO la cuenta | estado VENCIDO + matching inactivo + cuenta ACTIVA |

## Hallazgo de M1-G: bug latente de Quartz (corregido con config, no toca Flyway)

Al correr por primera vez contra un Postgres real, el contexto no levantaba:
`Bad value for type long : \xaced...` al almacenar el JobDetail del job de M1-F
(`capVencimiento`). Causa raíz:

- `spring.quartz.job-store-type=jdbc` estaba configurado en `application.yml`,
  pero **no** se seteaba `org.quartz.jobStore.driverDelegateClass`.
- El default de Spring Boot (`StdJDBCDelegate`) lee `QRTZ_JOB_DETAILS.JOB_DATA`
  via `getBlob()` esperando un **large object (OID)**, pero las tablas QRTZ_* de
  `V3__quartz_tables.sql` usan **BYTEA** (script oficial de Quartz para Postgres).
- Hasta M1-F no había ningún JobDetail que persistir en runtime, por eso el bug
  estaba latente; el `capVencimiento` (primer job de la app) lo expuso.
- **Fix (config, AGENTS.md §7 intacto — NO se editó V3):** se agregó
  `driverDelegateClass: org.quartz.impl.jdbcjobstore.PostgreSQLDelegate` en
  `application.yml`. Confirmado por `QuartzPersistenciaTest` (verde) y por el
  arranque real del contexto con el job registrado.

## Alcance y límites de lo verificado acá

- **OCR real (Tesseract) aún NO verificado end-to-end.** Los tests de
  integración mockean `OcrService` a nivel de puerto para poder controlar DNI y
  fecha de nacimiento extraídos (necesario para menores/edad/duplicado). El
  binario nativo de Tesseract no está garantizado en este entorno; su E2E queda
  pendiente de un entorno con `tesseract`/`tesseract-ocr-spa` instalado
  (ADR-M1-01). El pipeline aislado sigue cubierto por `DniParserTest` +
  `PreprocesadorImagenTest`.
- `StubAlmacenamiento` no persiste archivos (ADR de storage pendiente); los tests
  de credencial/CAP usan la URL `stub:/...` — suficiente para el flujo lógico.
- los endpoints `/api/admin/**` quedan `authenticated()` (no rol ADMIN); M8 los
  cierra con rol ADMIN (ver NOTAS de M1-F).
