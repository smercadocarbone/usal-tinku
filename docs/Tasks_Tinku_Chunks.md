# Chunks de Implementación — Tinku

> Cada chunk = una sesión de opencode = un branch = un PR. Referencia siempre a `Tasks_Tinku_Implementacion.md` para el detalle atómico de cada tarea; este archivo es la unidad de trabajo, no reemplaza al otro — tildá en ambos.
>
> Orden de ejecución: de arriba hacia abajo, salvo que se indique "en paralelo" explícitamente. No saltar un chunk cuyo bloque anterior no esté cerrado (tests incluidos), salvo los casos marcados como no bloqueantes.

---

## FASE 0 — Setup General

- [x] **Chunk 000-A** — Scaffold + schemas (T-000-01, T-000-02)
- [x] **Chunk 000-B** — Quartz persistido + eventos in-memory (T-000-03, T-000-04) — _alcance ampliado en ejecución real: incluyó verificación de V2\_\_m1_identidad.sql contra el Plan de M1 (no formaba parte de las tareas originales, surgió de la auditoría pre-000-B) y la decisión de perfil por defecto para que la app levante (idem). Mergeado a main en 4eab0b9._
- [x] **Chunk 000-C** — Seguridad: JWT + hashing (T-000-05) — _en producción: Spring Security + JJWT 0.12.6 + bcrypt, confirmado por `SecurityHttpTest`/`JwtAuthTest` y por el uso real en los 383 tests (verificado 2026-09-21, JDK 21 + Testcontainers) de la suite. Auditoría 2026-09-18: `Tasks_Tinku_Implementacion.md` nunca tildó T-000-05 pese a que el código lleva meses funcionando — corregido acá._
- [x] **Chunk 000-D** — Cuentas externas: LiveKit, MercadoPago (T-000-06, T-000-07) — _confirmado por integración real de `LiveKitService` (M3-A) y `MercadoPagoClient`/webhook con firma HMAC (M5-A/B) contra las APIs reales, no solo stubs._
- [x] **Chunk 000-E** — Servicio Python de matching: health check (T-000-08) — _`GET /health` real, consumido además por `SaludInfraestructuraService` de M8 (ADR-M8-01)._
- [~] **Chunk 000-F** — CI mínimo (T-000-09) — _parcial: `.github/workflows/ci-backend.yml` existe (build + `mvn verify` con path filter en `backend/**`), pero no hay pipeline para `frontend/` ni `matching-service/`. Pendiente real, no cosmético._
- [ ] **Chunk 000-H** — Reconciliación de deuda técnica pre-existente (fuera de Tasks_Tinku_Implementacion.md original) — _ítem "ADR-000-01 (schema de Quartz)" ya completado fuera de orden durante el merge de 000-B, commit 4eab0b9. No pedirlo de nuevo en el prompt de este chunk._

## SPIKE — en paralelo desde el día 1, otra sesión/branch

- [x] **Chunk SPIKE-A** — Evaluación de modelos + latencia en dispositivo real (T-SPIKE-01, T-SPIKE-02) — _cerrado: NSFWJS (MobileNetV2 5-clases) elegido sobre NudeNet/NsfwSpy; ~12ms en dev, rango estimado gama media ~42-60ms; harness de navegador listo para fijarlo_
- [x] **Chunk SPIKE-B** — Prototipo de buffer rotativo con MediaRecorder (T-SPIKE-03) — _cerrado: chunks concatenados reproducibles (salvedad keyframe al inicio), `spikes/mediarecorder-buffer/`_
- [x] **Chunk SPIKE-C** — Cierre: ADR-M3-01 documentado, con decisión final o escalado de riesgo (T-SPIKE-04) — _cerrado: ADR-M3-01 Aceptado (NSFWJS + TensorFlow.js, modelo auto-host) merged a main; resultado positivo, riesgo de cronograma no escalado_

> ~~Bloquea a: Chunk M3-C (clasificador real).~~ Resuelto 2026-09-08: M3-C desbloqueado.

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

- [ ] **Chunk M2-F** — Catálogo granular de temas + búsqueda por nombre (T-M2-11…T-M2-17) — _contratos cerrados en `docs/plan/Plan_M2_Temas.md` (rama `chunk/m2-f-temas`): trayectos+temas con descripciones (reemplaza el uso de `materias_niveles`), `tema_ids` por Tutor, búsqueda por nombre/materia en Java antes de `/match`, recompute de embeddings en matching-service. Nota: la "Res. CFE 371/23" del encargo no existe (res. 371 es de 2020/E TP) — fuentes reales en Plan_M2_Temas.md §1. V12 ya tiene el skeleton; seed en V13._

## M4 — Sistema de Reservas y Agenda

_(requiere M1 y M2 cerrados)_

- [x] **Chunk M4-A** — Migración con `EXCLUDE constraint` de superposición desde el inicio (T-M4-01)
- [x] **Chunk M4-B** — Franjas + Solicitud de Sesión + aprobación (T-M4-02, T-M4-03, T-M4-04) _(suite 128 tests, 0 fallos; merge a `main` pendiente)_
- [x] **Chunk M4-C** — Reserva directa + timeout de `pendiente_pago` (T-M4-05, T-M4-06) _(suite 166 tests, 0 fallos; merge a `main` pendiente)_
- [x] **Chunk M4-D** — Reprogramación + cancelación con asimetría (T-M4-07, T-M4-08) _(suite 176 tests, 0 fallos; merge a `main` pendiente; incluye listeners M3 `reserva.cancelada`/`reserva.reprogramada` que desagendan / re-agendan la Sesión derivada)_
- [x] **Chunk M4-E** — Listeners desde M9 (sanción) y M7 (calificación pendiente) — stubs si esos módulos no existen aún (T-M4-09, T-M4-10) _(suite 179 tests, 0 fallos; merge a `main` pendiente; stubs a reemplazar por M9-D y M7-C)_
- [x] **Chunk M4-F** — Test de condición de carrera en reservas simultáneas (T-M4-11) _(suite 180 tests, 0 fallos; merge a `main` pendiente)_

## M5 — Motor de Pagos

_(requiere M4 cerrado; los listeners de sesión se completan cuando M3 exista)_

- [x] **Chunk M5-A** — Migraciones + integración MercadoPago con split (T-M5-01, T-M5-02)
- [x] **Chunk M5-B** — Webhook con validación de firma + listeners de eventos (T-M5-03, T-M5-04) — _cerrado en `chunk/m5-b`: webhook MP real (firma HMAC X-Signature + anti-replay, reconcilia contra `/v1/payments/{id}` y confirma la Reserva) reemplaza al `confirmar-pago-simulado` de M3-B; listeners 1:1 con la tabla del Plan §2 (ver NOTA en T-M5-04). Pendiente fuera de scope: `denuncia.resuelta` (M5-C/D, payload) y `reserva.cancelada` tardía (M5-D). Suite 198 tests, 0 fallos; merge FF a `main` sin pr — todos los chunks anteriores del legacy se mergearon a `main` igual_
- [x] **Chunk M5-C** — Job de liberación automática + reintentos con backoff (T-M5-05, T-M5-06) — _cerrado en `chunk/m5-c`: LiberacionEscrowService + LiberacionEscrowJob (Quartz persistido, agenda en `sesion.finalizada`, cancela en reembolso/denuncia), backoff 5/15/1h con alerta a Soporte (port stub hasta M8) y cola `pagos-fallidos` vía `intentos_liberacion`. Provider real `LiberacionProveedorMercadoPago` reemplaza el fail-closed (verifica `approved` contra la API — la retención de cuenta queda pendiente de ADR-M5-01). Suite 232 tests, 0 fallos; merge FF a `main`._
- [x] **Chunk M5-D** — Función única de reembolso total + flujo manual de reembolso parcial (T-M5-07, T-M5-08) — _cerrado en `chunk/m5-d`: reembolso total real `ReembolsoProveedorMercadoPago` (`POST /v1/payments/{id}/refunds` con body vacío, FR-PAG-009) como ÚNICA vía; parcial manual aislado en `ReembolsoParcialProveedor` fail-closed hasta que M8 lo reemplace (T-M5-08). Además de T-M5-07/08 entran los dos diferidos de M5-B: `reserva.cancelada` (asimetría FR-RES-008/016: ≥24hs o cancela el Tutor → reembolso; <24hs y cancela quien pagó → liberación al Tutor) y reembolso de pagos tardíos del webhook (Reserva fuera de `pendiente_pago` → reembolso + fila ancla idempotente). Suite 240 tests, 0 fallos; merge FF a `main`._
- [x] **Chunk M5-E** — Precio de referencia regional (T-M5-09) — _cerrado en `chunk/m5-e`: `GET /api/pagos/precio-referencia/{provincia}` resume la versión vigente (mayor `version`) de la tabla acumulativa `precios_referencia_regional`; la respuesta es un snapshot que se copia al perfil del Tutor sin FK (FR-PAG-005/006). Pasa 404 si la provincia no tiene fila (sugerencia no vinculante). Suite 245 tests, 0 fallos._
- [x] **Chunk M5-F** — Tests (T-M5-10) — _cerrado en `chunk/m5-f`: reembolso automático probado E2E contra la cadena real (stub HTTP local): total, body vacío (FR-PAG-009), monto íntegro, idempotente — nunca un parcial; y liberación pausada por denuncia con ventana ya vencida que no libera fondos ni con disparo vencido real de Quartz, hasta la resolución (diferida a M9-D). Suite 248 tests, 0 fallos._

## M3 — Aula Virtual

_(Chunks A/B no dependen del spike; Chunk C sí — no arrancar M3-C hasta que SPIKE-C esté cerrado)_

- [x] **Chunk M3-A** — Migración + integración LiveKit (T-M3-01, T-M3-02)
- [x] **Chunk M3-B** — Jobs de sala a T-5, no-show a T+10, finalización (T-M3-03, T-M3-04, T-M3-05) — _cerrado: SesionService + 3 jobs de Quartz + endpoint finalizar + eventos `sesion.no_show_*/finalizada`; suite completa 157 tests OK._
  _Auditoría 2026-09-21: el webhook de LiveKit solo procesa `participant_joined`; `participant_left` y `room_finished` no se manejan, así que US-5/US-8 (corte por desconexión) no tienen implementación server-side — AUD-029._ **CERRADO 2026-09-24 (1fa9772, FASE2-05):** webhook procesa `participant_left`/`room_finished`; el corte automático mide la duración contra `par_roto_at` (desconexión real) y el estado de un corte por desconexión previa al fin agendado es `finalizada_anticipada` — sin cierre inmediato al salir ambos (un microcorte de red no termina la clase).
- [~] **Chunk M3-C** — Clasificador on-device + endpoint de killswitch, rama decidida en backend (T-M3-06, T-M3-07) — _PARCIAL: T-M3-07 (backend, `SesionService.ejecutarKillswitch`, rama decidida server-side) cerrado y testeado. **T-M3-06 (clasificador NSFW on-device en el cliente) sigue sin implementar** — cero NSFWJS/TensorFlow.js en `frontend/`. Bloqueante de seguridad real, no cosmético: sin esto el backend nunca recibe el disparo del kill-switch en una sesión real._
  _Auditoría 2026-09-21: **T-M3-07 tampoco está completo.** El backend decide la rama correctamente (eso sí está y está testeado), pero `SesionService.cortar()` sólo cambiaba estado en BD y nunca le decía nada a LiveKit (AUD-001) — **cerrado 2026-09-22 (c650018, ADR-M3-03):** el corte hace `DeleteRoom` y `/token` responde 422 sobre sesiones cortadas. Además, `POST /api/sesiones/{id}/killswitch` sólo exige ser participante de la Reserva: cualquiera de los tres actores puede dispararlo contra otro sin evidencia previa, sin límite de tasa y con efecto monetario inmediato (reembolso total vía `EscrowService`) — la evidencia (`subirEvidencia`) es opcional y posterior al corte (AUD-005)._
- [x] **Chunk M3-D** — Evidencia de 30s + confirmación de la rama "adultos" (T-M3-08, T-M3-09) — _cerrado: `subirEvidencia` (Artículo V, solo referencia) + `confirmarRamaAdultos`._
- [x] **Chunk M3-E** — Emisión de todos los eventos hacia M5 (T-M3-10) → _`sesion.finalizada/interrumpida/no_show_*/killswitch_*` cerrados; M5-B los consume._
- [x] **Chunk M3-F** — Test: manipulación de cliente no puede forzar la rama "adultos" con un menor presente (T-M3-11) — _`KillswitchIntegracionTest`, ejercita el ataque explícito del enunciado._

## M9 — Denuncias, Seguridad y Moderación

_(requiere M1, M3, M5 cerrados)_

- [x] **Chunk M9-A** — Migración (T-M9-01)
- [x] **Chunk M9-B** — Endpoint de denuncia con rechazo 403 a menores + jobs de plazos (48hs/5 días hábiles, track separado del kill-switch) (T-M9-02, T-M9-03)
- [x] **Chunk M9-C** — Resolución de Denuncia estándar + resolución de Alerta de kill-switch (T-M9-04, T-M9-05)
- [x] **Chunk M9-D** — Propagación de sanción a M1/M2/M4/M5 con reintentos ante fallo (T-M9-06)
- [x] **Chunk M9-E** — Tests: denuncias cruzadas, propagación de sanción a los 4 módulos (T-M9-07) — _suite completa verde, confirmado en T-FIN-02._

## M6 — Resumen Automático de Sesiones

_(requiere M3 cerrado)_

- [x] **Chunk M6-A** — Migración + listener de `sesion.finalizada` con validación de duración (T-M6-01, T-M6-02)
- [x] **Chunk M6-B** — Verificación de Denuncia/Alerta activa antes de generar (T-M6-03)
- [x] **Chunk M6-C** — Módulo de anonimización, aislado y testeado antes de conectar al pipeline (T-M6-04)
- [~] **Chunk M6-D** — Integración LLM + reintentos con backoff (reutilizar patrón de M5) (T-M6-05, T-M6-06) — _código completo detrás de un puerto `ResumenProveedor` fail-closed; **el ADR de proveedor (GPT-4o vs. Gemini 2.0 Flash) sigue pendiente**, así que hoy no genera un resumen real, solo falla cerrado de forma segura. No bloquea el resto del sistema (T-FIN-03 lo confirma explícitamente)._
  _Auditoría 2026-09-21: además del ADR del proveedor, **falta el transcript**: `TranscriptSesionProveedorNoDisponible` devuelve `null` siempre y M3 no genera transcript (sin LiveKit Egress). Elegir proveedor de LLM no desbloquea M6 por sí solo — AUD-024._
- [x] **Chunk M6-E** — Recordatorio único a 24hs (T-M6-07)
- [x] **Chunk M6-F** — Test de anonimización con datos reales de prueba (T-M6-08)

## M7 — Sistema de Calificaciones y Reputación

_(requiere M3 cerrado; retroalimenta a M2-C y M4-E, que hasta acá tenían stubs)_

- [x] **Chunk M7-A** — Migración (T-M7-01)
- [x] **Chunk M7-B** — Endpoint de calificación + perfil público con umbral de 5 (T-M7-02, T-M7-03)
- [x] **Chunk M7-C** — Endpoint interno de calificación oculta (solo rol Moderación) + bloqueo de nueva reserva (T-M7-04, T-M7-05) → _reemplazó el stub del Chunk M4-E._
- [x] **Chunk M7-D** — Recordatorio/edición de calificación + señales implícitas incrementales (T-M7-06, T-M7-07) → _reemplazó el stub del Chunk M2-C._
- [x] **Chunk M7-E** — Test: ningún endpoint público filtra `tutor_a_estudiante` (T-M7-08)

## M8 — Panel de Administración

_(requiere M1, M9, M5 cerrados — es la interfaz sobre reglas ya definidas, no define nada nuevo)_

- [x] **Chunk M8-A** — Migración + interceptor de auditoría común, ANTES que cualquier endpoint (T-M8-01, T-M8-02)
- [x] **Chunk M8-B** — Endpoints de colas + ordenamiento por plazo restante (T-M8-03, T-M8-04)
- [x] **Chunk M8-C** — Mapeo de enrutamiento de tickets de soporte (T-M8-05)
- [x] **Chunk M8-D** — Test de aislamiento de roles (403 cruzado) (T-M8-06)
- [x] **Chunk M8-E** _(agregado, fuera del plan original)_ — Resolución de credencial desde la cola (T-M8-07) + storage real local-fs (T-M8-08).
  _Auditoría 2026-09-21: la resolución de credencial desde la cola está, pero **no existe endpoint para ver el archivo** que se está aprobando — `CredencialColaResponse` excluye `archivoUrl` a propósito y ningún controller sirve el archivo; el Admin aprobaba o rechazaba conociendo sólo nombre, apellido y tipo de documento — AUD-007. **Cerrado 2026-09-22:** `GET /api/admin/moderacion/credenciales/{id}/archivo` + visor en la cola._
- [x] **Chunk M8-F** _(agregado, PR #19, ADR-M5-01/M8-01)_ — Modo Bypass de la pasarela de pagos + pestaña "Salud de Infraestructura" con datos reales, formalizados retroactivamente vía ADR.

---

## Cierre — Integración Transversal

- [x] **Chunk FIN-A** — E2E flujo feliz completo, sin mocks entre módulos propios (T-FIN-01) — `E2EFlujoFelizIntegracionTest`.
- [x] **Chunk FIN-B** — E2E rama de seguridad completa (T-FIN-02) — `E2ERamaSeguridadIntegracionTest`; suite completa 383 tests (verificado 2026-09-21, JDK 21 + Testcontainers), 0 errores.
- [x] **Chunk FIN-C** — Revisión de que ningún ADR quedó pendiente (T-FIN-03) — _M1/M2/M3 resueltos. M5 (productivo real de MercadoPago) y M6 (LLM) quedan pendientes de forma explícita y no bloqueante. **Auditoría 2026-09-18 agrega uno que T-FIN-03 no contempló: T-M3-06 (integración cliente del kill-switch) sigue sin resolver — a diferencia de M5/M6, este si es bloqueante de seguridad antes de producción real con menores.**_

---

## TESIS — Decisiones de la tesis (specs en `docs/superpowers/specs/tesis/`)

> Piloto el 27/10/2026. Orden y dependencias en `docs/superpowers/specs/tesis/00-LEEME-tesis.md`.

- [x] **Chunk TESIS-CAP-A** — ADR-M1-04 + enmienda Constitución v2.3 — el CAP vuelve acotado a Tutores de Menores (T-TES-01, spec `tesis/T01-cap-adr-enmienda.md`, DT6, _branch `tesis/cap-menores`_). Solo documentación; T-AUD-032 CANCELADA, AUD-035 `EN CURSO`.
- [ ] **Chunk TESIS-CAP-B** — CAP backend (T-TES-02, spec `tesis/T02-cap-backend.md`) — _branch `tesis/cap-menores`. Riesgo alto (seguridad del menor). Depends on T-TES-01._
- [ ] **Chunk TESIS-CAP-C** — CAP frontend + moderación (T-TES-03, spec `tesis/T03-cap-frontend-moderacion.md`) — _Depends on T-TES-02._
- [ ] T-TES-04 — firma digital del CAP (spec `tesis/T04-cap-firma-digital.md`): **OPCIONAL / no ejecutar** (PT2, manual en el MVP). Depends on T-TES-02.
- [x] **Chunk TESIS-A** — Comisión de plataforma al 27 % (T-TES-05, spec `tesis/T05-comision-27.md`, DT1) — _branch `tesis/comision-27`. Riesgo medio (dinero). Sin dependencias. Cerrado 2026-09-23: 27 % en `application.yml` y default de `ComisionPlataforma`, javadocs y Spec_M5 alineados (historial 27 %, antes 15 %); `ComisionPlataformaTest` nuevo (RED 2250 → GREEN 4050) y 4 asserts de integración a 4050; suite 427 run/0 fail._
- [x] **Chunk TESIS-B** — Gate de menores del piloto (T-TES-10, spec `tesis/T10-gate-menores-piloto.md`, DT7) — _branch `tesis/gate-menores-piloto`. Riesgo bajo (corte fail-closed). Sin dependencias. Flag solo en true con T-M3-06 y T02 cerradas. Cerrado 2026-09-23: RED `expected:<409> but was:<201>` → GREEN; suite 432 run/0 fail (427 previos + 5): `GateMenoresPilotoIntegracionTest` (4) + `crearReserva_beneficiarioMenor_conFlagTrue_ok`._
- [x] **Chunk TESIS-L** — Recomendaciones por resultado, sin rótulo "IA" (T-TES-12, spec `tesis/T12-ux-recomendaciones.md`) — _branch `tesis/ux-recomendaciones`. Riesgo bajo. Sin dependencias. Cerrado 2026-09-24: "Búsqueda inteligente con IA" → "Tutores recomendados para lo que necesitás" (`buscar/page.tsx`) y "resultados de matching" → "resultados de búsqueda" (`tutores/[id]/page.tsx`); E2E `BUSCAR-E2E-001` actualizado. PARAR §2.2: no se agrega la línea de "por qué" por resultado — el backend no devuelve materia/nivel que coincidan. Solo frontend: `bun run lint` + `npx tsc --noEmit -p .` en verde (sin suite de Maven)._


## UX — Rediseño UX/UI (specs en `docs/superpowers/specs/ux/`)

- [x] **Chunk UX-01..08 + U1** — _branch `claude/lucid-lovelace-htlv4z` (un commit por spec). Cerrado 2026-09-24: sistema visual, zona pública, embudo, cuenta, tutor, aula y admin; U1 (bio/foto) con V28 y Spec_M1 US-7; `ReservaResponse` con acciones del servidor. Suite 467 run/0 fail; E2E 76/76. Lo que depende de FASE2-01, FASE2-03, FASE3-03 o de endpoints que no existen quedó listado en `Tasks_Tinku_Implementacion.md` (sección UX)._

---

## FASE 2 — Remediación de auditoría (sessiones/branches por spec)

- [x] **Chunk FASE2-01** — Disponibilidad en bloques de 30 min y tarifa por hora (AUD-009 + AUD-020, T-AUD-012, D6) — _branch `claude/lucid-lovelace-htlv4z`. Cerrado 2026-09-24: V29 (duración + fin, backfill), V30 (EXCLUDE por `tstzrange`), V31 (`precio_hora`); `franjaQueContiene`, precio por minuto, M3 con la duración de la Reserva, horarios cada 30; `/reservar` con selector de duración y precio en vivo. Suite 492/0; E2E 76/76._
- [x] **Chunk FASE2-07** — Modo Bypass solo fuera de `prod` (AUD-018, T-AUD-018, P2 opción a) — _branch `claude/lucid-lovelace-htlv4z`. Cerrado 2026-09-24: 409 en prod, `bypassPermitido`, log WARN, banner en `/admin`; ADR-M5-01 con subsección de actualización._
- [x] **Chunk FASE2-02** — Rate limiting, bloqueo de login y política de contraseña (AUD-012, T-AUD-013, P4) — _branch `claude/lucid-lovelace-htlv4z` (la sesión no podía crear `aud/*`). Cerrado 2026-09-24: filtro por IP en memoria (D8), bloqueo por DNI, `@PasswordSegura`; filas nuevas en la Tabla de Tiempos; ADR-000-04 actualizado._
- [x] **Chunk FASE2-06** — Baja de menor por anonimización, no DELETE (AUD-017, T-AUD-016, spec `superpowers/specs/remediacion/FASE2-06-baja-menor-anonimizacion.md`) — _branch `aud/fase2-baja-menor`. Riesgo medio (datos de menores, Ley 25.326). Cerrado 2026-09-24: ADR-M1-05 (decisión D7), migración V27 (`estado_cuenta` admite `BAJA`, se dropea el CHECK anónimo de V2), `darDeBajaMenor` anonimiza (dni determinístico `BAJA-` + 14 chars del UUID, nombre/apellido/email/fecha/password reemplazados, `activo_para_matching` false) y `listarMenores` excluye `BAJA`. RED: 500 por FK `reservas_beneficiario_id_fkey` → GREEN: suite 444 run/0 fail (440 previos + 4 de `BajaMenorAnonimizacionIntegracionTest`). PARAR reportado: no se cancelan las reservas futuras confirmadas — no existe método de cancelación por sistema que cubra el rol `beneficiario` del menor (ver spec §4)._
  - [x] **FASE2-06b** — PARAR resuelto por el usuario (opción a) — _branch `aud/fase2-06b-cancelacion-baja-menor`. Puerto `CancelacionReservasFuturas` (M1→M4): la baja confirmada cancela las reservas futuras del menor en nombre del AR, motivo `voluntaria` (M5 aplica FR-RES-008; con <24 hs cobra el Tutor), y rechaza sus Solicitudes pendientes (si no, el AR podía aprobarlas después para un perfil dado de baja). RED: `expected: CANCELADA but was: CONFIRMADA/PENDIENTE_PAGO`, `expected: RECHAZADA but was: PENDIENTE` → GREEN: suite 455 run/0 fail._

---

## Nota de auditoría — 2026-09-18

Este archivo y `README.md` estaban desactualizados desde ~72 commits atrás (última edición real
10-sep, mientras `main` siguió recibiendo M6/M7/M8/M9 completos, M2-F, y features de admin/landing
hasta 18-sep). `docs/Tasks_Tinku_Implementacion.md` es la fuente de verdad atómica; este archivo se
corrigió contra ese documento y contra el código real en `backend/src/main/java/com/tinku/`. Único
pendiente real de dominio: **T-M3-06** (ver Chunk M3-C). Todo lo demás marcado `[ ]` en este
archivo antes de esta revisión era un falso negativo de tracking, no trabajo faltante.

---

## Resumen de dependencias cruzadas a vigilar

| Chunk con stub                        | Se completa en                                     | Motivo                                               |
| ------------------------------------- | -------------------------------------------------- | ---------------------------------------------------- |
| M2-C (reputación/suspensión)          | M7-D, M9 ✅                                        | Cerrado — reemplazado por consulta real a M7/M9. |
| M4-E (sanción/calificación pendiente) | M9-D, M7-C ✅                                      | Cerrado — reemplazado por consulta real a M9/M7. |
| M5-B (listeners de eventos de sesión) | M3-E, M9-D ✅                                      | Cerrado — M3-E/M9-D publican las clases definidas por M5 en `chunk/m5-b` (`sesion.*`, `denuncia.registrada`, `denuncia.resuelta` vía M5-C/D). |
| M5-B (webhook MP real + confirmación) | M5-B ✅                                           | El webhook real con validación de firma reemplazó a `confirmar-pago-simulado` (stub M3-B dev/test) |
| M3-C (endpoint backend del killswitch)| M3-C ✅ (T-M3-07)                                  | Rama decidida en backend, cerrado y testeado (`KillswitchIntegracionTest`). |
| M6-D (proveedor LLM real)             | Pendiente — requiere ADR (GPT-4o vs. Gemini)       | Código detrás de puerto fail-closed; no bloqueante para el resto del sistema (T-FIN-03). |

**Único stub real que sigue sin reemplazar, y SÍ es bloqueante — distinto a los de arriba, no es
un stub de integración entre módulos sino la mitad cliente de un control de seguridad:**

| Pendiente                                              | Motivo                                                                                     |
| -------------------------------------------------------| -------------------------------------------------------------------------------------------|
| **T-M3-06** — clasificador NSFW on-device en el cliente | El backend del killswitch (T-M3-07 a T-M3-11) está completo y testeado, pero nunca recibe un disparo real en producción porque el frontend no corre ningún clasificador sobre los frames de video. Bloqueante de seguridad (Artículo II/XI de la Constitución) antes de cualquier sesión real con un menor presente. |

_Cualquier stub que quede sin reemplazar al llegar a Chunk FIN-A debe tratarse como bloqueante — no cerrar el flujo feliz E2E con un mock permanente disfrazado de stub temporal._

---

## Nota de auditoría — 2026-09-21

Auditoría técnica independiente (segunda opinión, fuera del ciclo de desarrollo asistido).
Informe: `docs/auditoria/2026-09-21-auditoria-independiente.md` — 36 findings.
Registro de estado: `docs/auditoria/REGISTRO_FINDINGS.md`.
Plan de remediación: `docs/superpowers/plans/2026-09-21-remediacion-auditoria.md`.

La nota de auditoría del 2026-09-18 afirmaba que "todo lo demás marcado `[ ]` era un falso
negativo de tracking, no trabajo faltante". Esta auditoría corrige esa conclusión: hay 7
findings CRÍTICOS de seguridad y 13 de integridad que no estaban registrados en ningún lado,
y la suite verde (383 tests) no los detecta porque ninguno de ellos es expresable como
"request → estado en BD".

**Verificación de la suite al 2026-09-21:** 383 tests, 0 failures, 0 errors, 0 skipped.

**Nota de auditoría — 2026-09-24 (FASE2-04, matching auth pool):** cierre de AUD-015
(autenticación + exposición del `matching-service` + recompute en una transacción) y del
sub-ítem 7 de AUD-036 (lock de la carga lazy del embedder). Detalle y tests de regresión en
`REGISTRO_FINDINGS.md`. Suite backend al 2026-09-24: **442 tests, 0 failures, 0 errors,
0 skipped**; suite del `matching-service`: 18 tests verdes.
