# Spec: M3 — Aula Virtual

**Módulo:** M3 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M1 (Identidad, Autorización), M4 (Reserva confirmada, horario agendado, habilitación de sala a T-5)
**Dispara eventos consumidos por:** M5 (finalización, no-show, corte <50%), M9 (Alerta de Seguridad del kill-switch), M7 (señales de puntualidad)

---

## 1. Resumen

Este módulo gobierna el ciclo de vida de la Sesión de Aprendizaje: creación de la sala de videollamada (LiveKit), asistencia (no-show), degradación por conectividad, corte del kill-switch, y finalización formal de la sesión — que es el evento que habilita la liberación de fondos en M5. La evidencia y resolución del kill-switch viven en M9; aquí vive solo la detección y el corte.

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Entrar a la sesión
*Como* Estudiante o Tutor, *quiero* entrar a la sala en el momento justo, *para* no esperar ni entrar a una sala vacía.

- **Dado** que falten 5 minutos para el horario agendado, **cuando** ese momento llegue, **entonces** la sala de LiveKit se crea y el botón de unirse se habilita para ambas partes (coherente con FR-RES-010 de M4).
- **Dado** que una de las dos partes se una, **cuando** la otra aún no llegó, **entonces** ve claramente que está esperando (indicador de sala de espera) — sin exponer datos de contacto.

### US-2 — No-show automático
*Como* Tinku, *quiero* detectar automáticamente la inasistencia, *para* no depender de que alguien la marque manualmente.

- **Dado** que pasaron 10 minutos desde el inicio agendado (T+10) y ninguno de los dos se unió, **cuando** el timeout se ejecute, **entonces** la reserva pasa a `no_show_doble` (FR-RES-009 de M4) y se notifica a ambas partes.
- **Dado** que a T+10 solo se unió una parte, **cuando** el timeout se ejecute, **entonces** la reserva pasa a `no_show_estudiante` o `no_show_tutor` según quién esté ausente, y M5 aplica la asimetría correspondiente (cobrar o reembolsar, FR-RES-005 de M4).
- **Dado** que la parte ausente se une tarde (entre T+0 y T+10), **cuando** eso ocurra, **entonces** el timeout de no-show se cancela y la sesión continúa con normalidad.

### US-3 — No-show manual
*Como* participante presente, *quiero* poder marcar que la otra parte no está, *para* no esperar de más si algo falló con la detección automática.

- **Dado** que estoy solo en la sala, **cuando** marque manualmente la ausencia del otro, **entonces** el sistema permite el marcado a partir de T+10, y el resultado es equivalente al del timeout automático.
- **Dado** que un no-show ya fue ejecutado (automático o manual), **cuando** alguien intente revertirlo desde la app, **entonces** no puede — la revisión es un caso de soporte del Admin (FR-AULA-006).

### US-4 — Degradación por conectividad
*Como* participante con mala conexión, *quiero* que la calidad se ajuste sola, *para* seguir la clase aunque mi red baje.

- **Dado** que mi conexión empeore, **cuando** el sistema lo detecte, **entonces** degrada automáticamente video → audio → texto, sin intervención manual, y sin un "modo degradado" visible (FR-AULA-002, BR-CONN-01, NFR-REND-03 de la Constitución).
- **Dado** que la conexión mejore, **cuando** el sistema lo detecte, **entonces** restituye la mejor calidad posible automáticamente. La degradación nunca afecta el cobro (BR-CONN-01).

### US-5 — Corte total antes del 50%
*Como* Estudiante, *quiero* recuperar mi dinero si la clase no pudo darse, *para* no pagar por una sesión que no ocurrió.

- **Dado** que la sesión se corte por completo (ambas partes desconectadas, sin reconexión) antes de cumplirse el 50% de la duración agendada, **cuando** el sistema lo determine, **entonces** emite el evento `sesion.interrumpida` y M5 reembolsa al Estudiante (FR-AULA-005, BR-CONN-02).

  **Implementado (AUD-029, 2026-09-24, FASE2-05):** el webhook de LiveKit procesa
  `participant_left`/`room_finished` (flags `tutor_conectado`/`estudiante_conectado` +
  `par_roto_at`). El corte automático a fin agendado + 5 min mide la duración efectiva contra
  `par_roto_at` (última desconexión del par, si nadie la recompone), no contra `Instant.now()`
  del job. **No hay cierre inmediato al salir ambos**: un microcorte de red de los dos no
  termina la clase (Spec FASE2-05 §2); el hecho queda registrado y lo decide el corte ya
  existente. El estado final de un corte por desconexión previo al fin agendado es
  `finalizada_anticipada` (US-8 caso borde #6).
- **Dado** que el corte ocurra después del 50%, **cuando** eso ocurra, **entonces** la sesión emite el mismo evento `sesion.finalizada` que cualquier cierre normal (no un evento especial) — así M5 libera los fondos con normalidad, M6 genera el resumen si hubo ≥10 min efectivos, y M7 habilita la calificación, sin reglas separadas para este caso (resuelve E-09 del informe de QA).

### US-6 — Kill-switch, contraparte menor (rama 1)
*Como* Tinku, *quiero* cortar la sesión ante contenido inapropiado cuando hay un menor presente, *para* protegerlo de forma inmediata.

- **Dado** que hay un perfil de menor en la sesión y el clasificador on-device detecta contenido inapropiado/ilegal en el video del Tutor, **cuando** la detección se confirme, **entonces** la sesión se corta para ambos, el buffer de 30s se sube y persiste como Alerta de Seguridad (BR-KS-01), el Tutor queda en suspensión preventiva, se emite el evento `sesion.killswitch_menor`, con el que M5 pausa el escrow hasta que M9 resuelva la Alerta y recién ahí reembolsa al Estudiante (FR-PAG-009, ADR-M3-02: el corte es inmediato, la plata no), y se notifica inmediatamente al Adulto Responsable. La Alerta pasa a M9 (ventana de 12hs → revisión del Admin).

  **Implementado (AUD-001, 2026-09-22):** el corte cierra la sala de LiveKit (`DeleteRoom`, que
  desconecta a ambos) y la sesión deja de emitir tokens nuevos. Si LiveKit no contesta, el corte
  se persiste igual y el cierre se reintenta con un job persistido — ver ADR-M3-03 para la
  decisión fail-open y sus riesgos aceptados. Los tokens ya emitidos no se pueden revocar en
  LiveKit; valen hasta su TTL contra una sala ya cerrada.

  **NO IMPLEMENTADO (AUD-014, 2026-09-21):** no existe infraestructura de notificación en el
  sistema. El Adulto Responsable no recibe ningún aviso. Ver FASE 2.

  **NO IMPLEMENTADO (AUD-014/T-M3-06, 2026-09-21):** el endpoint `POST /api/sesiones/{id}/evidencia`
  existe pero ningún cliente lo llama — no hay MediaRecorder en `frontend/`.
- **Dado** que el participante menor es quien genera la detección, **cuando** eso ocurra, **entonces** se aplica el mismo corte y se notifica al Adulto Responsable — la sesión nunca continúa, sin importar quién disparó la detección (Artículo II).

  **A quién se suspende (decisión D2, AUD-006, 2026-09-22):** el corte es incondicional, pero la
  suspensión preventiva recae **solo sobre el detectado** (el usuario cuyo video disparó la
  detección), igual que en la rama adultos (US-7). La Alerta apunta siempre a esa persona, y
  resolverla en M9 revierte exactamente esa suspensión.
  **Riesgo aceptado:** si el detectado es el menor, el Tutor no queda suspendido y puede tomar
  otra sesión durante la ventana de 12hs hasta la revisión del Admin. Se acepta para no
  penalizar a un Tutor por una detección que no generó; la revisión humana ocurre igual porque la
  Alerta se crea siempre y entra a la cola de moderación.

### US-6bis — Falla técnica del propio clasificador _(agregado, auditoría 2026-09-18)_
*Como* Tinku, *quiero* que una falla del clasificador (no un falso negativo de contenido, sino que el modelo no cargue o deje de responder) tenga un comportamiento explícito y a favor de la seguridad, *para* que nunca haya una sesión con un menor corriendo sin protección activa sin que nadie lo sepa.

- **Dado** que el clasificador on-device no logre cargarse en el cliente (modelo no descarga, dispositivo sin soporte, error de runtime) **y** haya un perfil de menor en la sesión, **cuando** eso se detecte antes de habilitar el botón de unirse, **entonces** la sala no se habilita para ese participante y se informa un error claro — nunca se entra a una sesión con un menor sin el clasificador corriendo (fail-closed, Artículo II, mismo criterio que "ningún flujo con un menor depende de una confirmación que puede no llegar").
- **Dado** que el clasificador deje de responder ya iniciada la sesión (crash del modelo, no un simple frame sin detección), **cuando** eso ocurra con un menor presente, **entonces** se trata como corte por conectividad del lado de quien perdió el clasificador (misma rama que US-4/US-5) — nunca se continúa la sesión con el menor sin monitoreo activo sabiendo que se perdió.
- **Dado** que ambos participantes sean adultos y el clasificador falle en cargar, **cuando** eso ocurra, **entonces** la sesión puede continuar sin bloquear el ingreso (el Artículo II no aplica sin un menor presente) — el evento queda logueado para monitoreo de infraestructura, pero no es un caso de seguridad de la Constitución.

### US-7 — Kill-switch, ambos adultos (rama 2)
*Como* Tinku, *quiero* una respuesta proporcionada cuando los dos participantes son adultos, *para* no cortar sesiones legítimas por falsos positivos.

- **Dado** que ambos participantes son adultos y el clasificador detecta contenido en el video de uno, **cuando** la detección se confirme, **entonces** el video del detectado se bluerea (placeholder estático) y la sesión continúa; se pregunta al otro participante si vio algo incorrecto o ilegal.
- **Dado** que el otro participante responde "No", **cuando** eso ocurra, **entonces** se restaura la cámara y continúa el monitoreo normal (el evento queda logueado internamente).
- **Dado** que el otro participante responde "Sí", **cuando** eso ocurra, **entonces** se corta la sesión, se bloquea al detectado, M5 reembolsa al Estudiante, y se genera la Alerta de Seguridad para M9.

### US-8 — Finalización de la sesión
*Como* Estudiante o Tutor, *quiero* que la sesión termine de forma clara, *para* saber cuándo empiezan a correr los plazos de calificación y de liberación de fondos.

- **Dado** que cualquiera de las dos partes presione "Finalizar", **cuando** eso ocurra, **entonces** la sesión se marca `finalizada` para ambas y se emite `sesion.finalizada` (evento que M5 usa para iniciar la cuenta de 24hs de liberación del escrow, FR-PAG-002).
- **Dado** que nadie finalice manualmente, **cuando** se cumpla el fin del horario agendado más una tolerancia de gracia de 5 minutos, **entonces** el sistema corta la sala y marca la sesión `finalizada` automáticamente, emitiendo el mismo evento.
- **Dado** que un participante corte antes del fin agendado sin presionar "Finalizar", **cuando** eso ocurra, **entonces** la sesión queda `finalizada_anticipada` cuando la otra parte también salga o al agotarse la tolerancia; ese outcome alimenta la regla de corte <50% de US-5.

  **Implementado (AUD-029, 2026-09-24, FASE2-05):** el webhook procesa `participant_left`/
  `room_finished` (`par_roto_at` = instante en que salió el último: si uno se va y el otro se queda, no hay corte — US-5) y el corte
  automático deja el estado `finalizada_anticipada` cuando `par_roto_at` es anterior al fin
  agendado — y la duración efectiva se mide contra ese instante. Cualquier reconexión
  limpia `par_roto_at`: el microcorte de ambos no cuenta como corte.

## 3. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-AULA-001 | Sala de videollamada LiveKit creada a T-5 minutos (coherente con FR-RES-010 de M4). Sesiones siempre 1 a 1 en el MVP. |
| FR-AULA-002 | Degradación automática y continua de calidad (video → audio → texto), sin "modo degradado" visible. |
| FR-AULA-003 | Clasificador de contenido en tiempo real (kill-switch), on-device, según BR-KS-01 a BR-KS-06. Sin botón de pánico manual en el MVP — solo detección automática. |
| FR-AULA-004 | Buffer local rotativo de 30 segundos de video, no persistente; se sube solo si el kill-switch se dispara. |
| FR-AULA-005 | Corte total antes del 50% de la duración agendada → evento `sesion.interrumpida` → reembolso automático (vía M5). |
| FR-AULA-006 | No-show: timeout automático a T+10 + marcado manual (habilitado también desde T+10). No reversible desde la app; revisión es caso de soporte del Admin. |
| FR-AULA-007 | Finalización manual o corte automático a fin de horario + 5 min de tolerancia. Ambos caminos emiten `sesion.finalizada`. |
| FR-AULA-008 | Llegada tardía entre T+0 y T+10 cancela el timeout de no-show. |
| FR-AULA-009 | El kill-switch emite `sesion.killswitch_menor` o `sesion.killswitch_adultos` según la rama, eventos que M5 consume para reembolsar (ver tabla de eventos de M5). |
| FR-AULA-010 _(agregado, auditoría 2026-09-18)_ | Falla técnica del clasificador (no detección, sino modelo caído): con un menor presente, fail-closed — no se habilita la sala si no carga, se trata como corte por conectividad si falla ya iniciada la sesión. Sin menor presente, no bloquea (US-6bis). **NO IMPLEMENTADO (AUD-001, 2026-09-21):** `SesionService.obtenerToken()` no consulta ningún estado del clasificador; la sala se habilita siempre. |

## 4. Reglas de Negocio Aplicadas (referencia)

- BR-KS-01 a BR-KS-06: buffer, retención de evidencia, no-generación de Denuncia automática, sin calibración de falsos positivos en MVP, acceso restringido al clip, aceptación del Tutor en onboarding.
- BR-CONN-01: la degradación no afecta el cobro. BR-CONN-02: <50% → reembolso.
- FR-RES-005/009 de M4: la asimetría del no-show se dispara desde este módulo.

## 5. Casos Borde — Todos Resueltos

| # | Caso / Pregunta | Resolución |
|---|---|---|
| 1 | Marcado manual de no-show llega después del timeout automático | El manual no puede revertir un no-show ya ejecutado; solo puede anticiparlo. Reversión = caso de soporte del Admin (FR-AULA-006). |
| 2 | Llegada tardía antes de T+10 | Cancela el timeout; la sesión continúa con normalidad (FR-AULA-008). |
| 3 | Ambos ausentes a T+10 | `no_show_doble`: reembolso completo al Estudiante, sin pago al Tutor (FR-RES-009 de M4). |
| 4 | Kill-switch dispara con menor presente, sin importar quién generó la detección | Corte total en todos los casos; la rama de blur aplica solo cuando ambos son adultos (Artículo II). |
| 5 | Nadie presiona "Finalizar" | Corte automático a fin de horario + 5 min de tolerancia; emite `sesion.finalizada` igual (FR-AULA-007). |
| 6 | Un participante corta y nunca vuelve | `finalizada_anticipada` al agotarse la tolerancia; si ocurrió antes del 50%, aplica reembolso (US-5). |
| 7 | Caída de LiveKit (falla del proveedor, no del usuario) | Se comunica a ambas partes, nunca falla en silencio (NFR-DISP-02). El outcome monetario sigue la regla de corte <50%. |
| 8 _(agregado, auditoría 2026-09-18)_ | El clasificador on-device del kill-switch no carga o deja de responder | Con menor presente: fail-closed, no se habilita la sala o se trata como corte de conectividad si ya estaba en curso (FR-AULA-010). Sin menor: no bloquea, solo se loguea. |

## 6. Fuera de Alcance de este Spec

- La creación de la reserva y del botón de unirse desde la UI (ver Spec de M4).
- El cobro/reembolso en sí (ver Spec de M5) — M3 solo emite los eventos.
- La Alerta de Seguridad como entidad, su evidencia y resolución (ver Spec de M9) — M3 solo la genera.
- Botón de pánico manual del kill-switch (decisión de producto: excluido del MVP).
- Transcripción y resumen (ver Spec de M6).

## 7. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica nueva aparece en este documento (LiveKit y el clasificador on-device ya están decididos en la Constitución).
- [x] Las preguntas abiertas fueron resueltas.
- [x] Revisado contra la Constitución — Artículo II (seguridad del menor: la rama 1 del kill-switch nunca depende de que el menor confirme nada) y Artículo XI (el clasificador on-device es justamente el componente que la Constitución exige prototipar/spikear antes de comprometer fechas del resto del sistema — este Spec no asume que ya funciona, solo define el comportamiento esperado una vez que el spike lo valide).

---

**Estado: APROBADO.**
