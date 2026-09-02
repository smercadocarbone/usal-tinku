# Spec: M6 — Resumen Automático de la Sesión

**Módulo:** M6 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M3 (transcript de la sesión + evento `sesion.finalizada`), M4 (contexto de la reserva: materia, tutor, duración), M1 (perfil, minoría de edad), M9 (pausa de generación si hay Denuncia activa sobre la sesión, FR-SEC-003)
**Alimenta a:** M7 (el resumen como insumo indirecto), M9 (el material queda disponible como referencia si el Admin revisa un caso)

---

## 1. Resumen

Este módulo genera, de forma automática y para toda sesión que lo amerite, un resumen de aprendizaje en lenguaje natural a partir de la transcripción de la videollamada (M3). El resumen es descriptivo de la sesión, **no evaluativo** del Estudiante ni del Tutor — la evaluación es territorio de M7, y la evidencia de conducta es territorio de M9. Respeta el Artículo II: si hubo un menor, el resumen es visible también para su Adulto Responsable.

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Generación automática
*Como* Estudiante, *quiero* recibir un resumen de lo que vimos en la clase, *para* repasar y saber si la sesión valió la pena.

- **Dado** que la sesión finalizó (`sesion.finalizada` emitida por M3) con una duración efectiva de al menos 10 minutos, **cuando** el evento se procese, **entonces** el resumen se genera automáticamente en un plazo máximo de 10 minutos y queda disponible en la sesión, sin que nadie lo pida.
- **Dado** que la sesión duró menos de 10 minutos efectivos (ej. no-show cercano, abandono temprano), **cuando** el evento se procese, **entonces** no se genera resumen — la sesión muestra el resultado transaccional (finalizada/reembolso) sin sección de resumen.

### US-2 — Contenido del resumen
*Como* Estudiante, *quiero* que el resumen me sea útil, *para* no leer un texto genérico.

- **Dado** que se genera el resumen, **cuando** lo leo, **entonces** incluye: temas tratados, conceptos clave explicados, ejercicios o ejemplos trabajados, dudas que quedaron abiertas, y sugerencia de qué reforzar en la próxima sesión. Redactado en tono claro, adaptado al nivel escolar del Estudiante (dato de M1).
- **Dado** que la sesión tuvo tramos degradados a texto (FR-AULA-002), **cuando** se genera el resumen, **entonces** la transcripción de ese texto es fuente igualmente válida.

### US-3 — Resumen y menor de edad
*Como* Usuario con capacidad Adulto Responsable, *quiero* ver qué se trabajó en la sesión de mi menor a cargo, *para* acompañar su aprendizaje.

- **Dado** que el perfil que participó es un menor, **cuando** el resumen esté disponible, **entonces** es visible tanto para el menor como para su Adulto Responsable (coherente con el Artículo II: el adulto tiene visibilidad plena, el menor no tiene contenido "oculto" de su propia clase).
- **Dado** que el Estudiante es adulto, **cuando** se genera el resumen, **entonces** solo él lo ve (más el Tutor, ver US-4).

### US-4 — Tutor y resumen
*Como* Tutor, *quiero* tener un registro de lo que trabajé con cada Estudiante, *para* dar continuidad en próximas sesiones.

- **Dado** que la sesión finalizó, **cuando** el resumen se genera, **entonces** el Tutor ve el mismo resumen que el Estudiante — misma fuente, sin una versión adicional evaluativa en el MVP.

### US-5 — Privacidad del pipeline de IA
*Como* Tinku, *quiero* que el procesamiento del transcript no exponga datos personales, *para* cumplir el Artículo II y el Artículo V (minimización de datos).

- **Dado** que se envíe la transcripción al modelo para generar el resumen, **cuando** se construya el prompt, **entonces** se anonimizan nombres de personas, datos de contacto, enlaces y datos de pago; el resumen final nunca contiene datos personales de los participantes.

## 3. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-SUM-001 | Generación automática de resumen para toda sesión con ≥10 min de duración efectiva, disparada por `sesion.finalizada`. Sin acción del usuario. |
| FR-SUM-002 | Fuentes válidas: transcripción de audio/video y transcripción de los tramos degradados a texto. |
| FR-SUM-003 | Estructura fija: temas tratados, conceptos clave, ejercicios trabajados, dudas abiertas, sugerencia para la próxima sesión. |
| FR-SUM-004 | Visibilidad: Estudiante y Tutor ven el mismo resumen; si el Estudiante es menor, también su Adulto Responsable. |
| FR-SUM-005 | Anonimización de datos personales (nombres, contactos, enlaces, pagos) antes del envío al modelo y en la salida. |
| FR-SUM-006 | El resumen se genera una única vez por sesión (idempotente). Sin regeneración en el MVP; correcciones de contenido son caso de soporte del Admin. |
| FR-SUM-007 | Latencia objetivo: resumen disponible en ≤10 min tras la finalización. Ante falla, reintento hasta 3 veces con backoff; si persiste, la sesión queda sin resumen y el evento se loguea para monitoreo (la sesión no se marca como fallida). |
| FR-SUM-008 | El resumen no incluye evaluación de personas, tono moralizante, ni predicciones de desempeño. Si el transcript contiene contenido marcado por el kill-switch (M3), el resumen se suspende y el material queda reservado a M9. |

## 4. Reglas de Negocio Aplicadas (referencia)

- Artículo II: visibilidad del Adulto Responsable sobre la actividad del menor.
- Artículo V: minimización y anonimización de datos personales.
- BR-KS-03: ninguna Alerta ni evidencia del kill-switch se expone fuera de M9 — de ahí FR-SUM-008.

## 5. Casos Borde — Todos Resueltos

| # | Caso / Pregunta | Resolución |
|---|---|---|
| 1 | Sesión interrumpida por corte <50% (`sesion.interrumpida`, M3) | No se genera resumen (hubo reembolso; no hay "clase" que resumir). |
| 2 | Transcripción vacía o de mala calidad | Si el contenido útil está por debajo de un umbral mínimo, no se genera resumen; se muestra "Resumen no disponible para esta sesión". Nunca se inventa contenido. |
| 3 | El Estudiante pide reembolso mientras se genera el resumen | El resumen igual se genera si la clase ocurrió ≥10 min; el reembolso no lo anula (el aprendizaje ocurrió). |
| 4 | Denuncia activa sobre la sesión | La generación se pausa (coherente con FR-SEC-003 de M9); el material queda disponible para M9/Admin. **Decisión explícita:** si la disputa se resuelve con reembolso, el resumen igual queda accesible para el Estudiante — el reembolso es sobre el dinero, no sobre el valor educativo ya generado. |
| 5 | Dos sesiones finalizan al mismo tiempo | Procesamiento independiente por sesión; no hay estado compartido entre resúmenes. |
| 6 | El transcript contiene datos de contacto intercambiados entre los participantes | La anonimización (FR-SUM-005) los excluye del resumen. La captura de contacto fuera de plataforma es tema de M9, no del resumen. |

**Supuestos validados en esta ronda:** umbral de 10 minutos, mismo resumen visible para Tutor y Estudiante, sin regeneración en el MVP — los tres se mantienen tal como estaban propuestos.

## 6. Fuera de Alcance de este Spec

- La transcripción en sí (captura y storage) — vive en M3, este módulo solo la consume.
- Evaluación del Tutor, calificaciones, análisis de sentimiento del feedback — M7.
- Alertas de seguridad y uso del transcript como evidencia — M9.
- Regeneración o edición del resumen por el usuario (MVP: una generación por sesión).

## 7. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica nueva aparece (el proveedor de LLM queda para el Plan técnico).
- [x] Preguntas/supuestos abiertos validados.
- [x] Revisado contra la Constitución (Artículo II — visibilidad del adulto; Artículo V — minimización de datos).

---

**Estado: APROBADO.**
