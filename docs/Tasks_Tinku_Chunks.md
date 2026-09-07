# Chunks de Implementación — Tinku

> Cada chunk = una sesión de opencode = un branch = un PR. Referencia siempre a `Tasks_Tinku_Implementacion.md` para el detalle atómico de cada tarea; este archivo es la unidad de trabajo, no reemplaza al otro — tildá en ambos.
>
> Orden de ejecución: de arriba hacia abajo, salvo que se indique "en paralelo" explícitamente. No saltar un chunk cuyo bloque anterior no esté cerrado (tests incluidos), salvo los casos marcados como no bloqueantes.

---

## FASE 0 — Setup General

- [x] **Chunk 000-A** — Scaffold + schemas (T-000-01, T-000-02)
- [x] **Chunk 000-B** — Quartz persistido + eventos in-memory (T-000-03, T-000-04) — _alcance ampliado en ejecución real: incluyó verificación de V2\_\_m1_identidad.sql contra el Plan de M1 (no formaba parte de las tareas originales, surgió de la auditoría pre-000-B) y la decisión de perfil por defecto para que la app levante (idem). Mergeado a main en 4eab0b9._
- [ ] **Chunk 000-C** — Seguridad: JWT + hashing (T-000-05)
- [ ] **Chunk 000-D** — Cuentas externas: LiveKit, MercadoPago (T-000-06, T-000-07) — _mayormente manual, no requiere agente_
- [ ] **Chunk 000-E** — Servicio Python de matching: health check (T-000-08)
- [ ] **Chunk 000-F** — CI mínimo (T-000-09) — _no bloqueante; hacerlo antes de cerrar el 3er módulo (M4), no antes de arrancar_
- [ ] **Chunk 000-H** — Reconciliación de deuda técnica pre-existente (fuera de Tasks_Tinku_Implementacion.md original) — _ítem "ADR-000-01 (schema de Quartz)" ya completado fuera de orden durante el merge de 000-B, commit 4eab0b9. No pedirlo de nuevo en el prompt de este chunk._

## SPIKE — en paralelo desde el día 1, otra sesión/branch

- [ ] **Chunk SPIKE-A** — Evaluación de modelos + latencia en dispositivo real (T-SPIKE-01, T-SPIKE-02)
- [ ] **Chunk SPIKE-B** — Prototipo de buffer rotativo con MediaRecorder (T-SPIKE-03)
- [ ] **Chunk SPIKE-C** — Cierre: ADR-M3-01 documentado, con decisión final o escalado de riesgo (T-SPIKE-04)

> Bloquea a: Chunk M3-C (clasificador real). No bloquea a M1, M2, M4, M5.

---

## M1 — Identidad y Perfiles

_(requisito de todo lo demás — nada de M2 en adelante arranca sin esto cerrado)_

- [x] **Chunk M1-A** — Migraciones (T-M1-01, T-M1-02) — _cerrado como verificación: la migración `V2__m1_identidad.sql` se encontró pre-existente (commit `0616a05`) y ya había sido auditada contra el Plan de M1 durante Chunk 000-B sin requerir migración correctiva (ver nota en la línea de 000-B). No se creó en esta sesión. Incluye `usuarios` (CHECK `chk_adulto_tiene_capacidad`), `credenciales_academicas` (`ciclo_espera_hasta` nullable, sin backoff), `autorizaciones_tutor` (índice único) y `consentimientos_menor`; sin `certificados_antecedentes_penales` (Chunk M1-F). No se editó V2 (AGENTS.md §7). Verificado con `./mvnw verify` (16 tests OK, BUILD SUCCESS), `ddl-auto:validate` y `\d` de las 4 tablas._
- [x] **Chunk M1-B** — ADR-M1-01 (proveedor OCR) + integración (T-M1-03, T-M1-04) — _ADR-M1-01 hacia Tesseract/Tess4J (in-process, spa). `TesseractOcrService` (perfil prod) = preprocesado (deskew+contraste) → Tess4J → `DniParser`. Tests unitarios aislados verdes (DniParserTest 7 + PreprocesadorImagenTest 6). E2E vs binario nativo pendiente de entorno con Tesseract. Integración con Testcontainers requiere Docker (no verificada en este entorno)._
- [x] **Chunk M1-C** — Registro adulto + menor + backoff de OCR (T-M1-05, T-M1-06, T-M1-07) — _merged a main en la reconciliación de M1 (branch chunk/m2-e)._
- [x] **Chunk M1-D** — Capacidades combinables + registro Tutor (T-M1-08, T-M1-09) — _merged a main idem._
- [x] **Chunk M1-E** — Credenciales + autorizaciones + baja de menor (T-M1-10, T-M1-11, T-M1-12) — _merged a main idem._
- [x] **Chunk M1-F** — CAP: carga, revisión, vencimiento (T-M1-14, T-M1-15, T-M1-16, T-M1-17) — _merged a main idem. BR-CAP-02 no se lanza a producción sin validación legal externa (ver nota en Spec_M1)._
- [x] **Chunk M1-G** — Tests de integración de todas las Historias de Usuario, incluido CAP (T-M1-13) — _verificado: 111 tests verdes + OCR real (5.5.3). merged a main idem._

## M2 — Motor de Matching Semántico

_(requiere M1 cerrado)_

- [x] **Chunk M2-A** — Migraciones (T-M2-01, T-M2-02) — _merged a main con la reconciliación de M1._
- [x] **Chunk M2-B** — ADR-M2-01 (índice: pgvector vs. memoria) + endpoint `/match` en Python (T-M2-03, T-M2-04)
- [x] **Chunk M2-C** — Lógica Java: autorización del menor, exclusión de suspendidos, reordenamiento por reputación (T-M2-05, T-M2-06, T-M2-07)
- [x] **Chunk M2-D** — Endpoint de búsqueda orquestado + búsquedas guardadas (T-M2-08, T-M2-09)
- [x] **Chunk M2-E** — Tests (T-M2-10) — _verificado: 111 tests verdes + arranque real contra Postgres con pgvector._

> Nota: M2-C referencia M7 y M9 (reputación, suspensión) que todavía no existen como módulos completos en este punto del roadmap — usar stubs/interfaces mínimas y dejarlo señalado para cuando M7/M9 se implementen.

## M4 — Sistema de Reservas y Agenda

_(requiere M1 y M2 cerrados)_

- [x] **Chunk M4-A** — Migración con `EXCLUDE constraint` de superposición desde el inicio (T-M4-01)
- [ ] **Chunk M4-B** — Franjas + Solicitud de Sesión + aprobación (T-M4-02, T-M4-03, T-M4-04)
- [ ] **Chunk M4-C** — Reserva directa + timeout de `pendiente_pago` (T-M4-05, T-M4-06)
- [ ] **Chunk M4-D** — Reprogramación + cancelación con asimetría (T-M4-07, T-M4-08)
- [ ] **Chunk M4-E** — Listeners desde M9 (sanción) y M7 (calificación pendiente) — stubs si esos módulos no existen aún (T-M4-09, T-M4-10)
- [ ] **Chunk M4-F** — Test de condición de carrera en reservas simultáneas (T-M4-11)

## M5 — Motor de Pagos

_(requiere M4 cerrado; los listeners de sesión se completan cuando M3 exista)_

- [ ] **Chunk M5-A** — Migraciones + integración MercadoPago con split (T-M5-01, T-M5-02)
- [ ] **Chunk M5-B** — Webhook con validación de firma + listeners de eventos (T-M5-03, T-M5-04) — _los listeners de eventos de M3 quedan como stub hasta Chunk M3-E_
- [ ] **Chunk M5-C** — Job de liberación automática + reintentos con backoff (T-M5-05, T-M5-06)
- [ ] **Chunk M5-D** — Función única de reembolso total + flujo manual de reembolso parcial (T-M5-07, T-M5-08)
- [ ] **Chunk M5-E** — Precio de referencia regional (T-M5-09)
- [ ] **Chunk M5-F** — Tests (T-M5-10)

## M3 — Aula Virtual

_(Chunks A/B no dependen del spike; Chunk C sí — no arrancar M3-C hasta que SPIKE-C esté cerrado)_

- [x] **Chunk M3-A** — Migración + integración LiveKit (T-M3-01, T-M3-02)
- [ ] **Chunk M3-B** — Jobs de sala a T-5, no-show a T+10, finalización (T-M3-03, T-M3-04, T-M3-05)
- [ ] **Chunk M3-C** _(bloqueado por SPIKE-C)_ — Clasificador on-device + endpoint de killswitch, rama decidida en backend (T-M3-06, T-M3-07)
- [ ] **Chunk M3-D** — Evidencia de 30s + confirmación de la rama "adultos" (T-M3-08, T-M3-09)
- [ ] **Chunk M3-E** — Emisión de todos los eventos hacia M5 → _desbloquea Chunk M5-B (listeners reales)_
- [ ] **Chunk M3-F** — Test: manipulación de cliente no puede forzar la rama "adultos" con un menor presente (T-M3-11)

## M9 — Denuncias, Seguridad y Moderación

_(requiere M1, M3, M5 cerrados)_

- [ ] **Chunk M9-A** — Migración (T-M9-01)
- [ ] **Chunk M9-B** — Endpoint de denuncia con rechazo 403 a menores + jobs de plazos (48hs/5 días hábiles, track separado del kill-switch) (T-M9-02, T-M9-03)
- [ ] **Chunk M9-C** — Resolución de Denuncia estándar + resolución de Alerta de kill-switch (T-M9-04, T-M9-05)
- [ ] **Chunk M9-D** — Propagación de sanción a M1/M2/M4/M5 con reintentos ante fallo (T-M9-06)
- [ ] **Chunk M9-E** — Tests: denuncias cruzadas, propagación de sanción a los 4 módulos (T-M9-07)

## M6 — Resumen Automático de Sesiones

_(requiere M3 cerrado)_

- [ ] **Chunk M6-A** — Migración + listener de `sesion.finalizada` con validación de duración (T-M6-01, T-M6-02)
- [ ] **Chunk M6-B** — Verificación de Denuncia/Alerta activa antes de generar (T-M6-03)
- [ ] **Chunk M6-C** — Módulo de anonimización, aislado y testeado antes de conectar al pipeline (T-M6-04)
- [ ] **Chunk M6-D** — Integración LLM + reintentos con backoff (reutilizar patrón de M5) (T-M6-05, T-M6-06)
- [ ] **Chunk M6-E** — Recordatorio único a 24hs (T-M6-07)
- [ ] **Chunk M6-F** — Test de anonimización con datos reales de prueba (T-M6-08)

## M7 — Sistema de Calificaciones y Reputación

_(requiere M3 cerrado; retroalimenta a M2-C y M4-E, que hasta acá tenían stubs)_

- [ ] **Chunk M7-A** — Migración (T-M7-01)
- [ ] **Chunk M7-B** — Endpoint de calificación + perfil público con umbral de 5 (T-M7-02, T-M7-03)
- [ ] **Chunk M7-C** — Endpoint interno de calificación oculta (solo rol Moderación) + bloqueo de nueva reserva (T-M7-04, T-M7-05) → _reemplaza el stub del Chunk M4-E_
- [ ] **Chunk M7-D** — Recordatorio/edición de calificación + señales implícitas incrementales (T-M7-06, T-M7-07) → _reemplaza el stub del Chunk M2-C_
- [ ] **Chunk M7-E** — Test: ningún endpoint público filtra `tutor_a_estudiante` (T-M7-08)

## M8 — Panel de Administración

_(requiere M1, M9, M5 cerrados — es la interfaz sobre reglas ya definidas, no define nada nuevo)_

- [ ] **Chunk M8-A** — Migración + interceptor de auditoría común, ANTES que cualquier endpoint (T-M8-01, T-M8-02)
- [ ] **Chunk M8-B** — Endpoints de colas + ordenamiento por plazo restante (T-M8-03, T-M8-04)
- [ ] **Chunk M8-C** — Mapeo de enrutamiento de tickets de soporte (T-M8-05)
- [ ] **Chunk M8-D** — Test de aislamiento de roles (403 cruzado) (T-M8-06)

---

## Cierre — Integración Transversal

- [ ] **Chunk FIN-A** — E2E flujo feliz completo, sin mocks entre módulos propios (T-FIN-01)
- [ ] **Chunk FIN-B** — E2E rama de seguridad completa (T-FIN-02)
- [ ] **Chunk FIN-C** — Revisión de que ningún ADR quedó pendiente (T-FIN-03)

---

## Resumen de dependencias cruzadas a vigilar

| Chunk con stub                        | Se completa en                                     | Motivo                                               |
| ------------------------------------- | -------------------------------------------------- | ---------------------------------------------------- |
| M2-C (reputación/suspensión)          | M7-D, M9 (implícito vía M2-C ya usa consulta a M9) | M7 y M9 aún no existen cuando se hace M2             |
| M4-E (sanción/calificación pendiente) | M9-D, M7-C                                         | Idem                                                 |
| M5-B (listeners de eventos de sesión) | M3-E                                               | M3 emite los eventos que M5-B consume                |
| M3-C                                  | SPIKE-C                                            | El clasificador real depende del resultado del spike |

_Cualquier stub que quede sin reemplazar al llegar a Chunk FIN-A debe tratarse como bloqueante — no cerrar el flujo feliz E2E con un mock permanente disfrazado de stub temporal._
