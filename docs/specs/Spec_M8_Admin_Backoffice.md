# Spec: M8 — Panel de Administración

**Módulo:** M8 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M1 (Credenciales pendientes), M9 (Alertas de Seguridad, Denuncias), M5 (fallas de pago)
**Ejecuta decisiones definidas en:** M1, M9, M5 — M8 es la interfaz; las reglas de negocio (plazos, escalas de sanción, criterios) ya están definidas en esos módulos, no se redefinen acá.
**Referencia de tiempos:** Tabla_Tiempos_Tinku.md

---

## 1. Resumen

Este módulo es el panel operativo desde donde el equipo de Tinku ejecuta todas las decisiones manuales que el resto del sistema ya dejó definidas. **Hay dos roles de Admin, con colas y permisos separados:**

- **Moderación y Seguridad:** Credenciales pendientes, Alertas de kill-switch, Denuncias, aplicación de sanciones.
- **Soporte Financiero:** intervención manual en pagos fallidos, mantenimiento de la tabla de precios regional.

Los tickets de soporte (US-7) se enrutan automáticamente al rol correspondiente según el contexto que los originó (ej. un problema de Credencial va a Moderación; un problema de pago va a Soporte Financiero). La auditoría (US-6) aplica por igual a ambos roles — toda acción de cualquiera de los dos queda registrada de la misma forma.

No se define ninguna regla de negocio nueva acá — es la superficie desde donde se aplican las que ya existen en M1, M5 y M9.

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Cola de Credenciales pendientes
*Como* Admin de Moderación y Seguridad, *quiero* ver todas las Credenciales de Tutores esperando revisión, *para* aprobarlas o rechazarlas dentro del plazo de 48hs ya comprometido (M1).

- **Dado** que un Tutor cargue o recargue una Credencial, **cuando** eso ocurra, **entonces** aparece en esta cola, ordenada por tiempo restante antes de vencer el plazo de 48hs (FR-ADM-001).
- **Dado** que el Admin apruebe o rechace, **cuando** lo haga, **entonces** dispara el efecto ya definido en M1 (habilitación para matching, o inicio del ciclo de reintento con backoff).

### US-2 — Cola de Alertas de Seguridad (kill-switch)
*Como* Admin de Moderación y Seguridad, *quiero* ver las Alertas del kill-switch por separado y con máxima prioridad, *para* cumplir la ventana de 12hs definida en M9.

- **Dado** que M3 genere una Alerta de Seguridad, **cuando** eso ocurra, **entonces** aparece en esta cola por encima de cualquier otra, con el tiempo restante de las 12hs visible (FR-ADM-002).
- **Dado** que el Admin abra el caso, **cuando** lo revise, **entonces** ve la evidencia (clip de 30s), la opinión del Adulto Responsable si la rama fue de menor, y el descargo del Tutor si ya lo presentó — sin que su ausencia bloquee la resolución dentro de las 12hs (coherente con FR-SEC-004 de M9).
- **Dado** que el Admin resuelva, **cuando** lo haga, **entonces** puede reactivar la cuenta o confirmar/agravar la sanción — la suspensión preventiva en sí ya estaba activa desde antes (M3), esta acción es la resolución, no el inicio de la suspensión.

### US-3 — Cola de Denuncias estándar
*Como* Admin de Moderación y Seguridad, *quiero* ver las Denuncias en curso, *para* resolverlas dentro del ciclo de 48hs de descargo + 5 días hábiles definido en M9.

- **Dado** que una Denuncia pase a `en_revision`, **cuando** eso ocurra, **entonces** aparece en esta cola, separada de la de kill-switch, ordenada por plazo restante (FR-ADM-003).
- **Dado** que se cumplan los 5 días hábiles sin resolución (FR-SEC-010 de M9), **cuando** eso ocurra, **entonces** el caso se marca visualmente con prioridad alta en esta cola.

### US-4 — Aplicar sanciones
*Como* Admin de Moderación y Seguridad, *quiero* elegir la sanción correspondiente desde un caso ya evaluado, *para* ejecutar la escala definida en M9 sin ambigüedad.

- **Dado** que resuelva un caso (Denuncia o Alerta) como fundado, **cuando** elija la sanción, **entonces** selecciona de la escala cerrada ya definida (advertencia, suspensión temporal de 7/15/30 días, suspensión definitiva, baneo + reporte a autoridades — FR-SEC-005 de M9); el sistema propaga los efectos automáticamente a M1, M2, M4 y M5 según a quién se sancione.

### US-5 — Intervención manual en pagos fallidos
*Como* Admin de Soporte Financiero, *quiero* ver los pagos que agotaron sus reintentos automáticos, *para* resolverlos manualmente.

- **Dado** que la liberación de un pago agote los 3 reintentos automáticos (FR-PAG-007 de M5), **cuando** eso ocurra, **entonces** aparece en una cola separada de intervención manual, con el historial de intentos visible (FR-ADM-004).

### US-6 — Auditoría de acciones del Admin
*Como* Tinku, *quiero* que toda acción del Admin quede registrada, *para* poder auditar decisiones sensibles después (NFR-SEC-04 de la Constitución).

- **Dado** que el Admin ejecute cualquier acción sobre una cuenta, credencial, denuncia o pago, **cuando** eso ocurra, **entonces** se registra con fecha, acción, caso vinculado, y admin responsable — no editable ni eliminable desde el panel (FR-ADM-005).

### US-7 — Canal de soporte
*Como* Tutor (u otro usuario) que llega a un límite ya definido en otro módulo (ej. agotar los 3 intentos de OCR/Credencial de M1), *quiero* poder contactar a alguien, *para* no quedar sin salida.

- **Dado** que un usuario use el canal de "Contactar a soporte", **cuando** lo haga, **entonces** genera un ticket enrutado automáticamente al rol correspondiente según el contexto que lo originó (ej. un límite de Credencial va a Moderación y Seguridad; un problema de pago va a Soporte Financiero) (FR-ADM-006).

### US-8 — Mantenimiento de la tabla de precios regional
*Como* Admin de Soporte Financiero, *quiero* poder actualizar la tabla de precios de referencia por provincia, *para* mantenerla vigente con la revisión trimestral ya definida en M5.

- **Dado** que corresponda la revisión trimestral (BR-PAG-02), **cuando** el equipo de operaciones actualice la tabla desde este panel, **entonces** el nuevo valor aplica únicamente a Tutores que configuren su perfil de ahí en adelante — no modifica retroactivamente el precio ya fijado de un Tutor existente (coherente con FR-PAG-006 de M5) (FR-ADM-007).

## 3. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-ADM-001 | Cola de Credenciales pendientes, ordenada por plazo restante de 48hs. |
| FR-ADM-002 | Cola de Alertas de Seguridad (kill-switch), máxima prioridad, con tiempo restante de 12hs visible. |
| FR-ADM-003 | Cola de Denuncias estándar, separada de la anterior, ordenada por plazo restante. |
| FR-ADM-004 | Cola de intervención manual para pagos que agotaron reintentos automáticos. |
| FR-ADM-005 | Registro de auditoría de toda acción del Admin — no editable ni eliminable desde el panel. |
| FR-ADM-006 | Canal de soporte: tickets vinculados a cuenta y contexto de origen. |
| FR-ADM-007 | Mantenimiento de la tabla de precios regional, con aplicación solo hacia adelante, nunca retroactiva. |
| FR-ADM-009 _(agregado 2026-09-26)_ | Temas sugeridos: Moderación y Seguridad ve los temas buscados sin Tutor directo que se pidieron al menos N veces (configurable, 3), agrupados por área, sin datos de quién los buscó, y los marca como resueltos al actualizar el catálogo (FR-MATCH-012, ADR-M2-04). |
| FR-ADM-008 | Dos roles de Admin con colas y permisos separados: Moderación y Seguridad, y Soporte Financiero. Los tickets de soporte se enrutan automáticamente según su origen. |

## 4. Casos Borde y Preguntas Abiertas

| # | Caso / Pregunta | Resolución |
|---|---|---|
| 1 | Roles del Admin | Dos roles con colas y permisos separados: Moderación y Seguridad (Credenciales, kill-switch, Denuncias, sanciones) y Soporte Financiero (pagos fallidos, tabla de precios) (FR-ADM-008). |
| 2 | Choque entre dos Admins del mismo rol abriendo el mismo caso | Sin mecanismo de bloqueo en el MVP — dado el tamaño del equipo, el riesgo es bajo; se resuelve con la primera decisión guardada. Revisar si el equipo crece (Artículo I de la Constitución: no sobre-diseñar para un problema que todavía no existe). |
| 3 _(agregado, auditoría 2026-09-18)_ | El único Admin de Moderación y Seguridad no está disponible (enfermedad, vacaciones) y un SLA de 12hs (kill-switch) o 48hs (descargo) está por vencer | **Riesgo operacional aceptado explícitamente para el piloto, sin mecanismo de guardia/backup en el MVP** — mismo criterio que el caso 2 (Artículo I: no sobre-diseñar para 1 desarrollador). No hay hoy un segundo rol que pueda cubrir la cola de Moderación y Seguridad si la única persona no está. Revisar obligatoriamente antes de escalar el volumen de Tutores/Estudiantes activos, junto con la revisión de ADR-M1-02 (retiro del CAP) — ambos comparten la misma dependencia de un solo operador humano para la seguridad del menor. |

## 5. Fuera de Alcance de este Spec

- Un dashboard de métricas/BI del negocio (retención, volumen, etc.) — no es parte del MVP, y no hay una regla de negocio que lo requiera todavía.
- Las reglas de negocio en sí (plazos, escalas de sanción, criterios de aprobación) — ya están definidas en M1, M5 y M9; este módulo solo las ejecuta.

## 6. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica aparece en este documento.
- [x] Las 2 preguntas abiertas originales fueron resueltas, + 1 riesgo operacional agregado en la auditoría 2026-09-18.
- [x] Revisado contra la Constitución (Artículo X — auditoría y NFR-SEC-04; Artículo I — no se agregó bloqueo de concurrencia sin necesidad probada).

---

**Estado: APROBADO.**
