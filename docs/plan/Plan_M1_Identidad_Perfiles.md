# Plan Técnico: M1 — Gestión de Identidad y Perfiles

**Basado en:** Spec_M1_Identidad_Perfiles.md (aprobado)
**Stack (Constitución, Registro de Decisiones):** Java + Spring Boot, PostgreSQL, Spring Security (JWT), bcrypt/argon2.

---

## 1. Modelo de Datos (lógico)

### `usuarios`

| Campo                        | Tipo                              | Notas                                                                                        |
| ---------------------------- | --------------------------------- | -------------------------------------------------------------------------------------------- |
| id                           | UUID (PK)                         |                                                                                              |
| dni                          | varchar, **UNIQUE, NOT NULL**     | Aplica FR-ID-001/018/019: un DNI = una sola fila en todo el sistema, sin importar el `tipo`. |
| nombre, apellido             | varchar                           | Extraídos por OCR y confirmados contra lo declarado.                                         |
| fecha_nacimiento             | date                              | Usada para calcular edad (≥18 adultos, ≥6 menores).                                          |
| email                        | varchar(255), **UNIQUE** (índice parcial `WHERE email IS NOT NULL`) | Credencial de acceso (login). Nulo en cuentas creadas antes de V18 (migración que lo agrega) y en perfiles de menor (no se autorregistran con email). |
| tipo                         | enum(`adulto`, `menor`, `tutor`)  | Determina qué otras tablas/columnas aplican.                                                 |
| capacidad_estudiante         | boolean, default false            | Solo relevante si `tipo = adulto`.                                                           |
| capacidad_adulto_responsable | boolean, default false            | Solo relevante si `tipo = adulto`.                                                           |
| adulto_responsable_id        | UUID (FK → usuarios.id), nullable | Solo si `tipo = menor`. Quién lo dio de alta.                                                |
| password_hash                | varchar                           | bcrypt/argon2 (NFR-SEC-02 de la Constitución).                                               |
| estado_cuenta                | enum(`activa`, `suspendida`)      | Lo modifica M9 vía sanción, no este módulo directamente.                                     |
| created_at                   | timestamp                         |                                                                                              |

**Restricción de negocio a nivel de aplicación (no solo de UI):** si `tipo = adulto` y ambas capacidades son `false`, el registro es inválido — siempre debe activarse al menos una al crear la cuenta.

### `credenciales_academicas`

| Campo                   | Tipo                                                 | Notas                                            |
| ----------------------- | ---------------------------------------------------- | ------------------------------------------------ |
| id                      | UUID (PK)                                            |                                                  |
| tutor_id                | UUID (FK → usuarios.id)                              |                                                  |
| tipo_documento          | enum(`titulo`, `certificado_analitico`, `matricula`) | Lista cerrada, BR-ID-01.                         |
| archivo_url             | varchar                                              | Referencia a storage, no el binario en la tabla. |
| estado                  | enum(`pendiente`, `aprobado`, `rechazado`)           |                                                  |
| numero_intento          | int, default 1                                       | Máximo 3 (FR-ID-008).                            |
| ciclo_espera_hasta      | timestamp, nullable                                  | Implementa el backoff 24h→48h→96h (FR-ID-012).   |
| admin_revisor_id        | UUID (FK → usuarios.id), nullable                    | Queda en el registro de auditoría (NFR-SEC-04).  |
| created_at, revisado_at | timestamp                                            |                                                  |

### `autorizaciones_tutor`

| Campo                 | Tipo                    | Notas                                          |
| --------------------- | ----------------------- | ---------------------------------------------- |
| id                    | UUID (PK)               |                                                |
| adulto_responsable_id | UUID (FK → usuarios.id) |                                                |
| menor_id              | UUID (FK → usuarios.id) |                                                |
| tutor_id              | UUID (FK → usuarios.id) |                                                |
| no_confiable          | boolean, default false  | FR-ID-009 — no elimina la fila, solo la marca. |
| created_at            | timestamp               | No vence (BR-AUTH-01).                         |

_Índice único sugerido: (`adulto_responsable_id`, `menor_id`, `tutor_id`) — una sola fila por combinación, se actualiza el flag `no_confiable` en vez de duplicar._

### `consentimientos_menor`

| Campo                 | Tipo                    | Notas                                                                                              |
| --------------------- | ----------------------- | -------------------------------------------------------------------------------------------------- |
| id                    | UUID (PK)               |                                                                                                    |
| menor_id              | UUID (FK → usuarios.id) |                                                                                                    |
| adulto_responsable_id | UUID (FK → usuarios.id) |                                                                                                    |
| version_texto         | varchar                 | Referencia a qué versión del texto de consentimiento aceptó (para poder re-solicitarlo si cambia). |
| aceptado_at           | timestamp               |                                                                                                    |
| revocado_at           | timestamp, nullable     | FR-ID-006.                                                                                         |

### `certificados_antecedentes_penales` _(RETIRADO — ver ADR-M1-02 y enmienda Constitución v2.2. La migración `V6` no se elimina, AGENTS.md §7: la tabla queda en la base sin uso. Texto conservado como registro histórico.)_

| Campo                   | Tipo                                                                       | Notas                                                                                                                                   |
| ----------------------- | -------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------- |
| id                      | UUID (PK)                                                                  |                                                                                                                                         |
| tutor_id                | UUID (FK → usuarios.id)                                                    |                                                                                                                                         |
| archivo_url             | varchar                                                                    | PDF con firma digital del Registro Nacional de Reincidencia — referencia a storage, no el binario en la tabla.                          |
| fecha_emision           | date                                                                       | Extraída del documento o declarada por el Tutor al subirlo.                                                                             |
| vence_at                | date                                                                       | `fecha_emision + 12 meses` (FR-ID-025).                                                                                                 |
| estado                  | enum(`pendiente`, `aprobado`, `rechazado`, `en_revision_legal`, `vencido`) | `en_revision_legal` es el estado para BR-CAP-02 — nunca se auto-resuelve.                                                               |
| tiene_antecedentes      | boolean                                                                    | Si el documento informa algo, aunque después se apruebe igual (caso BR-CAP-02).                                                         |
| categoria_antecedente   | varchar, nullable                                                          | Texto libre que carga el Admin al revisar — para trazabilidad de qué categoría de BR-CAP-01/02 aplicó, no para automatizar la decisión. |
| numero_intento          | int, default 1                                                             | Máximo 3, mismo backoff que `credenciales_academicas` (FR-ID-021 reutiliza FR-ID-012).                                                  |
| ciclo_espera_hasta      | timestamp, nullable                                                        | Igual mecanismo que credenciales.                                                                                                       |
| admin_revisor_id        | UUID (FK → usuarios.id), nullable                                          | Auditoría (NFR-SEC-04).                                                                                                                 |
| created_at, revisado_at | timestamp                                                                  |                                                                                                                                         |

_Nota de implementación: no se valida automáticamente la firma digital del PDF en el MVP (queda como mejora futura, no bloqueante) — la revisión es 100% manual del Admin, igual que la Credencial Académica._

## 2. Flujos Técnicos Clave

### 2.1 Registro de Usuario adulto (US-1)

1. Cliente envía DNI (foto) + datos declarados + email + password. El **email** es la credencial de acceso y se persiste con la cuenta.
2. Backend llama al servicio de OCR (**proveedor a definir — ver ADR-M1-01**) con la foto.
3. OCR devuelve: nombre, apellido, fecha de nacimiento extraídos del documento.
4. Backend valida: (a) nombre/apellido extraído == declarado (fuzzy match tolerante a mayúsculas/acentos, no exacto carácter por carácter), (b) edad ≥ 18, (c) `SELECT 1 FROM usuarios WHERE dni = ?` no devuelve fila.
5. Si las tres pasan → crea el usuario, hashea password, retorna JWT.
6. Si falla cualquiera → rechazo con motivo específico (no exponer detalles del OCR al usuario final, solo "no pudimos verificar tu documento" para (a), mensaje claro y específico para (b) y (c) según FR-ID-018).
7. Fallos de lectura del documento (no de validación, sino que el OCR no pudo procesar la imagen) cuentan aparte, contra el contador de 3 intentos + 24hs (FR-ID-011) — **distinto** de un rechazo por edad o DNI duplicado, que no consume reintentos (no tiene sentido "reintentar" ser mayor de edad).

#### 2.1.1 Verificación previa del DNI (compuerta del wizard de registro)

El frontend de registro es un wizard de 4 pasos: (0) rol, (1) datos personales, (2) verificación de identidad, (3) credenciales. Para que el paso de credenciales (email + contraseña) no se pida antes de confirmar que la persona es adulta y su documento es válido, existe una verificación previa **sin creación de cuenta**:

1. Cliente envía a `/verificar-dni` el DNI (foto) + datos declarados — **sin** email ni password.
2. El backend aplica el mismo chequeo de backoff + OCR + validaciones (edad ≥ 18, coincidencia, DNI no usado) que 2.1, **pero no persiste nada**.
3. Si pasa → responde **204 No Content** y el cliente habilita el paso de credenciales (el body no lleva información; el contrato real son los status de error, ver paso 4).
4. Si el OCR detecta minoría de edad → 403 (pantalla informativa, no se crea cuenta); DNI duplicado → 409; no coincide → 422; backoff → 429.
5. El alta real sigue siendo un único POST a `/registro` con email + password (paso 2.1) — la verificación previa es solo la compuerta.

### 2.2 Alta de cuenta de menor (US-2)

1. Requiere sesión activa de un adulto con `capacidad_adulto_responsable = true`.
2. Mismo flujo de OCR que 2.1, aplicado al DNI del menor, más el chequeo de edad mínima (6 años) en vez de 18.
3. Se registra el consentimiento (tabla `consentimientos_menor`) como paso obligatorio antes de persistir el usuario tipo `menor` — transacción atómica: si el consentimiento no se guarda, no se crea la cuenta.
4. El adulto define la contraseña inicial del menor (o el sistema genera un link de invitación de un solo uso con expiración — **decisión de UX para el Plan de M4/frontend, no bloqueante acá**).

### 2.3 Backoff de Credencial Académica (US-4)

Job persistido (Quartz, Constitución Artículo IV/X) que:

- Al agotar el intento N (N ≤ 3) sin aprobación, calcula `ciclo_espera_hasta = now() + 24h * 2^(ciclo_actual - 1)` (24h, 48h, 96h...).
- Antes de aceptar una nueva carga, el endpoint valida `now() >= ciclo_espera_hasta`.

### 2.4 Carga y vencimiento del CAP (US-6) _(RETIRADO — ver ADR-M1-02. Texto conservado como registro histórico.)_

1. Tutor sube el PDF del CAP → estado `pendiente`, mismo backoff de reintentos que la Credencial Académica (reutilizar el job de Chunk M1-E, no duplicar la lógica).
2. Admin revisa manualmente (interfaz de M8, no de este módulo): si no hay antecedentes → `aprobado`. Si hay un antecedente de BR-CAP-01 → `rechazado`, sin reintento posible para ese motivo. Si hay un antecedente de BR-CAP-02 o un proceso en trámite → `en_revision_legal` (nunca auto-resuelto; requiere decisión manual documentada, ver nota legal pendiente en el Spec).
3. Job persistido (Quartz) que corre diariamente: marca `vencido` cualquier CAP con `vence_at < now()` y suspende `activo_para_matching` del Tutor (consistente con el mecanismo que M2 ya usa para Tutores suspendidos por M9 — reutilizar el mismo flag, no crear uno nuevo).
4. El Tutor puede recargar un CAP nuevo en cualquier momento antes o después del vencimiento — no hay backoff por vencimiento en sí (el backoff aplica solo a rechazos, no a la expiración natural del documento).

## 3. API (contratos de alto nivel)

| Método   | Endpoint                                          | Notas                                                                                                                                     |
| -------- | ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `POST`   | `/api/usuarios/registro`                          | Alta de adulto (Estudiante y/o Adulto Responsable). Incluye email + password.                                                            |
| `POST`   | `/api/usuarios/verificar-dni`                     | Verificación previa del DNI del adulto (compuerta del wizard). No crea cuenta.                                                            |
| `POST`   | `/api/usuarios/menores`                           | Alta de cuenta de menor (auth: adulto_responsable).                                                                                       |
| `PATCH`  | `/api/usuarios/me/capacidades`                    | Activa/desactiva Estudiante o Adulto Responsable (FR-ID-015/016).                                                                         |
| `POST`   | `/api/tutores/registro`                           | Alta de Tutor. Incluye email + password.                                                                                                  |
| `POST`   | `/api/tutores/verificar-dni`                      | Verificación previa del DNI del Tutor (compuerta del wizard). No crea cuenta.                                                             |
| `POST`   | `/api/tutores/credenciales`                       | Carga de documento (respeta backoff).                                                                                                     |
| `GET`    | `/api/admin/moderacion/credenciales`              | Cola de M8 (rol Moderación y Seguridad).                                                                                                  |
| `PATCH`  | `/api/admin/moderacion/credenciales/{id}`         | Aprobar/rechazar.                                                                                                                         |
| `POST`   | `/api/autorizaciones`                             | Autorizar un Tutor para un menor.                                                                                                         |
| `PATCH`  | `/api/autorizaciones/{id}/no-confiable`           | Marcar/desmarcar (FR-ID-009).                                                                                                             |
| `DELETE` | `/api/usuarios/menores/{id}`                      | Con confirmación explícita si hay reservas futuras (FR-ID-014) — el frontend debe mostrar la advertencia antes de llamar a este endpoint. |
| `POST`   | `/api/tutores/antecedentes-penales`               | _RETIRADO (ADR-M1-02)._ Carga del CAP (respeta backoff, igual patrón que credenciales).                                                                           |
| `GET`    | `/api/admin/moderacion/antecedentes-penales`      | _RETIRADO (ADR-M1-02)._ Cola de M8 — pendientes y `en_revision_legal`.                                                                                            |
| `PATCH`  | `/api/admin/moderacion/antecedentes-penales/{id}` | _RETIRADO (ADR-M1-02)._ Aprobar / rechazar (BR-CAP-01) / marcar `en_revision_legal` (BR-CAP-02), con `categoria_antecedente` en el body para trazabilidad.        |

## 4. ADRs de este Módulo (pendientes, no bloquean el resto del Plan)

- **ADR-M1-01:** Proveedor de OCR/verificación de documento. Candidatos a evaluar: APIs de visión genéricas (ej. Google Cloud Vision, AWS Textract) vs. servicios especializados en verificación de identidad (más caros, pero con mejor tasa de acierto en documentos argentinos). Definir antes de implementar 2.1.
- **ADR-M1-02:** Mecanismo de entrega de credenciales al menor (contraseña definida por el adulto vs. link de invitación). No bloquea el modelo de datos, sí afecta el flujo de frontend.

## 5. Trazabilidad con el Spec

Todos los FR-ID-001 a FR-ID-020 del Spec quedan cubiertos por las tablas y flujos de este Plan. No se identificó ningún requisito del Spec sin una implementación técnica correspondiente.

---

**Estado: Borrador de Plan técnico.** Pendiente: resolver ADR-M1-01 antes de comenzar la implementación del flujo de OCR.
