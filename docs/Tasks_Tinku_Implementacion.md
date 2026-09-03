# Tasks de Implementación — Tinku

> Basado en los 9 Planes técnicos aprobados. Orden por dependencia real, no por número de módulo. Cada tarea es accionable y verificable — al completarla, algo concreto debería poder probarse (unit test, request real, o ambos).

---

## FASE 0 — Setup General (una sola vez, antes de cualquier módulo)

- [x] T-000-01: Inicializar proyecto Spring Boot (Java), estructura de paquetes por módulo (`identidad`, `matching`, `aula`, `reservas`, `pagos`, `resumen`, `reputacion`, `admin`, `seguridad`) — bounded contexts del Artículo VIII de la Constitución.
- [x] T-000-02: Configurar PostgreSQL con schemas separados por módulo (`identidad.*`, `pagos.*`, etc., Artículo VIII).
- [x] T-000-03: Configurar Quartz con JobStore persistido en la misma base (Artículo IV/X) — probar que un job programado sobrevive a un reinicio del proceso antes de construir nada encima. (Migración Flyway `V3__quartz_tables.sql`, tablas QRTZ_* en schema `public`; config JDBC en `application.yml`; verificado con `QuartzPersistenciaTest` — programar→shutdown→reiniciar y confirmar que el job+trigger persisten y vuelven a ejecutarse — contra PostgreSQL 16 real vía Testcontainers, y con `spring-boot:run` contra la base local.)
- [x] T-000-04: Configurar `ApplicationEventPublisher` (o equivalente) para eventos de dominio en memoria (Artículo IX) — crear un evento de prueba y un listener de prueba para validar el mecanismo antes de usarlo en lógica real. (`EjemploEvent` + `EjemploListener` con `@EventListener`; verificado con `DomainEventExampleTest` usando `@SpyBean` — ejemplifican el patrón `sesion.*`/`denuncia.*` de la sección 4 del AGENTS.md.)
- [ ] T-000-05: Configurar Spring Security + JWT + bcrypt/argon2 para hashing de contraseñas (NFR-SEC-02).
- [ ] T-000-06: Cuenta de desarrollador de LiveKit Cloud + credenciales de sandbox.
- [ ] T-000-07: Cuenta de MercadoPago Developers + usuarios de prueba operando en modo productivo controlado (ADR-M5-01 — **no** el sandbox clásico, por el problema conocido de webhooks).
- [ ] T-000-08: Levantar el proceso Python del Motor de Matching como servicio separado, con un endpoint de salud (`/health`) y comunicación interna verificada desde el backend Java antes de implementar lógica de negocio sobre él.
- [ ] T-000-09: Configurar pipeline de CI mínimo (build + tests) — no bloqueante para arrancar, pero antes de tener 3+ módulos implementados.

## SPIKE PRIORITARIO — arranca en paralelo desde el día 1 (Artículo XI, ADR-M3-01)

- [ ] T-SPIKE-01: Evaluar 2-3 modelos pre-entrenados de clasificación de contenido NSFW ejecutables en el navegador (ej. vía TensorFlow.js) contra un set de imágenes de prueba.
- [ ] T-SPIKE-02: Medir latencia real de inferencia en un dispositivo de gama media (no solo en la laptop de desarrollo) — el spike debe responder si la detección es lo bastante rápida para ser útil en tiempo real.
- [ ] T-SPIKE-03: Prototipar el buffer rotativo de 30s con `MediaRecorder` en el navegador — validar que los chunks concatenados producen un video reproducible (riesgo real de códec mencionado en el Plan de M3).
- [ ] T-SPIKE-04: Documentar el resultado del spike como ADR-M3-01 cerrado, con la decisión final (modelo + framework) — si el resultado es negativo, escalar de inmediato como riesgo de cronograma, no seguir adelante en silencio.

---

## M1 — Gestión de Identidad y Perfiles

- [ ] T-M1-01: Migración: tabla `usuarios` con constraint `UNIQUE` en `dni`.
- [ ] T-M1-02: Migración: tablas `credenciales_academicas`, `autorizaciones_tutor`, `consentimientos_menor`.
- [ ] T-M1-03: Resolver ADR-M1-01 (proveedor de OCR) antes de continuar con T-M1-04.
- [ ] T-M1-04: Integración con el proveedor de OCR elegido — función que recibe una imagen y devuelve nombre/apellido/fecha de nacimiento extraídos.
- [ ] T-M1-05: Endpoint `POST /api/usuarios/registro` — valida coincidencia nombre/apellido/DNI + unicidad de DNI + edad ≥18 (FR-ID-001).
- [ ] T-M1-06: Endpoint `POST /api/usuarios/menores` — mismo flujo de OCR + edad ≥6 + consentimiento obligatorio en la misma transacción (FR-ID-017, BR-CONSENT-01).
- [ ] T-M1-07: Job de backoff de OCR: 3 intentos por ciclo, 24hs de espera (FR-ID-011).
- [ ] T-M1-08: Endpoint `PATCH /api/usuarios/me/capacidades` — activar/desactivar Estudiante/Adulto Responsable, con bloqueo si tiene menores a cargo (FR-ID-015/016).
- [ ] T-M1-09: Endpoint `POST /api/tutores/registro` — mismo flujo de OCR que adulto (FR-ID-007).
- [ ] T-M1-10: Endpoint `POST /api/tutores/credenciales` + job de backoff escalonado (24h→48h→96h, FR-ID-012).
- [ ] T-M1-11: Endpoints de `autorizaciones_tutor` (crear, marcar `no_confiable`) — FR-ID-009.
- [ ] T-M1-12: Endpoint `DELETE /api/usuarios/menores/{id}` con verificación de reservas futuras (FR-ID-014).
- [ ] T-M1-13: Tests: cada Historia de Usuario del Spec de M1 tiene al menos un test de integración que la ejercita de punta a punta (registro rechazado por DNI duplicado, por edad, backoff de credencial, etc.).

## M2 — Motor de Matching Semántico

- [ ] T-M2-01: Migración: tabla `materias_niveles` (catálogo cerrado), cargar niveles educativos oficiales de Argentina.
- [ ] T-M2-02: Migración: `perfiles_tutor_matching`, `busquedas_guardadas`.
- [ ] T-M2-03: Resolver ADR-M2-01 (dónde vive el índice — `pgvector` vs. en memoria del proceso Python) antes de T-M2-04.
- [ ] T-M2-04: Endpoint interno `/match` en el servicio Python — recibe texto + lista acotada de tutor_ids, devuelve ranking por similitud.
- [ ] T-M2-05: Lógica en Java: resolver contexto de autorización (lista del menor, o universo completo) **antes** de llamar al servicio Python (FR-MATCH-004).
- [ ] T-M2-06: Lógica en Java: excluir Tutores con Alerta de Seguridad activa (consulta a M9) antes del cálculo semántico (FR-MATCH-007).
- [ ] T-M2-07: Lógica en Java: reordenamiento final por señales implícitas (consulta a M7) y sombra de BR-MATCH-01 (consulta a M7).
- [ ] T-M2-08: Endpoint `POST /api/busquedas` que orquesta todo lo anterior y marca `no_autorizado: true` en resultados fuera de la lista del menor (FR-MATCH-005).
- [ ] T-M2-09: Endpoints de búsquedas guardadas (crear, listar, re-ejecutar).
- [ ] T-M2-10: Tests: búsqueda de un menor sin autorizados devuelve resultados marcados; búsqueda excluye correctamente a un Tutor suspendido.

## M4 — Sistema de Reservas y Agenda

- [ ] T-M4-01: Migración: `franjas_disponibilidad`, `solicitudes_sesion`, `reservas` — incluir la `EXCLUDE constraint` sobre superposición de horario (tutor y beneficiario) desde el primer momento, no agregarla después.
- [ ] T-M4-02: Endpoint de publicación de franjas de disponibilidad del Tutor.
- [ ] T-M4-03: Endpoint `POST /api/solicitudes` (menor) + job de expiración a 48hs (FR-RES-022).
- [ ] T-M4-04: Endpoint `POST /api/solicitudes/{id}/aprobar` (Adulto Responsable) → crea Reserva.
- [ ] T-M4-05: Endpoint `POST /api/reservas` (directa, Estudiante adulto o Adulto Responsable) — valida franja publicada y ventana mínima de 15min (FR-RES-013).
- [ ] T-M4-06: Job de timeout de `pendiente_pago` a 15min (FR-RES-020), con cancelación explícita del job al confirmarse el pago.
- [ ] T-M4-07: Endpoint de reprogramación — valida ventana de 24hs, conserva precio original, no genera transacción nueva (FR-RES-015).
- [ ] T-M4-08: Endpoint de cancelación — aplica asimetría (quién cancela) y dispara evento hacia M5.
- [ ] T-M4-09: Listener de eventos desde M9 (sanción/revocación) que cancela reservas futuras con `motivo_cancelacion` correcto.
- [ ] T-M4-10: Listener de eventos desde M7 (calificación pendiente) que bloquea nuevas Reservas del Tutor afectado (FR-REP-006, ejecutado acá).
- [ ] T-M4-11: Tests: condición de carrera de dos reservas simultáneas al mismo horario — confirmar que la `EXCLUDE constraint` la resuelve, no solo el código de aplicación.

## M5 — Motor de Pagos

- [ ] T-M5-01: Migración: `transacciones`, `precios_referencia_regional`.
- [ ] T-M5-02: Integración con MercadoPago: generación de preferencia de pago con split (`marketplace_fee` = 15%).
- [ ] T-M5-03: Endpoint `POST /api/webhooks/mercadopago` — **validar firma del webhook antes de procesar cualquier payload.**
- [ ] T-M5-04: Listeners de los eventos de la tabla de eventos entrantes del Plan (`sesion.finalizada`, `sesion.interrumpida`, `sesion.no_show_*`, `sesion.killswitch_*`, `denuncia.*`) — uno por evento, cada uno probado por separado.
- [ ] T-M5-05: Job de liberación automática a `liberar_at`, con chequeo de `estado != pausado_denuncia`.
- [ ] T-M5-06: Job de reintento con backoff (5min/15min/1h) + alerta a Soporte Financiero al agotar los 3 (FR-PAG-007).
- [ ] T-M5-07: **Función única de reembolso total** (body vacío a la API de MP) — todo el código que necesite reembolsar llama a esta función, nunca reimplementa la llamada (punto crítico señalado en el Plan).
- [ ] T-M5-08: Flujo manual de reembolso parcial por disputa (M8) — código explícitamente separado del anterior.
- [ ] T-M5-09: Endpoint de sugerencia de precio de referencia regional — copia el valor, sin FK a la fila de configuración (para que revisiones futuras no afecten retroactivamente).
- [ ] T-M5-10: Tests: reembolso automático nunca deja un monto parcial; liberación pausada por denuncia efectivamente no libera fondos hasta la resolución.

## M3 — Aula Virtual

*(No arranca la implementación completa hasta que T-SPIKE-04 esté resuelto — pero T-M3-01 a T-M3-05 no dependen del resultado del spike y pueden avanzar en paralelo.)*

- [ ] T-M3-01: Migración: `sesiones_aprendizaje`, `alertas_seguridad`.
- [ ] T-M3-02: Integración con LiveKit: creación de sala + generación de tokens.
- [ ] T-M3-03: Job de creación diferida de sala a T-5min (a partir del horario ya confirmado en M4).
- [ ] T-M3-04: Job de no-show a T+10min, con cancelación explícita si ambos se unen antes.
- [ ] T-M3-05: Endpoint `POST /api/sesiones/{id}/finalizar` + job de corte automático a T-fin+5min.
- [ ] T-M3-06 *(depende del spike)*: Integrar el clasificador on-device en el cliente, según el resultado de ADR-M3-01.
- [ ] T-M3-07 *(depende del spike)*: Endpoint `POST /api/sesiones/{id}/killswitch` — el backend decide la rama (menor/adultos) con datos propios de M1, nunca confiando en un flag del cliente.
- [ ] T-M3-08: Endpoint de subida de evidencia (clip de 30s) — solo alcanzable tras un killswitch ya registrado.
- [ ] T-M3-09: Endpoint de confirmación de la rama "adultos" (sí/no del otro participante).
- [ ] T-M3-10: Emisión de todos los eventos de sesión hacia M5 (`sesion.finalizada`, `sesion.interrumpida`, `sesion.no_show_*`, `sesion.killswitch_*`).
- [ ] T-M3-11: Tests: intento de manipular el cliente para forzar la rama "adultos" en una sesión con un menor — debe fallar porque la rama la decide el backend.

## M9 — Denuncias, Seguridad y Moderación

- [ ] T-M9-01: Migración: `denuncias`, `sanciones`.
- [ ] T-M9-02: Endpoint `POST /api/denuncias` — rechazo a nivel de autorización si `denunciante.tipo == menor` (403, no solo oculto en frontend).
- [ ] T-M9-03: Job de vencimiento de descargo (48hs) y de SLA de resolución (5 días hábiles) — **track completamente separado** del de Alertas de kill-switch, sin compartir código de plazos.
- [ ] T-M9-04: Endpoint de resolución de Denuncia (infundada/fundada/escalada) — reanuda escrow **solo de esa sesión puntual** (FR-SEC-011).
- [ ] T-M9-05: Endpoint de resolución de Alerta de Seguridad — separado del anterior, sin esperar descargo previo, dentro de la ventana de 12hs.
- [ ] T-M9-06: Publicación del evento de sanción + listeners en M1, M2, M4, M5 (saga o, como mínimo, reintentos con alerta si algún listener falla — riesgo señalado explícitamente en el Plan).
- [ ] T-M9-07: Tests: denuncias cruzadas resuelven su escrow de forma independiente; sanción a un Tutor se propaga correctamente a los 4 módulos listeners.

## M6 — Resumen Automático de Sesiones

- [ ] T-M6-01: Migración: `resumenes_sesion`, con constraint de unicidad por `sesion_id`.
- [ ] T-M6-02: Listener de `sesion.finalizada` que valida duración ≥10min antes de crear cualquier registro.
- [ ] T-M6-03: Consulta a M9/M3 para verificar Denuncia/Alerta activa antes de generar (`suspendido_seguridad`).
- [ ] T-M6-04: Módulo de anonimización (regex + NER liviano) — **ejecutar y testear como componente aislado antes de conectarlo al pipeline de LLM.**
- [ ] T-M6-05: Integración con el proveedor de LLM elegido (audio directo o transcript, según ADR ya resuelto en la Constitución).
- [ ] T-M6-06: Job de reintento con el mismo patrón de backoff que M5 (reutilizar, no reinventar).
- [ ] T-M6-07: Job de recordatorio único a 24hs.
- [ ] T-M6-08: Tests: verificar que el transcript anonimizado nunca llega con datos personales al proveedor externo (test con datos de prueba que contengan nombres/teléfonos reales de prueba).

## M7 — Sistema de Calificaciones y Reputación

- [ ] T-M7-01: Migración: `calificaciones`, `señales_implicitas_tutor`.
- [ ] T-M7-02: Endpoint de calificación — `direccion` derivada del rol del autor, nunca aceptada como input libre.
- [ ] T-M7-03: Endpoint de perfil público de Tutor — oculta el promedio si `count < 5` (FR-REP-007), **sin exponer nunca `tutor_a_estudiante` en ningún query de este endpoint.**
- [ ] T-M7-04: Endpoint interno de calificaciones ocultas — solo accesible por rol Moderación y Seguridad (M8).
- [ ] T-M7-05: Lógica de bloqueo de nueva Reserva por calificación pendiente (expuesta como servicio interno que M4 consulta, T-M4-10).
- [ ] T-M7-06: Job de recordatorio único a 24hs + validación de ventana de edición de 48hs.
- [ ] T-M7-07: Actualización incremental de `señales_implicitas_tutor` en cada evento relevante (no-show, cancelación, recontratación).
- [ ] T-M7-08: Tests: confirmar que ningún endpoint público, ni siquiera con parámetros manipulados, puede devolver una calificación `tutor_a_estudiante`.

## M8 — Panel de Administración

- [ ] T-M8-01: Migración: `admins`, `tickets_soporte`, `log_auditoria_admin` (permisos de base de datos: sin `UPDATE`/`DELETE` en la tabla de auditoría).
- [ ] T-M8-02: Interceptor/aspecto de auditoría común a todos los controladores del módulo — implementar esto **antes** que los endpoints individuales, para que ninguno se escriba sin auditoría desde el día 1.
- [ ] T-M8-03: Endpoints de las colas (Credenciales, Alertas, Denuncias, Pagos fallidos, Precios), cada uno con validación de rol.
- [ ] T-M8-04: Ordenamiento de cada cola por plazo restante (no por fecha de creación).
- [ ] T-M8-05: Tabla de mapeo `origen_modulo → rol_asignado` para el enrutamiento de tickets.
- [ ] T-M8-06: Tests: un Admin de Soporte Financiero recibe 403 al intentar acceder a un endpoint de Moderación y Seguridad, y viceversa.

---

## Cierre — Integración Transversal Final

- [ ] T-FIN-01: Test end-to-end: alta de Adulto Responsable → alta de menor → autorización de Tutor → Solicitud → Reserva → pago → sesión → resumen → calificación — el flujo feliz completo, sin mocks en los puntos de integración entre módulos propios.
- [ ] T-FIN-02: Test end-to-end de la rama de seguridad: sesión con menor → disparo simulado de kill-switch → suspensión → Alerta en M8 → resolución del Admin → efectos propagados a M1/M2/M4/M5.
- [ ] T-FIN-03: Revisión final de que ningún ADR pendiente (M1, M2, M5, M6) quedó sin resolver antes del cierre del piloto.
