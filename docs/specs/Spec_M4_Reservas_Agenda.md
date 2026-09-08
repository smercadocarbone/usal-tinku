# Spec: M4 — Sistema de Reservas y Agenda

**Módulo:** M4 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M1 (Identidad, Autorización de Tutor, modelo de acceso del menor), M2 (Matching), M7 (bloqueo de próxima reserva si el Tutor no calificó, FR-REP-006), M9 (cancelación de reservas por sanción o revocación, FR-SEC-008/012)
**Dispara eventos consumidos por:** M3 (habilitación de sala a T-5), M5 (cobro, no-show, reembolsos)
**Referencia de tiempos:** Tabla_Tiempos_Tinku.md

---

## 1. Resumen

Este módulo coordina el compromiso de horario entre un Estudiante y un Tutor. **Cambio estructural de esta ronda:** dado que el menor tiene su propia cuenta pero no puede pagar ni autorizar Tutores por sí mismo (ver Spec de M1), se separan dos entidades distintas: la **Solicitud de Sesión** (la genera el menor, logueado con su propia cuenta de permisos restringidos — liviana, sin pago, no bloquea ningún horario) y la **Reserva** (la crea y paga exclusivamente el Adulto Responsable, ya sea a partir de una Solicitud o directamente). Esto elimina la antigua "reserva pendiente de aprobación con timeout de 4hs" — ya no hace falta, porque el adulto siempre paga en el momento que él decide.

También se elimina el flujo de aceptación/rechazo manual del Tutor por cada solicitud de reserva: la Reserva se confirma sola si cae dentro de una franja de disponibilidad publicada.

## 2. Historias de Usuario y Criterios de Aceptación

### US-1 — Publicación de disponibilidad

_Como_ Tutor, _quiero_ publicar mis franjas horarias disponibles, _para_ que solo se pueda reservar en esos momentos.

- **Dado** que configure mi calendario, **cuando** publique una franja, **entonces** solo se puede reservar dentro de franjas publicadas (FR-RES-012).
- **Dado** que la franja que intento publicar dure menos de 30 minutos o más de 180 minutos, **cuando** intente guardarla, **entonces** el sistema la rechaza con un mensaje claro del rango permitido (FR-RES-024).

### US-2 — Solicitud de Sesión por el menor

_Como_ menor, con mi propia cuenta (creada por mi Adulto Responsable, no autorregistrada), _quiero_ pedirle a mi adulto que me consiga una clase con un Tutor autorizado, _para_ no depender de que él busque por mí, sin poder yo mismo pagar ni confirmar nada.

- **Dado** que, logueado con mi propia cuenta de permisos restringidos, elijo un Tutor autorizado y un horario de una franja publicada, **cuando** genero la Solicitud, **entonces** queda registrada como `solicitud_pendiente` y notifica al Adulto Responsable — **no bloquea el horario, no genera cobro, no es una Reserva** (FR-RES-021).
- **Dado** que el Adulto Responsable no procese la Solicitud, **cuando** pasen 48hs, **entonces** la Solicitud expira automáticamente sin generar ninguna consecuencia — simplemente deja de estar disponible para convertir (FR-RES-022).

### US-3 — Conversión de Solicitud en Reserva (o reserva directa)

_Como_ Usuario con capacidad Adulto Responsable, _quiero_ ser quien efectivamente reserva y paga, _para_ mantener control total del gasto y del calendario de mi menor a cargo.

- **Dado** que exista una Solicitud pendiente de mi menor a cargo, **cuando** la revise, **entonces** puedo aprobarla (lo que crea la Reserva real y dispara el cobro, US-4) o rechazarla (la Solicitud se descarta, sin exponer motivo al Tutor — no llegó a ser una Reserva, así que ni siquiera lo notifica).
- **Dado** que quiera reservar directamente para mi menor a cargo sin pasar por una Solicitud previa, **cuando** lo haga, **entonces** el sistema lo permite igual — la Solicitud es un atajo opcional para el menor, no un paso obligatorio del flujo.
- **Dado** que sea un Estudiante adulto reservando para sí mismo, **cuando** seleccione un horario, **entonces** no hay Solicitud intermedia — reserva y paga directamente (FR-RES-001).

### US-4 — Reserva auto-confirmada y pago

_Como_ quien paga la Reserva (Estudiante adulto, o Adulto Responsable por su menor), _quiero_ que quede confirmada al instante tras pagar, _para_ no depender de que el Tutor acepte manualmente.

- **Dado** que se confirme una Reserva (directa o desde una Solicitud aprobada) dentro de una franja publicada, **cuando** eso ocurra, **entonces** queda `pendiente_pago` y se solicita el pago de inmediato — no hay paso de aceptación del Tutor; el Tutor solo recibe notificación de la nueva Reserva (FR-RES-003).
- **Dado** que la Reserva quede en `pendiente_pago` sin completarse el pago, **cuando** pasen 15 minutos, **entonces** expira y libera el horario (FR-RES-020).
- **Dado** que falten menos de 15 minutos para el horario elegido, **cuando** se intente reservar, **entonces** el sistema lo bloquea (FR-RES-013).

### US-5 — Reprogramación

_Como_ quien pagó la Reserva, _quiero_ poder cambiar el horario sin perder el dinero en escrow, _para_ no cancelar y volver a pagar por un simple cambio de horario.

- **Dado** que falten 24 horas o más para el horario agendado, **cuando** se proponga una reprogramación a otra franja publicada y disponible, **entonces** se aplica conservando el pago ya hecho en escrow y **el precio original de la reserva** (no el precio vigente de la franja al momento de reprogramar) — sin nueva transacción en MercadoPago (FR-RES-015).
- **Dado** que falten menos de 24 horas, **cuando** se intente reprogramar, **entonces** se trata como cancelación tardía, con la misma asimetría de US-7 (FR-RES-016).

### US-6 — Cancelación sin penalidad

_Como_ quien pagó la Reserva, _quiero_ poder cancelar con antelación suficiente, _para_ no perder plata por un cambio de planes razonable.

- **Dado** que falten más de 24 horas, **cuando** se cancele, **entonces** no aplica ninguna penalidad (FR-RES-004).
- **Dado** que la Reserva todavía esté `pendiente_pago` (no confirmada con pago), **cuando** se cancele, **entonces** no hay nada que cobrar ni reembolsar — la asimetría de cancelación tardía solo aplica sobre reservas ya pagas (FR-RES-017).

### US-7 — Cancelación tardía y no-show asimétricos

_Como_ Tinku, _quiero_ que la responsabilidad recaiga en quien generó la cancelación tardía o el no-show, _para_ que ni el Estudiante ni el Tutor carguen con un costo que no generaron.

- **Dado** que falten menos de 24 horas y cancele el Tutor, **cuando** eso ocurra, **entonces** se reembolsa el escrow completo a quien pagó (FR-RES-008).
- **Dado** que falten menos de 24 horas y cancele quien pagó, **cuando** eso ocurra, **entonces** se libera el escrow al Tutor con normalidad — no es una transacción nueva, es el mismo dinero ya retenido que simplemente se libera (FR-RES-008, redactado sin ambigüedad de "cobro").
- **Dado** que el Estudiante no se presente (evento `sesion.no_show_estudiante` de M3), **cuando** eso ocurra, **entonces** se libera el escrow al Tutor con normalidad (FR-RES-005).
- **Dado** que el Tutor no se presente, **cuando** eso ocurra, **entonces** se reembolsa el escrow completo a quien pagó, y se descuenta el evento en las señales implícitas de reputación del Tutor.
- **Dado** que ninguno de los dos se presente, **cuando** eso ocurra, **entonces** se reembolsa el escrow completo a quien pagó, sin liberar nada al Tutor (FR-RES-009).

### US-8 — Revocación de Tutor con sesiones futuras

_Como_ Usuario con capacidad Adulto Responsable, _quiero_ que revocar a un Tutor cancele automáticamente lo agendado con mi menor a cargo, _para_ no cancelar cada reserva a mano.

- **Dado** que revoque a un Tutor con sesiones futuras ya agendadas, **cuando** eso ocurra, **entonces** se cancelan y reembolsan automáticamente (FR-RES-006).

### US-9 — Prevención de reservas superpuestas

_Como_ Tinku, _quiero_ que sea imposible tener dos reservas al mismo horario, _para_ evitar conflictos y condiciones de carrera.

- **Dado** que dos solicitudes de pago lleguen para el mismo horario del mismo Tutor al mismo tiempo, **cuando** eso ocurra, **entonces** solo una tiene éxito — la otra falla de forma clara (FR-RES-007).

### US-10 — Recordatorios

_Como_ Estudiante, Tutor, o Adulto Responsable, _quiero_ recibir recordatorios antes de la sesión, _para_ no olvidarme.

- **Dado** que falten 24 horas para el horario agendado, **cuando** ese momento llegue, **entonces** se notifica a ambas partes, con copia al Adulto Responsable si el Estudiante es un menor (FR-RES-019). _(Si la reserva se hizo con menos de 24hs de anticipación, este recordatorio simplemente no llega a dispararse — no es un error, ya pasó ese punto en el tiempo.)_
- **Dado** que falten 5 minutos (coincide con la creación de la sala y el botón de unirse, ver Spec de M3), **cuando** ese momento llegue, **entonces** se repite la notificación con el mismo criterio de copia al adulto.

## 3. Requisitos Funcionales

| ID                      | Requisito                                                                                                                                                                                                                                                                                           |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FR-RES-001              | Reserva directa de un Estudiante adulto, sin Solicitud intermedia.                                                                                                                                                                                                                                  |
| FR-RES-003              | Reserva auto-confirmada dentro de franjas publicadas — sin aceptación manual del Tutor.                                                                                                                                                                                                             |
| FR-RES-004              | Cancelación sin penalidad hasta 24hs antes.                                                                                                                                                                                                                                                         |
| FR-RES-005              | No-show del Estudiante: se libera el escrow al Tutor con normalidad.                                                                                                                                                                                                                                |
| FR-RES-006              | Cancelación y reembolso automático de sesiones futuras al revocar un Tutor.                                                                                                                                                                                                                         |
| FR-RES-007              | Prevención de reservas superpuestas.                                                                                                                                                                                                                                                                |
| FR-RES-008              | Cancelación <24hs: asimetría (Tutor cancela → reembolso; quien pagó cancela → se libera al Tutor).                                                                                                                                                                                                  |
| FR-RES-009              | Doble ausencia: reembolso completo, sin liberar nada al Tutor.                                                                                                                                                                                                                                      |
| FR-RES-010              | Botón de unirse y creación de sala a T-5 min (ver Spec de M3).                                                                                                                                                                                                                                      |
| FR-RES-012              | Solo se reserva dentro de franjas publicadas por el Tutor.                                                                                                                                                                                                                                          |
| FR-RES-013              | Bloqueo de reservas con menos de 15 minutos de anticipación.                                                                                                                                                                                                                                        |
| FR-RES-015              | Reprogramación con ≥24hs conserva el escrow y el precio original, sin nueva transacción.                                                                                                                                                                                                            |
| FR-RES-016              | Reprogramación con <24hs se trata como cancelación tardía.                                                                                                                                                                                                                                          |
| FR-RES-017              | La asimetría de cancelación tardía solo aplica sobre reservas ya pagas, no sobre `pendiente_pago`.                                                                                                                                                                                                  |
| FR-RES-019              | Recordatorios a T-24h y T-5min; copia al Adulto Responsable si el Estudiante es menor.                                                                                                                                                                                                              |
| FR-RES-020              | Timeout de `pendiente_pago`: 15 minutos, luego libera el slot.                                                                                                                                                                                                                                      |
| FR-RES-021              | Solicitud de Sesión del menor: liviana, sin pago, no bloquea horario.                                                                                                                                                                                                                               |
| FR-RES-022              | Solicitud de Sesión no procesada expira a las 48hs sin consecuencias.                                                                                                                                                                                                                               |
| FR-RES-023 _(agregado)_ | La duración de la Sesión es la de la franja publicada que la originó (hora_fin − hora_inicio), congelada en la Reserva al momento de crearla — no cambia si el Tutor modifica sus franjas futuras. No hay duración estándar de producto; el Tutor define la duración al publicar su disponibilidad. |
| FR-RES-024 _(agregado)_ | Toda franja publicada por un Tutor debe tener una duración de entre 30 y 180 minutos (Tabla_Tiempos_Tinku.md) — el sistema rechaza la publicación de franjas fuera de ese rango.                                                                                                                    |

## 4. Fuera de Alcance de este Spec

- El procesamiento del pago en sí (ver Spec de M5).
- El no-show automático por timeout en tiempo real (ver Spec de M3 — M4 define las reglas, M3 las ejecuta).

## 5. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica aparece en este documento.
- [x] Resueltos E-11, E-12, E-13, E-14, E-15, E-16, E-17 del informe de QA.
- [x] Revisado contra la Constitución (Artículo III, Artículo IV).

---

**Estado: APROBADO.**
