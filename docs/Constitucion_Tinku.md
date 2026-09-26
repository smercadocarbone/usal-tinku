# Constitución del Proyecto — Tinku

> Este documento gobierna todas las decisiones posteriores. Ningún Spec, Plan o Task de una feature puntual puede contradecirlo. Se modifica solo mediante enmienda explícita y versionada (ver Artículo XII), nunca implícitamente.
>
> **Distinción importante:** los Artículos son principios durables — cambiarlos requiere una enmienda formal. La sección final ("Registro de Decisiones Técnicas Actuales") son elecciones de proveedor/tecnología concretas — pueden cambiar vía un ADR normal, sin tocar este documento.

**Versión:** 2.4
**Contexto del equipo (informativo, no es una regla en sí misma):** actualmente 1 desarrollador, presupuesto de infraestructura USD 0-100/mes durante desarrollo. Los principios de abajo están pensados para este contexto, pero siguen siendo buena práctica aunque el equipo crezca.

---

## Parte I — Principios de Negocio

### Artículo I — La confianza se refuerza con verificación documental, no reemplaza a la ausencia de acceso estatal directo _(enmendado v2.3)_

Tinku no tiene acceso a un sistema de verificación de antecedentes penales en tiempo real ni a una integración directa con el Registro Nacional de Reincidencia. **El mecanismo de confianza para sesiones 1:1 con un Menor incluye el Certificado de Antecedentes Penales (CAP), obligatorio y vigente para que un Tutor quede habilitado a dictar clases a Menores** (ADR-M1-04, que reemplaza a ADR-M1-02). Los Tutores que solo enseñan a adultos no cargan el CAP: su habilitación para matching se apoya en la Credencial Académica, la calificación explícita y el kill-switch, mientras que la habilitación para Menores agrega el CAP `aprobado` con un máximo de vigencia de 12 meses (FR-ID-026, Tabla de Tiempos). El criterio de rechazo fuera de la lista automática (BR-CAP-02) es **fail-closed**: no habilita hasta que la asesoría legal defina la política interna de descalificación (PT1). Se acepta el riesgo residual de un documento adulterado o mal evaluado, mitigado con revisión manual del Admin de Moderación y Seguridad y con la verificación de firma digital del CAP diferida a futuro (T04, PT2).

### Artículo II — La seguridad del menor prevalece sobre cualquier feature o métrica de negocio

Ante cualquier conflicto entre una decisión de producto y la seguridad de un usuario menor de edad, prevalece la segunda. Ningún flujo que involucre a un menor puede depender exclusivamente de que el propio menor confirme o niegue una situación de riesgo en tiempo real.

### Artículo III — Transparencia de precio para el Estudiante

El precio que ve el Estudiante/Adulto Responsable es siempre el precio final. La comisión de plataforma se descuenta del lado del Tutor, nunca aparece como un cargo adicional visible — la simplicidad del precio importa más que la transparencia de cómo se reparte internamente.

### Artículo IV — Accesibilidad económica sobre maximización de ingresos de corto plazo

Ante una decisión que mejora el margen de Tinku pero encarece o complica el acceso de familias de bajo poder adquisitivo, se prioriza el acceso. La comisión de plataforma y el precio de referencia regional deben revisarse con esta prioridad, no solo con criterio financiero.

### Artículo V — Minimización de datos _(enmendado v2.4)_

No se persiste ningún dato — en particular video — más allá de lo estrictamente necesario para cumplir una regla de negocio ya aprobada. Toda nueva necesidad de retención de datos debe justificarse explícitamente contra la Ley 25.326 antes de implementarse, nunca incorporarse "por las dudas".

**Única excepción a la prohibición de grabar (ADR-M3-04):** el audio de una sesión puede grabarse solo si se cumplen las cuatro condiciones a la vez: (1) **solo audio**, nunca video; (2) la Reserva contrató el adicional de resumen; (3) **ningún participante es Menor**; (4) los dos participantes aceptaron expresamente la cláusula de grabación en su versión vigente. El audio se borra apenas se obtiene el transcript y, aunque algo falle, a las 24 hs como máximo (Tabla de Tiempos). Fuera de esas condiciones la prohibición sigue sin excepciones.

### Artículo VI — Control de alcance del MVP

El alcance del MVP está cerrado en nueve módulos (Identidad y Perfiles, Motor de Matching, Aula Virtual, Reservas y Agenda, Motor de Pagos, Resumen Automático, Calificaciones y Reputación, Panel de Administración, Denuncias y Seguridad). Ninguna funcionalidad nueva se incorpora sin una enmienda explícita a este documento.

---

## Parte II — Principios Técnicos

### Artículo VII — Simplicidad ante todo

Toda decisión técnica se justifica contra el tamaño real del equipo y el presupuesto — no contra "buenas prácticas" genéricas de empresas de otra escala. Ante dos alternativas que cumplen el mismo requisito, se elige la más simple salvo justificación explícita en un ADR.

### Artículo VIII — Monolito modular

El sistema es un monolito modular en el backend elegido, organizado en los nueve módulos del Artículo VI, con límites de dominio claros. Está prohibido crear microservicios propios adicionales salvo justificación técnica explícita documentada como ADR (no una preferencia de estilo).

### Artículo IX — Comunicación entre módulos

Síncrona, dentro del mismo proceso, para flujos que requieren respuesta inmediata. Eventos de dominio en memoria para efectos secundarios de un solo disparo con múltiples reacciones. Prohibido introducir un message broker externo mientras no cambie una premisa fundamental de escala del proyecto.

### Artículo X — Persistencia de estado crítico

Ningún timeout de negocio (escrow, aprobaciones, kill-switch, cancelaciones) puede vivir en memoria volátil. Todo timeout de seguridad o financiero se implementa con un scheduler persistido en base de datos, para sobrevivir a un reinicio o redeploy.

### Artículo XI — Mitigación de riesgo técnico antes que velocidad de feature

El componente de mayor riesgo técnico del proyecto (clasificador on-device del kill-switch) se prototipa (spike) antes de comprometerse a fechas de entrega sobre el resto del sistema.

---

## Artículo XII — Gobernanza de este documento

Esta Constitución se modifica exclusivamente mediante una enmienda explícita, versionada (se incrementa el número en el encabezado) y con su justificación registrada. Ningún Spec ni Plan de una feature puntual puede introducir un cambio a este documento de forma implícita.

---

## Registro de Decisiones Técnicas Actuales

> Esto NO es ley constitucional — son las elecciones vigentes hoy. Cambiar una fila es un ADR normal, no requiere enmendar la Constitución.

| Capa                              | Elección actual                                                                                                                                                                             | Estado                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Backend                           | Java + Spring Boot                                                                                                                                                                          | Decidido, evaluado contra Go y Node.js (ADR-000-02, 2026-09-21)                             |
| Base de datos                     | PostgreSQL, un solo motor, schemas por módulo                                                                                                                                               | Decidido                                                                                    |
| Cache / tiempo real propio        | Redis (Upstash)                                                                                                                                                                             | Decidido                                                                                    |
| Videollamada                      | LiveKit Cloud                                                                                                                                                                               | Decidido                                                                                    |
| Pagos                             | MercadoPago Marketplace con OAuth por Tutor (cada Tutor cobra en su cuenta; Tinku, su comisión con `marketplace_fee`; escrow como ventana lógica de 24 hs) — ADR-M5-02; sandbox de test users para desarrollo                                                                                                               | Decidido                                                                                    |
| Motor de matching                 | Python (sentence-transformers + FAISS), proceso separado                                                                                                                                    | Decidido                                                                                    |
| Transcripción + resumen de sesión | Dos pasos: transcript → anonimización (FR-SUM-005) → LLM solo con texto. El "audio directo en una sola llamada" se descartó porque el audio no se puede anonimizar antes de salir | Actualizado (ADR-M6-03, 2026-09-25) — la fuente del transcript se decide con T08/TS1 |
| LLM (resumen)                     | GPT-4o (OpenAI), activo con `LLM_PROVEEDOR=gpt-4o`; sin eso, fail-closed | Decidido (ADR-M6-03, 2026-09-25) — Gemini 2.0 Flash descartado |
| Frontend                          | Next.js + React (justificado por SEO en páginas públicas de Tutores)                                                                                                                        | Decidido, con alternativa más liviana (Vite + React) documentada si el SEO deja de importar |
| Scheduler de jobs                 | Persistido en base de datos (ej. Quartz sobre Spring Boot)                                                                                                                                  | Decidido — instancia única, sin clustering (ADR-000-04, 2026-09-21)                         |
| OCR de documento (registro/adulto/menor/tutor) | Tesseract local, invocado como programa del sistema (parsing DNI propio + preprocesamiento para fotos de celular) | Decidido (ADR-M1-01, 2026-09-04; integración por programa en vez de Tess4J: ADR-M1-08, 2026-09-26) — requiere el paquete tesseract-ocr + spa en el entorno |
| Clasificador de contenido NSFW on-device (kill-switch) | NSFWJS (MobileNetV2 5-clases) sobre TensorFlow.js, modelo auto-hosted; NudeNet descartado | Decidido (ADR-M3-01, 2026-09-08) — latencia estimada ~42-60ms/inferencia en gama media, throttling configurable |

---

## NFRs no negociables

- **Disponibilidad:** 95% mensual durante el piloto.
- **Seguridad:** TLS 1.2+; hashing bcrypt/argon2; log de auditoría de toda acción de Admin.
- **Privacidad:** cumplimiento de la Ley 25.326, con especial cuidado en datos de menores.
- **Rendimiento:** latencia de videollamada <200ms; video con ajuste automático y continuo de calidad, sin "modo degradado" visible.
- **Mantenibilidad:** sostenible por 1-2 desarrolladores, sin conocimiento tácito no documentado.

---

## Historial de Enmiendas

### Enmienda v2.0 → v2.1 — Artículo I (CAP)

**Motivo:** el Certificado de Antecedentes Penales (CAP) argentino es un documento oficial verificable (PDF con firma digital del Registro Nacional de Reincidencia), tramitable por el propio Tutor sin costo de infraestructura para Tinku. Fortalece directamente el Artículo II (seguridad del menor prevalece), a costa de fricción adicional en el onboarding del Tutor — fricción aceptada explícitamente porque el Artículo II tiene prioridad sobre velocidad de onboarding.
**Cambia:** Artículo I (texto de arriba).
**No cambia:** el resto de los Artículos, el Registro de Decisiones Técnicas, los NFRs.
**Bajado a Spec/Plan en:** Spec_M1_Identidad_Perfiles.md (US-6, FR-ID-021 a 024), Plan_M1_Identidad_Perfiles.md (tabla `certificados_antecedentes_penales`, sección 2.4).

### Enmienda v2.1 → v2.2 — Artículo I (retiro del CAP)

**Motivo:** el CAP introducía fricción de onboarding medible en una etapa de piloto donde la
oferta de Tutores es el recurso más escaso, y BR-CAP-02 (revisión de antecedentes fuera de la
lista de rechazo automático) exigía un criterio de descalificación legal que, sin asesoría
profesional contratada, recaía sin respaldo sobre el único desarrollador del proyecto. Se prioriza
explícitamente velocidad de onboarding y reducción de riesgo legal operativo por sobre el nivel de
verificación de antecedentes que exigía la v2.1 — ver justificación completa, alternativas
descartadas (incluida una alternativa de alcance acotado a sesiones con Menores, rechazada
explícitamente) y riesgo aceptado en **ADR-M1-02**.
**Cambia:** Artículo I (texto de arriba, reemplaza el requisito de CAP).
**No cambia:** el resto de los Artículos — en particular, el Artículo II (la seguridad del menor
prevalece) sigue vigente sin excepción; esta enmienda reduce un control preventivo documental, no
autoriza ninguna otra desviación del Artículo II —, el Registro de Decisiones Técnicas, los NFRs.
**Bajado a Spec/Plan en:** Spec_M1_Identidad_Perfiles.md (US-6, FR-ID-021 a 025 y BR-CAP-01/02
marcados `RETIRADO`), Plan_M1_Identidad_Perfiles.md (sección 2.4 y filas de API de CAP marcadas
`RETIRADO`). El texto original se conserva en ambos documentos como registro histórico, no se
borra.

### Enmienda v2.2 → v2.3 — Artículo I (retorno del CAP, acotado a Menores)

**Motivo:** la tesis (Cap. 6, riesgo R-10) retoma la alternativa que ADR-M1-02 había descartado —
CAP obligatorio solo para sesiones con Menores — porque resuelve los dos motivos del retiro:
los Tutores que solo enseñan a adultos se registran sin trámite adicional, y el criterio de
rechazo de BR-CAP-02 pasa a fail-closed (no habilita) hasta la asesoría legal presupuestada
(PT1, 2026-09-23). Justificación completa, riesgo residual y alternativas en **ADR-M1-04**.
**Cambia:** Artículo I (texto de arriba, reemplaza el retiro total de la v2.2 por un retorno
acotado a la habilitación para Menores).
**No cambia:** el resto de los Artículos — en particular, el **Artículo II** (la seguridad del menor
prevalece) sigue vigente sin excepción —, el Registro de Decisiones Técnicas, los NFRs.
**Bajado a Spec/Plan en:** Spec_M1_Identidad_Perfiles.md (US-6, FR-ID-021 a 026 y BR-CAP-01/02),
Tabla_Tiempos_Tinku.md (fila "Vigencia del CAP"), Specs M2 y M4 (referencias a FR-ID-026).

---

### Enmienda v2.3 → v2.4 — Artículo V (grabación de solo audio para el resumen)

**Motivo:** la tesis (DT3/DT5) convierte el resumen automático en un adicional pago, y el resumen
necesita el contenido de la clase. Sin grabación no hay transcript (AUD-024) y M6 no puede funcionar.
La excepción se acota a lo mínimo que el resumen necesita: solo audio, solo entre adultos, solo con
el adicional contratado y con consentimiento expreso de los dos, borrado al transcribir y a las 24 hs
como máximo. Justificación completa, mecanismo (grabación en el navegador del Tutor, sin costo de
infraestructura) y alternativas en **ADR-M3-04**.
**Cambia:** Artículo V (se agrega la excepción acotada).
**No cambia:** el resto de los Artículos. El **Artículo II** sigue sin excepción: una sesión con un
Menor nunca se graba, aunque el adicional figure contratado (el backend lo rechaza al reservar y
vuelve a controlarlo al recibir el audio). El buffer rotativo del kill-switch no cambia.
**Bajado a Spec/Plan en:** Spec_M3 (grabación y subida del audio), Spec_M4 (adicional al reservar),
Spec_M5 (BR-PAG-11, reembolso parcial del adicional), Spec_M6 (FR-SUM-001), Tabla_Tiempos_Tinku.md
(fila "Retención máxima del audio del resumen").

---

_Fin de la Constitución v2.4._
