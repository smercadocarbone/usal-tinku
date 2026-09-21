# Spec: M7 — Sistema de Calificaciones y Reputación

**Módulo:** M7 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M3 (evento `sesion.finalizada`), M1 (identidad de las partes), M6 (módulo de anonimización reutilizado por FR-REP-011, agregado en la auditoría 2026-09-18)
**Alimenta a:** M2 (señales implícitas para el orden del matching, BR-MATCH-01), M4 (bloqueo de próxima reserva del Tutor si no calificó), M9 (señal temprana de Estudiantes problemáticos)

---

## 1. Resumen

Este módulo gestiona la confianza bidireccional entre Estudiante y Tutor: la calificación explícita (estrellas) que ve el mundo, la calificación oculta que el Tutor hace del Estudiante (uso interno, casi obligatoria), y las señales implícitas de comportamiento que alimentan el orden del matching sin exponerse nunca al usuario. Resuelve también el OE7 original (algoritmo anti-manipulación): la solución elegida para el MVP es un umbral mínimo de calificaciones antes de mostrar el promedio público, no un algoritmo de detección de fraude.

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Calificación del Estudiante hacia el Tutor
*Como* Estudiante, *quiero* calificar a mi Tutor al terminar la clase, *para* dejar constancia de mi experiencia y ayudar a otros a elegir bien.

- **Dado** que la sesión finalizó, **cuando** se me solicite calificar, **entonces** puedo dar de 1 a 5 estrellas y un comentario opcional. Esta calificación es pública, visible en el perfil del Tutor (FR-REP-001).
- **Dado** que no califiqué, **cuando** pasen 24 horas desde la finalización, **entonces** recibo un único recordatorio (FR-REP-004) — no se insiste más allá de esa vez.
- **Dado** que ya publiqué mi calificación, **cuando** quiera modificarla o eliminarla, **entonces** puedo hacerlo dentro de las **48 horas** posteriores a publicarla; pasado ese plazo, queda definitiva (FR-REP-005).

### US-1bis — Moderación del comentario público _(agregado, auditoría 2026-09-18)_
*Como* Tutor calificado públicamente, *quiero* que un comentario no pueda exponer mis datos personales ni contener lenguaje abusivo sin ningún filtro, *para* que la calificación siga siendo sobre la experiencia de la clase, no un canal abierto de acoso o de exposición de datos.

- **Dado** que el Estudiante escriba un comentario opcional al calificar, **cuando** lo envíe, **entonces** el mismo filtro de anonimización que ya usa M6 (regex + NER liviano, reutilizado — no reinventado, Artículo I de la Constitución) se aplica sobre el texto antes de publicarlo, reemplazando datos de contacto/documento detectados por marcadores genéricos (FR-REP-011).
- **Dado** que el comentario contenga lenguaje que la lista cerrada de motivos de Denuncia de M9 ya cubre (ej. amenazas), **cuando** el Tutor lo considere así, **entonces** puede denunciarlo por el canal ya existente de M9 — este Spec no crea un mecanismo de moderación de contenido nuevo y paralelo al de M9, solo la anonimización automática de FR-REP-011 (FR-REP-012).

### US-2 — Calificación del Tutor hacia el Estudiante (oculta, casi obligatoria)
*Como* Tutor, *quiero* dejar constancia de cómo fue el Estudiante, *para* que Tinku tenga una señal temprana de comportamiento problemático.

- **Dado** que la sesión finalizó, **cuando** se me solicite calificar al Estudiante, **entonces** puedo dar de 1 a 5 estrellas. Esta calificación es oculta — nunca visible para el Estudiante ni para otros Tutores, solo para uso interno del Admin de Moderación y Seguridad, como señal temprana para M9 (FR-REP-002).
- **Dado** que no haya calificado a un Estudiante de una sesión anterior, **cuando** intente ver o aceptar su próxima reserva (de cualquier Estudiante, no solo el pendiente de calificar), **entonces** el sistema se lo bloquea hasta que complete esa calificación pendiente (FR-REP-006). No bloquea el cierre de la sesión en sí — solo el avance a la siguiente.

### US-3 — Señales implícitas de reputación (solo Tutores)
*Como* Tinku, *quiero* registrar el comportamiento real del Tutor más allá de lo que dice una estrella, *para* mejorar la calidad del matching con datos objetivos.

- **Dado** que ocurran eventos de comportamiento del Tutor (puntualidad, tasa de recontratación, tasa de cancelación/no-show, tiempo de respuesta a mensajes, antigüedad/volumen de sesiones), **cuando** eso pase, **entonces** se registran internamente y alimentan el orden del matching (M2, FR-MATCH-003) — nunca se exponen al usuario ni se fusionan con la calificación explícita (BR-REP-01).

### US-4 — Salvaguarda anti-manipulación (resolución del OE7)
*Como* Tinku, *quiero* que el sistema de calificación bidireccional no sea fácil de manipular, *para* que una estrella realmente signifique algo.

- **Dado** que un Tutor tenga menos de **5 calificaciones** públicas recibidas, **cuando** se muestre su perfil, **entonces** no se exhibe un promedio de estrellas — se muestra un estado tipo "Tutor nuevo, todavía sin calificaciones suficientes" (FR-REP-007). Al alcanzar la quinta calificación, el promedio se activa y se muestra desde ese momento.
- **Dado** que solo se puede calificar tras una Sesión real y finalizada, **cuando** eso se aplique, **entonces** ya elimina la forma más obvia de manipulación (reseñas falsas sin sesión real) — garantizado por diseño, sin lógica adicional.
- **Dado** que un Tutor reciba una calificación de 1-2 estrellas, **cuando** eso ocurra, **entonces** aplica la salvaguarda ya definida en M2 (BR-MATCH-01: no se sugiere activamente durante 24hs) — este Spec no la duplica, solo la referencia.

## 3. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-REP-001 | Calificación pública (1-5 estrellas + comentario opcional) del Estudiante hacia el Tutor al finalizar la sesión. |
| FR-REP-002 | Calificación oculta (1-5 estrellas) del Tutor hacia el Estudiante — visible solo para uso interno del Admin de Moderación y Seguridad. |
| FR-REP-003 | Registro interno de señales implícitas de comportamiento del Tutor, sin exposición ni fusión con la calificación explícita. |
| FR-REP-004 | Recordatorio único a las 24hs si el Estudiante no calificó, sin insistencia posterior. |
| FR-REP-005 | Ventana de 48hs para editar o eliminar una calificación pública ya publicada; luego es definitiva. |
| FR-REP-006 | Bloqueo de visualización/aceptación de la próxima reserva del Tutor si tiene una calificación de Estudiante pendiente de una sesión anterior. |
| FR-REP-007 | El promedio público de estrellas de un Tutor se oculta hasta alcanzar un mínimo de 5 calificaciones; antes de eso, se muestra un estado de "Tutor nuevo". |
| FR-REP-008 | Sesiones sin `sesion.finalizada` no se pueden calificar ni cuentan para el umbral de FR-REP-007. |
| FR-REP-009 | El bloqueo de FR-REP-006 aplica solo a nuevas Reservas, nunca a Reservas ya confirmadas antes de la calificación pendiente. |
| FR-REP-010 | La calificación oculta 1-2 estrellas del Tutor sobre el Estudiante no dispara ningún efecto automático — es señal pura para M9. |
| FR-REP-011 _(agregado, auditoría 2026-09-18)_ | El comentario público opcional pasa por el mismo filtro de anonimización que M6 (regex + NER liviano) antes de publicarse — reutiliza el módulo, no lo reimplementa. |
| FR-REP-012 _(agregado)_ | Contenido abusivo en un comentario se gestiona por el canal de Denuncia ya existente de M9 — este Spec no crea un mecanismo de moderación paralelo. |

## 4. Reglas de Negocio Aplicables (referencia)

- **BR-REP-01:** las señales implícitas influyen solo en el orden del matching, nunca se exponen ni se fusionan con la calificación explícita.
- **BR-REP-02:** sin consecuencias automáticas en el MVP por reputación implícita baja.
- **BR-MATCH-01 (definida en M2):** un Tutor con 1-2 estrellas no se sugiere activamente durante 24hs.

## 5. Casos Borde — Todos Resueltos

| # | Caso / Pregunta | Resolución |
|---|---|---|
| 1 | Estudiante no califica tras 24hs | Recordatorio único, sin insistencia posterior (FR-REP-004). |
| 2 | Umbral mínimo para mostrar el promedio público del Tutor (resolución de OE7) | 5 calificaciones mínimas; antes se muestra "Tutor nuevo" (FR-REP-007). |
| 3 | Edición/eliminación de la calificación del Estudiante | Permitida hasta 48hs después de publicada, luego es definitiva (FR-REP-005). |
| 4 | Tutor no califica al Estudiante | Se bloquea su próxima reserva (ver cualquier Estudiante) hasta que complete la calificación pendiente (FR-REP-006). |
| 5 | Calificación de sesiones que nunca emitieron `sesion.finalizada` (canceladas, kill-switch) | No se pueden calificar en absoluto, y **no cuentan** para el umbral de 5 calificaciones de FR-REP-007 (FR-REP-008). |
| 6 | Alcance exacto del bloqueo de FR-REP-006 | Bloquea únicamente **nuevas** Reservas (ver/aceptar) mientras haya una calificación de Estudiante pendiente — nunca afecta Reservas ya confirmadas antes de que existiera esa calificación pendiente (FR-REP-009). |
| 7 | Efecto de la calificación oculta 1-2 estrellas (Tutor sobre Estudiante) | Ninguno automático en el MVP — es señal pura para M9, sin disparar nada por sí sola (FR-REP-010). A diferencia de BR-MATCH-01, que sí es automática pero aplica solo a la calificación pública del Tutor. |

## 6. Fuera de Alcance de este Spec

- Las sanciones formales derivadas de mala conducta — M9.
- El algoritmo exacto de ponderación en el matching (fórmula, pesos) — Plan técnico, no este Spec.

## 7. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica aparece en este documento.
- [x] Las 4 preguntas abiertas fueron resueltas.
- [x] Revisado contra la Constitución (Artículo IV — accesibilidad: el umbral de 5 calificaciones protege a Tutores nuevos de zonas con menos densidad de Estudiantes, no solo de manipulación).

---

**Estado: APROBADO.** Listo para pasar al Plan técnico de M7.
