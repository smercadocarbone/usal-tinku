# T05 — Comisión de plataforma al 27 % (DT1)

**Branch:** `tesis/comision-27` · **Riesgo:** medio (dinero) · **Bloqueada por:** —

## 1. Problema (verificado al 2026-09-23)

`pagos/service/ComisionPlataforma` calcula la comisión con
`tinku.mercadopago.marketplace-fee-percent`, que en `backend/src/main/resources/application.yml`
vale **15** (BR-PAG-01). La evaluación económica de la tesis (Cap. 5) se sostiene con **27 %**:
por debajo de 23,4 % el VAN del escenario base es negativo.

## 2. Implementación

1. `application.yml`: `marketplace-fee-percent: 27`. Mantener la property (no hardcodear).
2. `ComisionPlataforma`: default del `@Value` a `27` y javadoc actualizado (hoy dice "15%").
3. `docs/specs/Spec_M5_Motor_Pagos.md`: BR-PAG-01 → 27 %, con una línea de historial que
   remita a la tesis (Cap. 5, calibración de la comisión). No borrar el texto anterior: tacharlo
   o moverlo al historial.
4. Frontend: `rg -n "15 ?%|0\.15|comisi" frontend/src` y actualizar todo texto o cálculo que
   muestre la comisión o el neto del Tutor.

## 3. Tests (RED primero)

1. `ComisionPlataformaTest`: `calcular(15000.00) == 4050.00` (hoy da 2250.00 → rojo).
2. Los tests que **afirman** la comisión calculada (`PagosWebhookIntegracionTest` ~L332,
   `E2EFlujoFelizIntegracionTest` ~L358) pasan a esperar `4050.00`. **A7:** estos tests
   codificaban la regla de negocio vieja, no un bug: explicarlo en el commit.
3. Los tests que solo **setean** `setComisionPlataforma(2250.00)` como fixture (escrow,
   reembolso, denuncias, admin) no dependen del porcentaje: **no tocarlos**.

## 4. Criterios de aceptación

- Suite verde con la línea `Tests run:`. N no baja.
- La preferencia de MercadoPago (`marketplace_fee`) y `Transaccion.comisionPlataforma` dan el
  mismo número (el javadoc de `ComisionPlataforma` lo exige).
- T-TES-05 tildada en los dos archivos de tareas.

## 5. NO tocar

- Las transacciones ya creadas: conservan su comisión congelada.
- `ComisionPlataforma` sigue siendo el **único** cálculo (no duplicar la fórmula en otro lado).
