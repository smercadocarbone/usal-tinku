# Plan Técnico: M9 — Denuncias, Seguridad y Moderación

**Basado en:** Spec_M9_Denuncias_Seguridad.md (aprobado)
**Stack (Constitución):** Java + Spring Boot, Quartz para SLA y escalado, eventos de dominio en memoria hacia M2/M4/M5.

---

## 1. Modelo de Datos (lógico)

### `denuncias`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| denunciante_id | UUID (FK → usuarios.id) | Nunca un `tipo = menor` (restricción de aplicación, FR-SEC-001). |
| denunciado_id | UUID (FK) | |
| sesion_id | UUID (FK), nullable | Puede no haber sesión asociada (denuncia de perfil). |
| motivo | enum(lista cerrada) | |
| evidencia_url | varchar, nullable | |
| estado | enum(`registrada`, `en_revision`, `resuelta_infundada`, `resuelta_fundada`, `escalada`) | |
| descargo_texto | varchar, nullable | |
| descargo_vence_at | timestamp | `en_revision.timestamp + 48h`. |
| sla_resolucion_vence_at | timestamp | `descargo_vence_at + 5 días hábiles` (FR-SEC-010). |
| admin_resolutor_id | UUID (FK), nullable | |
| created_at, resuelta_at | timestamp | |

*Nota: las `alertas_seguridad` (kill-switch) viven en la tabla de M3, no acá — son entidades separadas (BR-KS-03), aunque comparten la misma cola visual en M8.*

### `sanciones`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| usuario_sancionado_id | UUID (FK) | Puede ser Tutor, Estudiante o Adulto Responsable. |
| origen | enum(`denuncia`, `alerta_seguridad`) | Referencia a `denuncias.id` o `alertas_seguridad.id` según corresponda. |
| tipo | enum(`advertencia`, `suspension_temporal`, `suspension_definitiva`, `baneo_autoridades`) | |
| dias_suspension | int, nullable | Solo si `tipo = suspension_temporal` (7/15/30). |
| vigente_desde, vigente_hasta | timestamp | `vigente_hasta` nulo si es definitiva/baneo. |
| admin_id | UUID (FK) | |

## 2. Flujos Técnicos Clave

### 2.1 Presentar una Denuncia (US-1)
1. Endpoint valida que `denunciante.tipo != menor` (a nivel de autorización del endpoint, no solo de UI — un menor con su token no debe poder ni siquiera intentar la llamada).
2. Si `sesion_id` tiene una transacción en `retenido_escrow`, emite `denuncia.registrada` hacia M5.

### 2.2 Track del kill-switch — independiente del track de Denuncia estándar
Esto es lo más importante de implementar correctamente en este módulo, porque el Spec resolvió explícitamente una contradicción de plazos (12h vs 48h) separando los dos tracks — si el código los mezcla, se reintroduce el bug:
- Las `alertas_seguridad` **no pasan por el estado `en_revision` con `descargo_vence_at` de 48hs**. Tienen su propio campo (en la tabla de M3) para el plazo de 12hs y no bloquean la revisión del Admin de Moderación y Seguridad a la espera de un descargo.
- El descargo del Tutor, si llega, se adjunta al registro de la Alerta en cualquier momento — el código que arma la cola de M8 debe mostrarlo si existe, pero **nunca debe esperarlo** para habilitar la resolución dentro de las 12hs.

### 2.3 SLA y escalado (US-3, FR-SEC-010)
1. Job de Quartz programado a `sla_resolucion_vence_at` al momento de vencer `descargo_vence_at`.
2. Si el caso sigue en `en_revision` al ejecutarse el job, se marca visualmente con prioridad alta en la cola de M8 — **no se auto-resuelve en favor de ninguna de las partes**, solo fuerza visibilidad.

### 2.4 Escrow por caso, no por par (US-4, FR-SEC-011)
- El campo `sesion_id`/`reserva_id` de cada Denuncia es la unidad de pausa. Al resolver una Denuncia como infundada, el código reanuda la liberación **solo de esa transacción**, consultando `transacciones.reserva_id = denuncias.sesion_id` — nunca un query que busque "todas las transacciones pausadas entre estos dos usuarios".

### 2.5 Propagación de sanciones (US-6)
Al crear una fila en `sanciones`, se despacha un evento de dominio en memoria (Artículo IX de la Constitución) con el `usuario_sancionado_id` y su `tipo`. Los listeners (uno por módulo afectado) ejecutan, todos en la misma transacción o con manejo explícito de fallos parciales:
- **Listener M2:** marca `activo_para_matching = false` en `perfiles_tutor_matching` (solo si el sancionado es Tutor).
- **Listener M4:** cancela las Reservas futuras del sancionado (como Tutor o como pagador/beneficiario), con `motivo_cancelacion = sancion`, y emite el evento de cancelación que M5 consume para reembolsar.
- **Listener M1:** actualiza `usuarios.estado_cuenta = suspendida` si la sanción es `suspension_definitiva` o `baneo_autoridades`; para `suspension_temporal`, programa un job de Quartz a `vigente_hasta` que reactiva la cuenta automáticamente.

## 3. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `POST` | `/api/denuncias` | Rechaza si `denunciante.tipo == menor` (403, no solo oculto en UI). |
| `GET` | `/api/admin/moderacion/denuncias` | Cola de M8. |
| `GET` | `/api/admin/moderacion/alertas-seguridad` | Cola separada, prioridad alta. |
| `POST` | `/api/denuncias/{id}/descargo` | Disponible en cualquier momento, incluso post-resolución inicial (apelación). |
| `POST` | `/api/admin/moderacion/denuncias/{id}/resolver` | Body incluye tipo de sanción si corresponde. |
| `POST` | `/api/admin/moderacion/alertas-seguridad/{id}/resolver` | Reactivar o confirmar/agravar sanción. |

## 4. ADRs de este Módulo

- **ADR-M9-01:** mecanismo técnico de "cadena de custodia" para casos de contenido ilegal (US-5) — probablemente requiere asesoría legal externa sobre el formato exacto del expediente, no es una decisión puramente técnica. Marcar como bloqueante para esa Historia de Usuario específica, no para el resto del módulo.

## 5. Trazabilidad con el Spec

FR-SEC-001 a FR-SEC-012 cubiertos. El listener de propagación de sanciones (2.5) es el componente con más superficie de fallo silencioso — si un listener falla y los demás no, una cuenta podría quedar sancionada en M1 pero seguir apareciendo en el matching de M2. Se recomienda que la implementación real trate esto como una transacción con compensación (saga) o, dado el tamaño del proyecto, como mínimo con reintentos y alerta si algún listener falla — no asumir que "en memoria, en el mismo proceso" garantiza atomicidad entre listeners independientes.

---

**Estado: Borrador de Plan técnico.**
