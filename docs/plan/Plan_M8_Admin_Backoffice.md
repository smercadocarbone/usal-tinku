# Plan Técnico: M8 — Panel de Administración

**Basado en:** Spec_M8_Admin_Backoffice.md (aprobado)
**Stack (Constitución):** Java + Spring Boot (mismas colas expuestas vía API REST, consumidas por un frontend interno separado del de usuarios finales — no necesita ser parte de la misma PWA pública).

---

## 1. Modelo de Datos (lógico)

### `admins`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | Tabla separada de `usuarios` — un Admin no es un Estudiante/Tutor/menor, es personal interno de Tinku. |
| email | varchar, UNIQUE | |
| password_hash | varchar | |
| rol | enum(`moderacion_seguridad`, `soporte_financiero`) | Un Admin tiene exactamente un rol en el MVP — no combinable (a diferencia del modelo de capacidades de Usuario en M1; son conceptos distintos, no hace falta reutilizar ese patrón acá). |

### `tickets_soporte`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| usuario_id | UUID (FK → usuarios.id) | Quién lo generó. |
| origen_modulo | varchar | Ej. `"M1.credencial_agotada"`, `"M5.pago_fallido"` — usado para el enrutamiento automático. |
| rol_asignado | enum(`moderacion_seguridad`, `soporte_financiero`) | Derivado de `origen_modulo` en el momento de crear el ticket (tabla de mapeo simple, no lógica compleja). |
| estado | enum(`abierto`, `en_curso`, `resuelto`) | |
| admin_id | UUID (FK), nullable | |

### `log_auditoria_admin`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| admin_id | UUID (FK) | |
| accion | varchar | Ej. `"aprobar_credencial"`, `"resolver_denuncia"`, `"aplicar_sancion"`. |
| entidad_tipo, entidad_id | varchar, UUID | A qué caso/registro afectó. |
| detalle_json | jsonb | Snapshot de la decisión (ej. qué sanción, con qué motivo). |
| created_at | timestamp | **Tabla append-only** — sin `UPDATE` ni `DELETE` permitidos a nivel de permisos de base de datos, no solo de código de aplicación (NFR-SEC-04 de la Constitución: "no editable ni eliminable desde el panel" se refuerza acá también a nivel de motor de base de datos). |

## 2. Enrutamiento de Colas por Rol

| Cola | Rol | Origen de los datos |
|---|---|---|
| Credenciales pendientes | Moderación y Seguridad | Tabla `credenciales_academicas` de M1. |
| Alertas de kill-switch | Moderación y Seguridad | Tabla `alertas_seguridad` de M3, prioridad máxima (12hs). |
| Denuncias | Moderación y Seguridad | Tabla `denuncias` de M9. |
| Pagos fallidos | Soporte Financiero | Tabla `transacciones` de M5, `intentos_liberacion >= 3`. |
| Tabla de precios regional | Soporte Financiero | Tabla `precios_referencia_regional` de M5. |
| Tickets de soporte | Ambos, según `rol_asignado` | Tabla propia de este módulo. |

**Cada endpoint de este módulo valida el `rol` del Admin autenticado contra la cola que intenta acceder** — un Admin de Soporte Financiero no puede ni leer ni resolver una Denuncia, a nivel de autorización de API, no solo de que el frontend no le muestre el botón.

## 3. Flujos Técnicos Clave

### 3.1 Ordenamiento de colas por urgencia
Cada cola se ordena por el campo de plazo restante correspondiente (`ciclo_espera_hasta` en Credenciales, `descargo_vence_at`/`sla_resolucion_vence_at` en Denuncias, la ventana de 12hs en Alertas) — no por fecha de creación. El elemento con menos tiempo restante siempre aparece primero.

### 3.2 Toda acción pasa por el log de auditoría
No hay un endpoint de este módulo que modifique estado en M1/M5/M9 sin, en la misma transacción, insertar una fila en `log_auditoria_admin`. Esto se implementa como un aspecto/interceptor común a todos los controladores de este módulo, no repetido manualmente en cada uno — para que sea imposible agregar un endpoint nuevo que se olvide de auditar.

### 3.3 Enrutamiento de tickets
Al crear un ticket, una tabla de mapeo simple (`origen_modulo → rol_asignado`) decide el rol destino — no requiere lógica de negocio compleja ni un motor de reglas, es una tabla de config chica que el equipo de operaciones puede editar sin desplegar código.

## 4. API (contratos de alto nivel)

| Método | Endpoint | Rol requerido |
|---|---|---|
| `GET` | `/api/admin/moderacion/credenciales` | Moderación y Seguridad |
| `PATCH` | `/api/admin/moderacion/credenciales/{id}` | Moderación y Seguridad |
| `GET` | `/api/admin/moderacion/alertas-seguridad` | Moderación y Seguridad |
| `POST` | `/api/admin/moderacion/alertas-seguridad/{id}/resolver` | Moderación y Seguridad |
| `GET` | `/api/admin/moderacion/denuncias` | Moderación y Seguridad |
| `POST` | `/api/admin/moderacion/denuncias/{id}/resolver` | Moderación y Seguridad |
| `GET` | `/api/admin/financiero/pagos-fallidos` | Soporte Financiero |
| `POST` | `/api/admin/financiero/pagos-fallidos/{id}/reintentar` | Soporte Financiero |
| `POST` | `/api/admin/financiero/precios-regionales` | Soporte Financiero |
| `GET` | `/api/admin/tickets` | Filtrado por `rol_asignado` del Admin autenticado |
| `GET` | `/api/admin/auditoria` | Ambos roles, solo lectura de sus propias acciones (o de todas, si se decide que la auditoría es transversal — **ver Pregunta Abierta de Plan abajo**) |

## 5. Preguntas de Implementación (no bloquean el Spec, se resuelven acá)

- ¿Un Admin de un rol puede ver el log de auditoría del otro rol (transparencia total interna) o solo el propio? El Spec no lo definió porque es un detalle de implementación de permisos, no una regla de negocio — **recomendación: visibilidad total entre roles para la auditoría** (Artículo X de la Constitución, la auditoría existe justamente para poder revisar decisiones ajenas si hace falta), a confirmar con el equipo antes de implementar.

## 6. Trazabilidad con el Spec

FR-ADM-001 a 008 cubiertos. El interceptor de auditoría (3.2) es la pieza que garantiza que NFR-SEC-04 de la Constitución no dependa de que cada desarrollador se acuerde de loguear manualmente — es la forma correcta de implementar un requisito transversal como este.

---

**Estado: Borrador de Plan técnico.**
