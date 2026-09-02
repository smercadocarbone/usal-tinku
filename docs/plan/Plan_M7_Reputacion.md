# Plan Técnico: M7 — Sistema de Calificaciones y Reputación

**Basado en:** Spec_M7_Reputacion.md (aprobado)
**Stack (Constitución):** Java + Spring Boot, Quartz para el recordatorio único y la ventana de edición.

---

## 1. Modelo de Datos (lógico)

### `calificaciones`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| sesion_id | UUID (FK → sesiones_aprendizaje.id) | Solo sesiones con `estado = finalizada` (FR-REP-008) — constraint de aplicación, verificado antes de insertar. |
| autor_id | UUID (FK → usuarios.id) | Quien califica. |
| direccion | enum(`estudiante_a_tutor`, `tutor_a_estudiante`) | Determina la visibilidad (ver 2.2). |
| estrellas | int (1-5) | |
| comentario | varchar, nullable | Solo aplica a `estudiante_a_tutor` (la calificación oculta no tiene comentario público, no hace falta el campo para esa dirección). |
| editable_hasta | timestamp | `created_at + 48h` (FR-REP-005) — solo relevante para `estudiante_a_tutor`. |
| created_at | timestamp | |

*Índice único: (`sesion_id`, `autor_id`, `direccion`) — una sola calificación por sesión y dirección.*

### `señales_implicitas_tutor` (agregado, no un log crudo — ver ADR-M7-01)
| Campo | Tipo | Notas |
|---|---|---|
| tutor_id | UUID (PK, FK) | |
| puntualidad_promedio | decimal | |
| tasa_recontratacion | decimal | |
| tasa_cancelacion_noshow | decimal | |
| tiempo_respuesta_promedio_min | int | |
| sesiones_dictadas_total | int | |
| updated_at | timestamp | Recalculado incrementalmente en cada evento relevante, no en batch nocturno — para que M2 siempre lea un valor actualizado. |

## 2. Flujos Técnicos Clave

### 2.1 Umbral de 5 calificaciones (US-4, FR-REP-007)
- Se calcula `COUNT(*) FROM calificaciones WHERE direccion = 'estudiante_a_tutor' AND tutor_id = ?` al servir el perfil público. Mientras sea `< 5`, el endpoint de perfil devuelve `promedio: null, estado: "tutor_nuevo"` en vez del promedio — la decisión de ocultar vive en la capa de lectura, no se trunca ni se falsea el dato almacenado.

### 2.2 Visibilidad por dirección (US-1, US-2)
- El endpoint de perfil público de un Tutor **nunca** hace join con calificaciones `direccion = 'tutor_a_estudiante'` — ni siquiera agregadas. Es una separación a nivel de query, no de "campo oculto en el frontend", para que un error de frontend no pueda exponer el dato por accidente.
- El endpoint que sirve el perfil de un Estudiante a sí mismo tampoco expone sus propias calificaciones recibidas (`tutor_a_estudiante`) — solo un endpoint interno de Admin (M8, rol Moderación y Seguridad) puede leerlas.

### 2.3 Bloqueo de próxima Reserva por calificación pendiente (US-2, FR-REP-006/009)
1. Antes de que M4 confirme una nueva Reserva de un Tutor, consulta: `EXISTS (SELECT 1 FROM sesiones_aprendizaje s JOIN reservas r ON ... WHERE r.tutor_id = ? AND s.estado = 'finalizada' AND NOT EXISTS (SELECT 1 FROM calificaciones c WHERE c.sesion_id = s.id AND c.direccion = 'tutor_a_estudiante'))`.
2. Si existe, M4 rechaza la creación de la nueva Reserva con un motivo específico ("Tenés una calificación pendiente") — **no afecta Reservas ya confirmadas antes de esta verificación** (FR-REP-009): el chequeo ocurre únicamente en el momento de crear una Reserva nueva, nunca retroactivamente sobre las existentes.

### 2.4 Recordatorio único y ventana de edición (US-1, FR-REP-004/005)
- Job de Quartz a `sesion.finalizada.timestamp + 24h`: si no existe fila en `calificaciones` para esa sesión con `direccion = estudiante_a_tutor`, dispara **una única** notificación — el job no se reprograma a sí mismo, se ejecuta una vez y termina (evita el riesgo de convertirse accidentalmente en un recordatorio recurrente).
- La edición/eliminación valida `now() <= editable_hasta` en el propio endpoint — no requiere un job adicional, es una validación de lectura del timestamp ya almacenado.

## 3. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `POST` | `/api/sesiones/{id}/calificacion` | Body incluye `direccion` implícita por el rol del autor autenticado (un Estudiante siempre califica `estudiante_a_tutor`, nunca puede enviar la otra dirección). |
| `PATCH` | `/api/calificaciones/{id}` | Valida `editable_hasta`. |
| `GET` | `/api/tutores/{id}/perfil` | Expone `promedio` solo si `count >= 5`. |
| `GET` | `/api/admin/moderacion/calificaciones-ocultas/{estudiante_id}` | Rol Moderación y Seguridad únicamente (M8). |

## 4. ADRs de este Módulo

- **ADR-M7-01:** ¿la tabla `señales_implicitas_tutor` se mantiene como agregado recalculado incrementalmente (como está diseñado arriba), o como un log crudo de eventos que se agrega en el momento de leer? Se eligió el agregado por simplicidad de lectura (Artículo I) — el costo es que hay que tener cuidado de recalcular correctamente ante cualquier evento nuevo (no-show, cancelación, recontratación); un log crudo sería más fácil de auditar/corregir retroactivamente pero más caro de leer en cada búsqueda de M2. Revisar si en la práctica el mantenimiento del agregado resulta más complejo de lo esperado.

## 5. Trazabilidad con el Spec

FR-REP-001 a 010 cubiertos. La separación de la sección 2.2 (visibilidad por dirección a nivel de query, no de frontend) es el punto que más vale la pena revisar en cualquier code review de este módulo — es la única defensa real contra que la calificación oculta del Tutor termine expuesta por un descuido.

---

**Estado: Borrador de Plan técnico.**
