# NOTAS_VERIFICACION — branch `chunk/m1-e`

Chunk M1-E: **T-M1-10** (credenciales + backoff escalonado FR-ID-012),
**T-M1-11** (autorizaciones Tutor FR-ID-009), **T-M1-12** (baja de menor FR-ID-014).
Este branch NACE de `chunk/m1-d`. NO fue mergeado a main y NO marca ningún
chunk/tarea como cerrado.

## Qué quedó implementado

### T-M1-10 — Credenciales académicas
- `POST /api/tutores/credenciales` (autenticado, solo Tutor): multipart
  (`datos` + `archivo`). `CredencialService.cargarCredencial`:
  - Solo perfil Tutor.
  - Backoff escalado FR-ID-012 (`CredencialBackoffService`, tabla V5
    `intentos_credencial`, cooldown PASIVO 24hs * 2^n → 24→48→96…).
  - Una sola PENDIENTE a la vez.
  - FR-ID-008: hasta 3 intentos por ciclo (`numero_intento`).
- Transiciones de Admin (M8) expuestas como servicio: `marcarAprobada` /
  `marcarRechazada`. Al rechazar el 3er intento dispara el backoff.
- `Almacenamiento` (port) + `StubAlmacenamiento`: no hay storage real aún,
  devuelve URL derivada (ADR de storage pendiente).

### T-M1-11 — Autorizaciones de Tutor (FR-ID-009)
- `POST /api/autorizaciones` — autorizar un Tutor para un menor a cargo.
- `PATCH /api/autorizaciones/no-confiable` — marcar no confiable (privado,
  a nivel de cuenta del AR; no alerta a Admin ni toca reputación pública).
- Solo la capacidad "Adulto Responsable" (Artículo II: un menor no autoriza).

### T-M1-12 — Baja de menor (FR-ID-014)
- `DELETE /api/usuarios/menores/{id}?confirmar=` — solo su Adulto Responsable.
- Verificación de reservas futuras vía port `VerificadorReservasFuturas` +
  `StubVerificadorReservasFuturas` (devuelve 0 — M4 aún no existe). Si hubiera
  reservas y no viene `confirmar=true` → 409 con el conteo.
- Elimina el menor + autorizaciones + consentimientos.

## Unit tests corridos en esta sesión (verdes, sin Docker/Tesseract)

- `CredencialBackoffServiceTest` (5): 24→48→96hs, en espera lanza, fuera no
  lanza, sin registro no lanza.
- `CredencialServiceTest` (8): carga OK, no-Tutor rechaza, pendiente existente,
  en backoff, reintento incremente a intento 2, aprobar, rechazo 3er intento →
  backoff, rechazo 1er intento no.
- `AutorizacionServiceTest` (7): autorizar OK/idempotente/mentor no a cargo/
  sin capacidad/menor autorizando; no confiable OK/sin autorización previa.
- `UsuarioServiceDarDeBajaTest` (4): baja sin reservas, menor no a cargo,
  sin confirmación con reservas, con confirmación.
- Regresión: M1-C (28) + M1-D (10). Total 62 verdes.

## Qué falta correr/confirmar en un entorno con Docker + Tesseract

1. Migraciones Flyway: aplicar V5 y validar `ddl-auto:validate`.
2. Testcontainers `@SpringBootTest` (no corrieron): contexto completo con los
   controllers/beans nuevos (`CredencialService`, `AutorizacionService`,
   `CredencialBackoffService`, ports/stubs).
3. HTTP real: rutas autenticadas requieren token (`/api/tutores/credenciales`,
   `/api/autorizaciones/*`, `DELETE /menores/{id}`); `/api/tutores/registro`
   sigue pública.
4. E2E US-4: flujo carga → Admin aprueba/rechaza (M8) → reintento → backoff
   escalado real contra BD.
5. E2E US-5: marcar no confiable → el Tutor desaparece del matching (M2).
6. E2E baja de menor: el puente real a M4 (reservas futuras) hoy es stub → la
   verificación de FR-ID-014 queda pendiente hasta que M4 implemente el port.
7. Storage de credenciales: `StubAlmacenamiento` no persiste — pendiente ADR de
   storage antes de producción.

## Notas de diseño / decisiones (sin ADR nuevo)

- El "no confiable" (FR-ID-009) se modeló a nivel de CUENTA del AR: se aplica
  sobre todas sus autorizaciones de ese Tutor (bulk update por
  adulto_responsable_id + tutor_id), no por menor, para que sea consistente con
  el comportamiento "deja de aparecer en los resultados de esa cuenta".
- Rechazo/aprobación de credencial viven en M1 como servicios; M8 (panel Admin)
  los invoca. El conteo de intentos y el backoff quedan acá, no en M8.
