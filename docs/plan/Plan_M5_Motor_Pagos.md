# Plan Técnico: M5 — Motor de Pagos

**Basado en:** Spec_M5_Motor_Pagos.md (aprobado)
**Stack (Constitución):** Java + Spring Boot, MercadoPago Marketplace/Checkout Pro con split payment, Quartz para reintentos y liberación diferida.

---

## 1. Modelo de Datos (lógico)

### `transacciones`
| Campo | Tipo | Notas |
|---|---|---|
| id | UUID (PK) | |
| reserva_id | UUID (FK → reservas.id) | Relación **directa con Reserva**, nunca con Sesión (corrige la contradicción E-06/E-07/E-18 del informe de QA — el vínculo conceptual correcto siempre fue este). |
| mp_payment_id | varchar | ID externo de MercadoPago. |
| monto_bruto | decimal | Precio congelado de la Reserva. |
| comision_plataforma | decimal | 15% de `monto_bruto` (BR-PAG-01). |
| estado | enum(`retenido_escrow`, `liberado`, `reembolsado`, `pausado_denuncia`) | |
| liberar_at | timestamp, nullable | `sesion.finalizada.timestamp + 24h` (FR-PAG-002). |
| intentos_liberacion | int, default 0 | Máximo 3 antes de intervención manual (FR-PAG-007). |
| created_at | timestamp | |

### `precios_referencia_regional`
| Campo | Tipo | Notas |
|---|---|---|
| provincia | varchar (PK) | |
| valor_sugerido | decimal | |
| version | int | Se incrementa en cada revisión trimestral (FR-ADM-007 de M8) — nunca se sobreescribe la fila, para no afectar retroactivamente a Tutores que ya fijaron su precio con una versión anterior. |
| vigente_desde | timestamp | |

## 2. Eventos Entrantes (contrato formal con M3/M4/M9)

| Evento | Payload mínimo | Acción |
|---|---|---|
| `sesion.finalizada` | `reserva_id`, `timestamp_fin` | Programa job de liberación a `timestamp_fin + 24h`. |
| `sesion.interrumpida` | `reserva_id` | Reembolso total inmediato. |
| `sesion.no_show_estudiante` | `reserva_id` | Libera el escrow al Tutor de inmediato (no espera 24hs — el no-show ya es la confirmación de que la sesión no va a ocurrir). |
| `sesion.no_show_tutor` | `reserva_id` | Reembolso total inmediato. |
| `sesion.no_show_doble` | `reserva_id` | Reembolso total inmediato, sin liberar nada. |
| `sesion.killswitch_menor` | `reserva_id` | Reembolso total inmediato (FR-PAG-009). |
| `sesion.killswitch_adultos` | `reserva_id`, `detectado_id` | Reembolso total inmediato, incluso si `detectado_id` es el propio pagador (FR-PAG-012). |
| `denuncia.registrada` | `reserva_id` | `estado → pausado_denuncia`, se cancela el job de liberación si existía. |
| `denuncia.resuelta` | `reserva_id`, `resolucion` | Reanuda liberación (si infundada) o ejecuta la instrucción de M9 (si fundada). |
| `reserva.cancelada` (M4) | `reserva_id`, `cancelada_por_usuario_id` | Asimetría FR-RES-008/016: con ≥24hs al horario, o cancela el Tutor → reembolso total; con <24hs y cancela quien pagó → se libera el escrow al Tutor (no es una transacción nueva, es el mismo dinero ya retenido). |

> **Estado de esta tabla:** implementados y probados 1:1 (EscrowListenersIntegracionTest) los 8 eventos de M3 + `denuncia.registrada` (Chunk M5-B) + `reserva.cancelada` (Chunk M5-D). **`denuncia.resuelta` queda diferido**: el evento mínimo que define M9-D (`denuncia_id + usuario_sancionado_id`, ver clase `DenunciaResueltaEvent` en M4) no alcanza para reanudar el escrow — necesita `reserva_id` y `resolucion`. Al armar M9-D, revisar el payload contra esta fila (o que M9 la publique vía consulta a M5 con la `reserva_id`, que M5 ya conoce desde `denuncia.registrada`).

## 3. Flujos Técnicos Clave

### 3.1 Cobro en escrow (US-1)
1. Al crear la Reserva (M4), el backend genera una preferencia de pago en MercadoPago (Checkout Pro, modalidad Marketplace/split) con `marketplace_fee = 15%`.
   - **Decisión de implementación (Chunk M5-A):** la generación de la preferencia es **lazy via `POST /api/pagos/preferencia`** (Plan §4), no en el momento de la creación — así M4 no queda acoplado a una salida HTTP hacia MP en cada creación (y crear una Reserva sigue funcionando sin credenciales de MP: T-000-06). El guard de pagador lo resuelve el backend. La preferencia NO se persiste: el match con la Reserva va por `external_reference = reserva_id`, que es la clave que usará el webhook de M5-B.
2. El Estudiante/Adulto completa el pago en la interfaz de MercadoPago.
3. MercadoPago notifica por **webhook** — el backend nunca debe depender de que el cliente vuelva a la app para confirmar el pago (el usuario puede cerrar la pestaña).
4. Al recibir el webhook de pago aprobado, se crea la fila en `transacciones` con `estado = retenido_escrow`, y la Reserva pasa de `pendiente_pago` a `confirmada`.

### 3.2 Liberación automática (US-2)
1. Job de Quartz disparado a `liberar_at`.
2. Antes de ejecutar, valida `estado != pausado_denuncia`. Si está pausado, no hace nada — el evento `denuncia.resuelta` es quien reprograma o ejecuta la liberación después.
3. Ejecuta la liberación: en el modelo Split Payments 1:1 (Checkout Pro + `marketplace_fee`) no hay release por pago — el split se acredita solo al capturar; el job re-verifica el pago contra `GET /v1/payments/{id}` (debe seguir `approved`) antes de marcar `liberado` (impl. Chunk M5-C; la retención de 24hs es configuración de cuenta, ADR-M5-01).
4. Si la llamada falla: incrementa `intentos_liberacion`, reprograma un nuevo intento según el backoff (5min → 15min → 1h). Al llegar a 3, notifica a la cola de M8 (Soporte Financiero) y detiene los reintentos automáticos.

### 3.3 Reembolso total (US-3, US-4)
1. Todo reembolso llama a la API de reembolso de MercadoPago **con el body vacío** (reembolso total) — nunca se calcula un monto parcial en este flujo automático (FR-PAG-009). Esto hace que MercadoPago devuelva también su propia comisión, dejando el costo real en cero para Tinku (ya validado en el Spec).
2. Actualiza `transacciones.estado = reembolsado`.
3. Los reembolsos parciales (FR-PAG-010) son un flujo **manual, separado**, ejecutado desde M8 en el contexto de una disputa — no comparten código con este flujo automático, precisamente para que sea imposible que una regla automática termine haciendo un parcial por error.

> **Estado (Chunk M5-D):** reembolso total implementado y probado — `MercadoPagoClient.reembolsarPago(mpPaymentId)` (`POST /v1/payments/{id}/refunds` con body vacío) como ÚNICA vía, alcanzable solo vía el port `ReembolsoProveedor` (`ReembolsoProveedorMercadoPago`, reemplaza el fail-closed de M5-B). El parcial queda aislado en el port `ReembolsoParcialProveedor` (stub fail-closed hasta que M8 lo reemplace, T-M5-08). En este chunk también entra `reserva.cancelada` (asimetría FR-RES-008, ver §2) y el reembolso de pagos tardíos del webhook (pago aprobado con Reserva fuera de `pendiente_pago`: reembolso total + fila `reembolsado` ancla con comisión 0, idempotente por `mp_payment_id`).

### 3.4 Precio de referencia regional (US-6)
1. Al configurar el perfil por primera vez, el backend consulta `precios_referencia_regional` por la provincia declarada y sugiere `valor_sugerido` de la versión vigente.
2. El valor sugerido **no se guarda referenciado a la fila de la tabla** (no hay FK) — se copia como un valor sugerido de una sola vez al perfil del Tutor, precisamente para que una futura revisión trimestral (nueva `version`) no cambie retroactivamente lo que un Tutor ya vio y hasta pudo haber ignorado.

## 4. API (contratos de alto nivel)

| Método | Endpoint | Notas |
|---|---|---|
| `POST` | `/api/pagos/preferencia` | Genera la preferencia de MercadoPago para una Reserva. |
| `POST` | `/api/webhooks/mercadopago` | Recibe notificaciones de pago/reembolso. **Debe validar la firma del webhook** (no confiar en el payload sin verificar origen). |
| `GET` | `/api/admin/financiero/pagos-fallidos` | Cola de M8 (Soporte Financiero). |
| `POST` | `/api/admin/financiero/pagos-fallidos/{id}/reintentar` | Reintento manual tras agotar los 3 automáticos. |
| `POST` | `/api/admin/financiero/precios-regionales` | Nueva versión de la tabla (trimestral). |

## 5. ADRs de este Módulo

- **ADR-M5-01:** Ambiente de pruebas de MercadoPago. Usar usuarios de prueba operando en modo productivo controlado (no el sandbox clásico), porque los webhooks — pieza crítica de este módulo — no son confiables en el sandbox clásico según lo verificado en la sesión de diseño. Configurar esto **antes** de escribir cualquier test de integración de este módulo, no después.

## 6. Trazabilidad con el Spec

FR-PAG-001 a FR-PAG-013 cubiertos. El punto más sensible de todo el módulo es 3.3 (reembolso siempre total) — cualquier código nuevo que toque reembolsos debe pasar por esa única función, nunca reimplementar la llamada a MercadoPago en otro lugar, para no reabrir accidentalmente la puerta a un reembolso parcial automático que el Spec prohíbe explícitamente.

---

**Estado: Borrador de Plan técnico.** ~~Pendiente: ADR-M5-01 antes de escribir tests de integración.~~ Chunk M5-A implementado (T-M5-01, T-M5-02) con tests que NO pegan contra el provider real (stub HTTP local + cliente mockeado); ADR-M5-01 sigue pendiente antes de escribir tests de integración que toquen MercadoPago real. **Chunk M5-B implementado (T-M5-03, T-M5-04):** webhook con firma HMAC (X-Signature + anti-replay) que reconcilia contra `GET /api/payments/{id}` y confirma la Reserva (reemplaza `confirmar-pago-simulado`); escrow con los 8 listeners de `sesion.*` + `denuncia.registrada` (ver NOTA de §2). **Chunk M5-C implementado (T-M5-05, T-M5-06):** job de Quartz persistido a `liberar_at` + reintentos 5/15/1h con alerta a Soporte y cola `pagos-fallidos` (`intentos_liberacion`); `LiberacionProveedorMercadoPago` real. **Nota de integración M5-C:** en el Split Payments 1:1 elegido en M5-A el split se acredita solo al capturar (no hay release por pago) — el job verifica `approved` contra `/v1/payments/{id}` antes de marcar `liberado`; la retención de 24hs queda como configuración de cuenta, a validar con ADR-M5-01 antes de los tests contra el provider real. **Chunk M5-D implementado (T-M5-07, T-M5-08):** `MercadoPagoClient.reembolsarPago` (`POST /v1/payments/{id}/refunds`, body vacío → MP reintegra su comisión, FR-PAG-009) como única vía automática (`ReembolsoProveedorMercadoPago`); parcial manual aislado en `ReembolsoParcialProveedor` (fail-closed hasta M8). Además de T-M5-07/08 entran los dos diferidos de M5-B: **`reserva.cancelada`** resuelve la asimetría FR-RES-008/016 (≥24hs o cancela el Tutor → reembolso; <24hs y cancela quien pagó → liberación al Tutor vía el mismo camino resiliente de M5-C) y el **pago tardío** del webhook (Reserva fuera de `pendiente_pago` → reembolso total + fila `reembolsado` ancla con comisión 0, idempotente). `denuncia.resuelta` sigue diferida a M9-D (payload). Suite 240 tests, 0 fallos; merge FF a `main`.
