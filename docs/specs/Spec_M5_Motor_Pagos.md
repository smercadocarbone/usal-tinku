# Spec: M5 — Motor de Pagos

**Módulo:** M5 (ver Constitución, Artículo VI)
**Estado:** Borrador para revisión
**Depende de:** M3 (eventos de finalización/no-show/corte de sesión, incluidos los de kill-switch), M4 (Reserva confirmada), M9 (pausa y reanudación de escrow por Denuncia, efectos de sanción)
**BR-PAG-04 — Piso de la tarifa por hora (T06, DT2):** el Tutor no puede fijar un `precioHora` menor al piso vigente, USD 4/h expresado en ARS (`tinku.tarifa.piso-hora-ars`, hoy **$6.140**; tesis Cap. 5: por debajo la comisión no cubre la operación). PT3: valor fijo en ARS, lo revisa un Admin una vez por mes (variable de entorno), sin API de tipo de cambio. PT4: **no retroactivo** — una tarifa ya guardada por debajo sigue vigente hasta que el Tutor la edite; `PUT /api/pagos/tarifa` responde **422** con `pisoHora`, y `GET` devuelve `pisoHora` siempre (con `precioHora: null` si no hay tarifa). No es lo mismo que el precio de referencia regional (FR-PAG-005), que es una sugerencia.

**BR-PAG-11 — Adicional de resumen (T09, DT3):** el pagador puede sumar el resumen automático al reservar ($770, `tinku.resumen.adicional.precio-ars`, congelado en la Reserva). Se cobra sesión + adicional; el adicional va **íntegro a la plataforma** (`marketplace_fee` = 27 % de la sesión + adicional) y la comisión **no** se calcula sobre él (`transacciones.monto_adicional_resumen`). Si el resumen termina fallido o sin generarse, se hace un **reembolso parcial por el monto exacto del adicional** — única excepción a los reembolsos totales de M5. No aplica si la Reserva ya se reembolsó entera; si MercadoPago falla, lo resuelve el Admin financiero por la cola de reembolsos parciales (FR-PAG-010).

**Historial BR-PAG-01:** comisión de plataforma al **27 %** del monto bruto desde el 2026-09-23, calibrada en el Cap. 5 de la tesis (por debajo de 23,4 % el VAN del escenario base es negativo). Antes: **15 %**.

---

## 1. Resumen

Este módulo gestiona el dinero: cobro vía MercadoPago en escrow, la comisión de plataforma, la liberación de fondos al Tutor, y todos los reembolsos automáticos que se disparan desde otros módulos.

## 2. Eventos Entrantes (de M3, M4, M9)

| Evento | Origen | Acción de M5 |
|---|---|---|
| `sesion.finalizada` | M3 | Inicia la cuenta de 24hs para liberar el escrow al Tutor (FR-PAG-002). |
| `sesion.interrumpida` (corte <50%) | M3 | Reembolso completo al Estudiante (FR-AULA-005). |
| `sesion.no_show_estudiante` | M3/M4 | Se cobra al Estudiante; se libera el pago al Tutor con normalidad (FR-RES-005 de M4). |
| `sesion.no_show_tutor` | M3/M4 | Reembolso completo al Estudiante. |
| `sesion.no_show_doble` | M3/M4 | Reembolso completo al Estudiante, sin pago al Tutor (FR-RES-009 de M4). |
| `sesion.killswitch_menor` | M3 | Pausa el escrow (`pausado_alerta`, FASE2-10 — la pausa por Alerta de seguridad manda sobre la de Denuncia); el reembolso completo espera a `alerta.resuelta` (ADR-M3-02, AUD-005). |
| `sesion.killswitch_adultos` | M3 | Pausa el escrow (`pausado_alerta`); el reembolso completo espera a `alerta.resuelta`, y se hace incluso si el Estudiante fue el detectado — ver FR-PAG-012 y ADR-M3-02. |
| `alerta.resuelta` _(agregado, ADR-M3-02, 2026-09-22)_ | M9 | Reembolso completo al Estudiante del escrow que pausó el kill-switch, sea la decisión `reactivar` o `sancionar`. (FASE2-10) Actúa solo sobre `pausado_alerta` — también si la Denuncia de la misma sesión se resolvió antes (no-op): la Alerta manda y el reembolso es por esta vía. Payload: `reservaId`. |
| `denuncia.registrada` (con escrow activo) | M9 | Pausa la liberación de fondos hasta resolución. (FASE2-10) Si el escrow ya está `pausado_alerta`, no baja el estado: la pausa por Alerta es más grave. |
| `denuncia.resuelta` | M9 | Reanuda la liberación o ejecuta el reembolso/sanción que corresponda, según `resolucion` (`infundada` → reconta las 24hs de FR-PAG-002; `fundada` → liberación al Tutor de la sesión puntual si el encadenamiento de la sanción de M4 no la canceló antes; `escalada` → reembolso completo FR-PAG-011/009). (FASE2-10) Si el escrow está `pausado_alerta`, es no-op con log: la Alerta de seguridad manda y la destraba `alerta.resuelta`. **Payload enriquecido por T-M9-04** (documentado en `DenunciaResueltaEvent`): agregó `reservaId` (nullable, denuncia sin sesión) y `resolucion`; conserva el constructor original (stub → `reservaId=null`/`resolucion=null`) para compatibilidad del listener de M4, que ante `resolucion=null` mantiene el comportamiento de cancelar reservas futuras. |
| `reserva.cancelada` _(agregado en T-M4-08)_ | M4 | Cancelación manual de una Reserva confirmada. El payload (reservaId + quien cancela) y el horario permiten a M5 decidir la asimetría de FR-RES-008: ≥24hs o <24hs con cancelación por el Tutor → reembolso al pagador; <24hs con cancelación por quien pagó → liberación del escrow al Tutor. No aplica a `pendiente_pago` (FR-RES-017, no hay nada que mover). |

## 3. Historias de Usuario y Criterios de Aceptación

### US-1 — Cobro en escrow
*Como* Estudiante, *quiero* que mi pago quede retenido hasta que la clase efectivamente pase, *para* tener una garantía real si algo sale mal.

- **Dado** que confirme una Reserva, **cuando** complete el pago vía MercadoPago, **entonces** el dinero queda retenido en escrow.

### US-2 — Liberación automática al Tutor
*Como* Tutor, *quiero* cobrar automáticamente después de dar la clase, *para* no tener que reclamar el pago manualmente.

- **Dado** que la Sesión finalice normalmente (`sesion.finalizada`), **cuando** pasen 24 horas, **entonces** se liberan los fondos al Tutor (FR-PAG-002), descontando la comisión del 27%.
- **Dado** que exista una Denuncia activa sobre esa sesión al cumplirse esas 24 horas, **cuando** eso ocurra, **entonces** la liberación se pausa hasta resolución — con un límite explícito: 48hs de descargo + hasta 5 días hábiles de revisión del Admin de Moderación y Seguridad (FR-SEC-010 de M9); si se excede, el caso escala con prioridad alta, así el escrow nunca queda pausado indefinidamente.
- **Dado** que el corte de conectividad ocurra después del 50% de la duración, **cuando** eso pase, **entonces** la sesión se considera realizada y se libera el pago completo al Tutor con normalidad (consistente con US-5 de M3).

### US-3 — Reembolsos automáticos
*Como* Estudiante, *quiero* que todos los reembolsos ya definidos en otros módulos se ejecuten acá de forma centralizada, *para* no depender de que cada módulo "sepa" cómo hacer un reembolso.

- **Dado** que ocurra cualquiera de los eventos de la tabla de la sección 2, **cuando** se dispare, **entonces** este módulo ejecuta el reembolso correspondiente.

### US-4 — Reembolsos siempre totales, comisión de gateway absorbida por Tinku
*Como* Estudiante, *quiero* que un reembolso me devuelva el 100%, *para* no perder plata por una comisión de la que no tengo la culpa.

- **Dado** que se ejecute cualquier reembolso a un Estudiante, **cuando** se procese, **entonces** es siempre un reembolso **total** vía la API de MercadoPago con body vacío — este método hace que MercadoPago reintegre el 100% de su propia comisión también, por lo que el costo real para Tinku es cero. **Prohibido el reembolso parcial automático** que traslade la comisión al Estudiante — genera riesgo de contracargo y choca con la Ley de Defensa del Consumidor de Argentina. Los únicos reembolsos parciales posibles son los gestionados manualmente por una disputa en M8/M9 (FR-PAG-010), nunca una regla automática de este módulo (FR-PAG-009).
- **Dado** que un reembolso parcial surja de una disputa gestionada en M8/M9 (no de una regla automática), **cuando** eso ocurra, **entonces** la diferencia de comisión de gateway la absorbe Tinku, nunca el Estudiante (FR-PAG-010).

### US-5 — Fondos de un Tutor con sanción definitiva
*Como* Tutor sancionado, *quiero* cobrar por el trabajo que ya hice, *para* no perder ingresos por sesiones que ya dicté correctamente.

- **Dado** que un Tutor reciba una sanción definitiva (M9), **cuando** eso ocurra, **entonces** se libera el pago correspondiente a sesiones ya realizadas; se retiene únicamente lo correspondiente a sesiones futuras, que se cancelan con reembolso al Estudiante (FR-PAG-011).

### US-5bis — Reembolso íntegro incluso al infractor detectado
*Como* Tinku, *quiero* que el reembolso del kill-switch sea siempre total, incluso si el Estudiante fue quien generó la detección en la rama de ambos adultos, *para* mantener la regla de "nunca reembolso parcial" sin excepciones que la compliquen.

- **Dado** que el Estudiante sea el detectado en la rama de ambos adultos (US-7 de M3) y el otro participante confirme la infracción, **cuando** se ejecute el reembolso, **entonces** recibe igual el 100% de vuelta (FR-PAG-012) — la plataforma no usa el dinero como castigo; la consecuencia real para el infractor es la sanción de cuenta que aplica M9 (FR-SEC-012), no quedarse con fondos retenidos.

### US-5ter — Precio conservado en reprogramación
*Como* Tutor o Estudiante, *quiero* que reprogramar una sesión no cambie el precio acordado, *para* no pagar (o cobrar) distinto por una simple reprogramación.

- **Dado** que se reprograme una Reserva con ≥24hs de anticipación, **cuando** eso ocurra, **entonces** se conserva el precio original de la Reserva, sin importar si el precio vigente de esa franja cambió desde entonces (FR-PAG-013).

### US-5quater — Contracargo bancario en paralelo a Tinku _(agregado, auditoría 2026-09-18)_
*Como* Tinku, *quiero* un camino definido para cuando el titular de la tarjeta disputa el cargo directamente con su banco (contracargo/chargeback) en lugar de usar el flujo de Denuncia de la plataforma, *para* no descubrir el caso recién cuando MercadoPago ya retuvo o descontó fondos.

- **Dado** que MercadoPago notifique un contracargo sobre una transacción con escrow ya liberado al Tutor, **cuando** eso ocurra, **entonces** el caso se enruta a la cola de intervención manual de Soporte Financiero (M8, misma cola que FR-PAG-007) — no hay reversión automática de fondos ya liberados al Tutor (FR-PAG-015).
- **Dado** que el contracargo llegue con el escrow todavía retenido, **cuando** eso ocurra, **entonces** se pausa la liberación con el mismo criterio que una Denuncia con escrow activo (FR-PAG-002), hasta que Soporte Financiero lo resuelva manualmente (FR-PAG-016).
- Este flujo es explícitamente distinto del de Denuncia de M9: un contracargo lo inicia el banco emisor de la tarjeta, no un usuario dentro de Tinku, y su resolución depende primero de MercadoPago/la red de la tarjeta, no del Admin de Moderación y Seguridad.

### US-6 — Precio de referencia regional
*Como* Tutor, *quiero* una sugerencia de precio al configurar mi perfil, *para* no adivinar cuánto cobrar en mi zona.

- **Dado** que configure mi perfil por primera vez, **cuando** ingrese mi provincia, **entonces** el sistema sugiere un precio de referencia no vinculante.
- **Dado** que ya haya configurado mi precio, **cuando** vuelva más tarde, **entonces** el precio sugerido es fijo — solo cambia si Tinku recalcula toda la tabla regional, no a pedido individual (FR-PAG-006).

### US-7 — Transparencia de comisión
*Como* Estudiante, *quiero* ver un precio final simple, *para* no hacer cuentas de cuánto se lleva la plataforma (Artículo III).

- **Dado** que vea el precio de una sesión, **cuando** lo mire, **entonces** es el precio final — la comisión del 27% se descuenta del lado del Tutor, nunca aparece como línea aparte.

### US-8 — Resiliencia ante caída de MercadoPago
*Como* Tutor, *quiero* cobrar aunque MercadoPago tenga una falla momentánea, *para* no perder plata por un problema ajeno.

- **Dado** que la liberación automática falle porque MercadoPago no responde, **cuando** eso ocurra, **entonces** el sistema reintenta automáticamente (hasta 3 veces, con backoff de 5min → 15min → 1 hora) y, en paralelo, genera una alerta inmediata al Admin de Soporte Financiero. Si los 3 reintentos fallan, pasa a su cola de intervención manual (FR-PAG-007, ver Spec de M8).

### US-9 — Notificación individual
*Como* Tutor, *quiero* recibir una notificación por cada pago o reembolso, *para* llevar el control exacto, incluso con varias sesiones el mismo día.

- **Dado** que ocurra un pago o reembolso, **cuando** eso pase, **entonces** se notifica individualmente, nunca agrupado (FR-PAG-008).

## 4. Requisitos Funcionales

| ID | Requisito |
|---|---|
| FR-PAG-001 | Cobro vía MercadoPago en escrow al confirmar la Reserva. |
| FR-PAG-002 | Liberación automática al Tutor 24hs después de finalizada la Sesión, salvo Denuncia activa. |
| FR-PAG-003 | Comisión de plataforma del 27%, a cargo del Tutor, no visible como línea aparte. |
| FR-PAG-004 | Ejecución centralizada de todos los reembolsos automáticos (tabla de eventos, sección 2). |
| FR-PAG-005 | Precio de referencia regional no vinculante, sugerido una vez. |
| FR-PAG-006 | El precio de referencia es fijo; solo cambia con recálculo de toda la tabla regional. |
| FR-PAG-007 | Ante falla de MercadoPago al liberar fondos: 3 reintentos con backoff (5min/15min/1h) + alerta inmediata al Admin de Soporte Financiero en paralelo; tras agotar reintentos, pasa a su cola de intervención manual (M8). |
| FR-PAG-008 | Notificación individual por cada pago o reembolso. |
| FR-PAG-009 | Todo reembolso a Estudiantes es total (body vacío en la API de MP); prohibido el reembolso parcial que traslade comisión al Estudiante. |
| FR-PAG-010 | Reembolsos parciales por disputa (M8/M9): la diferencia de comisión la absorbe Tinku. |
| FR-PAG-011 | Sanción definitiva a un Tutor: se libera el pago de sesiones ya realizadas, se retiene y reembolsa lo de sesiones futuras. |
| FR-PAG-012 | El reembolso por kill-switch es total incluso si el Estudiante fue el infractor detectado en la rama de ambos adultos. |
| FR-PAG-013 | La reprogramación con ≥24hs conserva el precio original de la Reserva, no el precio vigente de la franja al momento de reprogramar. _(FASE2-01, D6: la tarifa del Tutor es **por hora** — `precio_hora` — y la Reserva congela `precio_hora × duracion_minutos / 60`, 2 decimales HALF_UP; el precio de referencia regional también es por hora.)_ |
| FR-PAG-014 _(agregado)_ | El precio configurado por el Tutor es un valor por hora. El monto final de cada Reserva = precio_hora × (duración_franja_minutos / 60), redondeado a 2 decimales. Aplica tanto al precio de referencia regional (US-6) como al precio final que ve el Estudiante (Artículo III). |
| FR-PAG-015 _(agregado, auditoría 2026-09-18)_ | Contracargo bancario sobre escrow ya liberado: cola de intervención manual de Soporte Financiero (M8); sin reversión automática de fondos ya liberados. |
| FR-PAG-016 _(agregado)_ | Contracargo bancario sobre escrow todavía retenido: pausa la liberación con el mismo criterio que una Denuncia con escrow activo, hasta resolución manual. |
| FR-PAG-017 _(agregado 2026-09-25, R2)_ | Conciliación del pago sin depender de la vuelta del navegador ni del webhook: cada preferencia queda registrada (`pagos.preferencias_pago`), vence junto con la Reserva sin pagar y no acepta efectivo (ticket, cajero). Antes de vencer una `pendiente_pago` y cada 5 min durante 48 hs (Tabla de Tiempos), M5 busca los pagos por `external_reference` y procesa los aprobados por el mismo camino del webhook: confirma, o reembolsa si la Reserva ya venció. Un pago aprobado que no puede resolver (monto distinto) abre un ticket de Soporte Financiero una sola vez. |

## 5. Casos Borde — Resueltos, 1 Diferido a Propósito

| # | Caso / Pregunta | Resolución |
|---|---|---|
| 1 | Recálculo del precio de referencia | Fijo, solo cambia con recálculo de toda la tabla regional (FR-PAG-006). |
| 2 | Caída de MercadoPago al liberar fondos | 3 reintentos con backoff + alerta inmediata al Admin de Soporte Financiero en paralelo (FR-PAG-007). |
| 3 | Notificación de pagos/reembolsos múltiples | Individual, nunca agregada (FR-PAG-008). |
| 4 | Métrica objetivo para revisar el 27% tras el piloto | **Diferido a propósito** — se define en el momento de esa revisión (BR-PAG-03). No bloquea este Spec. |
| 5 | Quién absorbe la comisión de gateway en un reembolso | Tinku, siempre — reembolso total vía MP, costo real cero (FR-PAG-009/010). |
| 6 | Fondos de un Tutor con sanción definitiva | Se libera lo de sesiones ya realizadas, se retiene y reembolsa lo futuro (FR-PAG-011). |
| 7 _(agregado, auditoría 2026-09-18)_ | Titular de la tarjeta disputa el cargo directamente con su banco (contracargo), en paralelo o en vez de usar la Denuncia de Tinku | Cola de intervención manual de Soporte Financiero; pausa si el escrow sigue retenido, sin reversión automática si ya se liberó (FR-PAG-015/016). |

## 6. Fuera de Alcance de este Spec

- La integración técnica con la API de MercadoPago (webhooks, tokens, sandbox) — Plan técnico.
- La lógica de cuándo se dispara cada evento (kill-switch, no-show, etc.) — ya definida en M3, M4 y M9; este módulo solo ejecuta la acción de pago.

## 7. Checklist de Revisión

- [x] Todas las Historias de Usuario tienen criterios de aceptación testeables.
- [x] Ninguna decisión técnica aparece en este documento.
- [x] Casos borde resueltos (5 de 6; 1 diferido a propósito, no bloqueante).
- [x] Revisado contra la Constitución (Artículo III — transparencia de precio, Artículo IV — accesibilidad).

---

**Estado: APROBADO** (con el ítem #4 explícitamente diferido, no bloqueante). Listo para pasar al Plan técnico de M5.
