# T09 — Resumen automático como adicional opcional pago (DT3)

**Branch:** `tesis/adicional-resumen` · **Riesgo:** medio (dinero) ·
**Bloqueada por:** T05, T08 (PT5 ya resuelta)

## 1. Problema (verificado al 2026-09-23)

`Spec_M6_Resumen_Automatico.md` genera el resumen "para toda sesión que lo amerite" (≥10 min).
La tesis (Cap. 5) lo convierte en un **adicional opcional**: grabar el audio y transcribirlo
cuesta USD 0,288 por sesión, el 35 % del margen de la comisión. El precio es **USD 0,50**, y el
modelo supone que lo contrata el 40 % de las sesiones (supuesto S-04, que el piloto tiene que
verificar — ver T11).

## 2. Decisión resuelta (PT5 — 2026-09-23)

Precio **$770** (`tinku.resumen.precio-adicional-ars`) y **reembolso parcial automático del
adicional** si el resumen termina `fallido`. El consentimiento de grabación se acepta al contratar
el adicional (T08), así que no existe el caso "sin consentimiento".

## 3. Implementación (con la recomendación)

1. **Reserva:** campos `resumen_contratado` (boolean) y `precio_adicional_resumen` (congelado al
   reservar, como el precio de la sesión). Migración nueva (A1).
2. **Regla de menores:** si el beneficiario es MENOR, el adicional **no se ofrece** y el backend
   rechaza `resumenContratado = true` con **422** (no confiar en el frontend).
3. **Pago:** el monto cobrado = precio de la sesión + adicional. En la preferencia de
   MercadoPago, el adicional va **íntegro a la plataforma**: `marketplace_fee = comisión (27 % de
   la sesión) + adicional`. La comisión **no** se calcula sobre el adicional. Reflejarlo en
   `Transaccion` con un campo separado para no mezclar conceptos.
4. **Reembolso del adicional:** reembolso **parcial** por el monto exacto del adicional cuando el
   resumen queda `fallido`, vía la API de reembolsos de MercadoPago. Es la **única excepción** a
   la regla de reembolsos totales de M5: documentarla como BR nueva en el Spec de M5 (la tesis la
   incorpora en su Cap. 5). Si la reserva ya tuvo un reembolso total, no aplica.
5. **M6:** `ResumenService.onSesionFinalizada` solo programa la generación si
   `resumenContratado`. Actualizar FR-SUM-001 en el Spec de M6.
6. **Frontend (`reservar/page.tsx`):** checkbox "Agregar resumen automático de la clase
   (+$770)", **oculto** si el beneficiario es un menor, con una línea de qué incluye y la
   aceptación explícita de la grabación de solo audio (cláusula de los Términos, T08).
7. **Métrica:** el piloto necesita la tasa de adopción del adicional (S-04): exponerla en T11.

## 4. Tests (RED primero)

1. `reservaConAdicional_adulto_montoIncluyeAdicional_yComisionSoloSobreSesion`.
2. `reservaConAdicional_beneficiarioMenor_422`.
3. `resumenFallido_reembolsaSoloElAdicional`.
4. `sesionSinAdicional_noGeneraResumen` (FR-SUM-001 nuevo).
5. Regresión: reserva sin adicional → mismo monto y comisión que antes.

## 5. Criterios de aceptación

- Suite verde. Spec M4, M5 y M6 actualizados (adicional, pago y FR-SUM-001).
- T-TES-09 tildada.

## 6. NO tocar

- El cálculo de la comisión de la sesión (`ComisionPlataforma`, T05).
