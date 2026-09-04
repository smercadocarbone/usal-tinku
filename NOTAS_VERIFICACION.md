# NOTAS_VERIFICACION — branch `chunk/m1-f`

Chunk M1-F: **T-M1-14** (migración CAP), **T-M1-15** (carga CAP FR-ID-021),
**T-M1-16** (revisión CAP para M8, BR-CAP-01/02), **T-M1-17** (job Quartz de
vencimiento FR-ID-025). Este branch NACE de `chunk/m1-e`. NO fue mergeado a
main y NO marca ningún chunk/tarea como cerrado.

## Qué quedó implementado

### T-M1-14 — Migración V6
- Tabla `certificados_antecedentes_penales` (schema identidad): estado enum
  (PENDIENTE/APROBADO/RECHAZADO/EN_REVISION_LEGAL/VENCIDO), `tiene_antecedentes`,
  `categoria_antecedente` (trazabilidad, no automatiza), `numero_intento` 1..3,
  `ciclo_espera_hasta`.
- Columna `usuarios.activo_para_matching` (columna NUEVA, no se edita V2 —
  AGENTS.md §7). Mismo flag que M2/M9 usan para suspender Tutores del matching;
  M2 lo lee para excluirlos.

### T-M1-15 — Carga del CAP
- `POST /api/tutores/antecedentes-penales` (autenticado, solo Tutor): multipart
  (`datos` + `archivo`). `CertificadoService.cargarCap`.
  - Backoff compartido con credenciales (FR-ID-021 reutiliza FR-ID-012):
    `CredencialBackoffService` (misma escalada 24→48→96).
  - Una sola PENDIENTE a la vez. `vence_at = fecha_emision + 12 meses`.
- Reutiliza `Almacenamiento` (stub) para la URL del archivo.

### T-M1-16 — Revisión para M8
- `GET /api/admin/moderacion/antecedentes-penales` (cola) y
  `PATCH .../{id}` mit `RevisarCapRequest{accion, categoriaAntecedente}`.
  - APROBAR → habilita `activo_para_matching` (FR-ID-025).
  - RECHAZAR → BR-CAP-01 (FR-ID-023); si era el 3er intento → backoff.
  - EN_REVISION_LEGAL → BR-CAP-02 (FR-ID-024); NUNCA se auto-resuelve.

### T-M1-17 — Vencimiento a los 12 meses
- `CapVencimientoJob` (Quartz, `@DisallowConcurrentExecution`) + JobDetail/Trigger
  diario (03:00) en `QuartzConfig` (durable/recoverable).
  - `CertificadoService.marcarVencidos()`: marca `VENCIDO` y suspende
    `activo_para_matching` (FR-ID-025). Saca del matching, no de la cuenta.

## Unit tests corridos en esta sesión (verdes, sin Docker/Tesseract)

- `CertificadoServiceTest` (10): carga OK (+vence_at 12m), no-Tutor, pendiente
  existente, en backoff, reintento→intento 2, aprobar→habilita matching,
  rechazar BR-CAP-01 (3er intento→backoff), en_revision_legal BR-CAP-02 (no
  auto-resuelve), vencidos suspenden matching, idempotencia.
- `CapVencimientoJobTest` (1): el job delega en `marcarVencidos()`.
- Regresión: C (28) + D (10) + E (24). Total 73 verdes.

## Qué falta correr/confirmar en un entorno con Docker + Tesseract

1. Flyway: aplicar V6 y `ddl-auto:validate` (nueva columna + tabla).
2. Testcontainers `@SpringBootTest`: el contexto arranca con el
   `CapVencimientoJob` registrado en Quartz con JobStore JDBC (expresión cron,
   `JobDetail`/`Trigger`).
3. HTTP real: `/api/tutores/antecedentes-penales` requiere token; revisión
   admin con rol.
4. E2E US-6: carga → Admin aprueba → tutor en matching; rechazo por BR-CAP-01;
   `en_revision_legal`; y vencimiento real a los 12 meses suspendiendo matching.

## Decisiones de scope (ADRs NO)

- **Rol ADMIN de M8:** los endpoints `/api/admin/...` quedan solo
  `authenticated()`. M8 debe cerrarlos con rol ADMIN (tabla `admins`, separada
  de `usuarios`, sin compartir JWT — ver `SecurityConfig`). La identidad del
  revisor se deduce del principal (UUID si aplica); si no, `admin_revisor_id`
  queda null hasta que M8 provea la identidad real (NFR-SEC-04).
- **`activo_para_matching` en `usuarios`:** decisión M1-F para que el job de
  vencimiento tenga un flag persistido que apagar (FR-ID-025) sin crear tablas
  de M2. M2 leerá este flag (mismo que planea reutilizar para M9) para excluir
  Tutores suspendidos. Si en M2 se prefiere migrar a `perfiles_tutor_matching`,
  se documenta en esa decisión — no se crea flag duplicado.
- **Backoff compartido por tutor** entre credencial y CAP (plan 2.4: "reutilizar
  el job, no duplicar la lógica"): ambos usan `CredencialBackoffService` clave
  tutorId. Queda registrado que agotar el ciclo de uno afecta también al otro
  (patrón intencional de "reusar", no un bug).
