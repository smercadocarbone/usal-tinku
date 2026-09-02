# Plan Técnico: M4 — Sistema de Reservas y Agenda

**Basado en:** Spec_M4_Reservas_Agenda.md (aprobado)
**Stack (Constitución):** Java + Spring Boot, PostgreSQL, **Quartz + JobStore persistido** para todos los timeouts (Artículo IV/X).

---

## 1. Modelo de Datos (lógico)

### `franjas_disponibilidad`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| tutor_id | UUID (FK) | |
| dia_semana / fecha_especifica | — | Recurrente o puntual — decisión de UX, no bloquea el resto. |
| hora_inicio, hora_fin | time | |
| activa | boolean | |

### `solicitudes_sesion` (FR-RES-021/022)
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| menor_id | UUID (FK → usuarios.id) | Quién la genera. |
| tutor_id | UUID (FK) | |
| horario_propuesto | timestamp | Debe caer dentro de una franja publicada. |
| estado | enum(`pendiente`, `convertida`, `expirada`, `rechazada`) | |
| created_at, expira_at | timestamp | `expira_at = created_at + 48h` (FR-RES-022, Tabla de Tiempos). |

### `reservas`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| pagador_id | UUID (FK → usuarios.id) | Estudiante adulto, o Adulto Responsable pagando por un menor. |
| beneficiario_id | UUID (FK → usuarios.id) | Quién toma la clase (puede ser el mismo que `pagador_id`, o un menor a cargo). |
| tutor_id | UUID (FK) | |
| solicitud_origen_id | UUID (FK → solicitudes_sesion.id), nullable | Null si fue reserva directa, sin Solicitud previa. |
| horario | timestamp | |
| precio | decimal | **Se congela en el momento de crear la Reserva** — es el valor que persiste incluso si la Reserva se reprograma después (FR-PAG-013 de M5). |
| estado | enum(`pendiente_pago`, `confirmada`, `en_curso`, `finalizada`, `cancelada`, `no_show_estudiante`, `no_show_tutor`, `no_show_doble`) | |
| motivo_cancelacion | enum(`voluntaria`, `timeout_pago`, `revocacion_autorizacion`, `sancion`), nullable | Resuelve E-05 de la ronda de QA (auditoría de por qué se canceló). |
| created_at | timestamp | |

**Restricción a nivel de base de datos (no solo de aplicación):** `EXCLUDE constraint` sobre (`tutor_id`, rango de horario) y sobre (`beneficiario_id`, rango de horario) para reservas en estado `confirmada` o posterior — implementa FR-RES-007 (prevención de superposición) de forma que ni una condición de carrera entre dos requests simultáneos pueda violarla; no alcanza con validarlo solo en el código de la aplicación.

## 2. Flujos Técnicos Clave

### 2.1 Solicitud → Reserva (US-2, US-3)
1. El menor (cuenta propia, permisos restringidos) crea una `solicitud_sesion`. No toca la tabla `reservas` en absoluto.
2. Job de Quartz programado a `expira_at`: si sigue en `pendiente`, la pasa a `expirada`. No dispara ninguna otra consecuencia (no hay nada que reembolsar, nunca hubo pago).
3. El Adulto Responsable ve sus Solicitudes pendientes (vía notificación + listado). Al aprobar, el backend crea la fila en `reservas` con `estado = pendiente_pago` y dispara el flujo de pago (ver 2.3).

### 2.2 Auto-confirmación dentro de franja publicada (US-4)
1. Al crear una Reserva (directa o desde Solicitud), el backend valida que el horario caiga dentro de una `franja_disponibilidad` activa del Tutor — si no, rechaza antes de llegar a intentar el pago.
2. No hay paso de aceptación del Tutor: la validación de franja **es** la aceptación implícita.

### 2.3 Timeout de `pendiente_pago` (US-4, FR-RES-020)
1. Al crear la Reserva, se programa un job de Quartz a `created_at + 15min`.
2. Si el job se ejecuta y la Reserva sigue en `pendiente_pago`, la pasa a `cancelada` (`motivo_cancelacion = timeout_pago`) y libera el horario (la `EXCLUDE constraint` de la sección 1 deja de aplicar sobre esa fila).
3. Si el pago se confirma antes (webhook de MercadoPago, ver Plan de M5), el job se cancela explícitamente al pasar a `confirmada` — no se deja que corra solo y sea ignorado; se remueve del scheduler para no acumular jobs muertos.

### 2.4 Reprogramación (US-5)
1. Valida que falten ≥24hs del horario **actual** de la Reserva.
2. Valida que el nuevo horario caiga en una franja publicada disponible.
3. Actualiza `horario` en la misma fila — **no crea una Reserva nueva, no toca el campo `precio`, no genera ninguna llamada a MercadoPago.**
4. Reprograma los jobs de Quartz asociados (recordatorios T-24h/T-5, creación de sala en M3) al nuevo horario.

### 2.5 Cancelación y no-show (US-6, US-7)
- La asimetría (quién cancela/falta determina el reembolso) se resuelve **en M5**, no acá — este módulo solo cambia el `estado` de la Reserva y emite el evento correspondiente (`sesion.no_show_estudiante`, etc., recibidos desde M3) o lo emite directamente si la cancelación ocurre antes de que exista una Sesión en M3 (cancelación manual, no vía no-show).

## 3. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `POST` | `/api/tutores/franjas` | Publicar disponibilidad. |
| `POST` | `/api/solicitudes` | Crea una Solicitud (auth: menor). |
| `GET` | `/api/solicitudes/pendientes` | Para el Adulto Responsable. |
| `POST` | `/api/solicitudes/{id}/aprobar` | Convierte en Reserva + inicia pago. |
| `POST` | `/api/reservas` | Reserva directa (Estudiante adulto, o Adulto Responsable sin Solicitud previa). |
| `POST` | `/api/reservas/{id}/reprogramar` | Valida ventana de 24hs. |
| `POST` | `/api/reservas/{id}/cancelar` | Aplica asimetría vía evento a M5. |

## 4. ADRs de este Módulo

- **ADR-M4-01:** Recurrencia de franjas de disponibilidad (¿franjas semanales recurrentes, o el Tutor publica fecha por fecha?). No afecta ninguna regla de negocio ya definida, es puramente de UX/modelo de datos — se puede resolver en el sprint de implementación sin volver al Spec.

## 5. Trazabilidad con el Spec

FR-RES-001 a FR-RES-022 cubiertos. El punto de mayor cuidado en la implementación es la `EXCLUDE constraint` de la sección 1 — sin ella, dos requests concurrentes podrían crear reservas superpuestas antes de que la validación de aplicación llegue a ejecutarse (condición de carrera real, no teórica, dado que MercadoPago involucra un round-trip externo entre la creación de la Reserva y la confirmación del pago).

---

**Estado: Borrador de Plan técnico.**
