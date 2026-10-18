# ADR-M5-05 — Comisión de plataforma al 27 % (supera ADR-M5-04; modifica BR-PAG-01)

**Estado:** Aceptado (decisión del dueño del producto, 2026-10-14). Implementado en T-TES-16.
Deja superado a ADR-M5-04 (21 %). No cambia la fila "Pagos" del Registro de Decisiones Técnicas.

## Contexto
La tesis vigente (Cap. 5, macro `\comision{}` = 27 %) recalculó la evaluación económica con un
ticket de USD 7,42: VAN base USD 3.573, TIR 20,47 %, y la comisión que anula el VAN en ≈ 25,3 %.
A 21 % el VAN del escenario base es USD −9.228. La premisa de ADR-M5-04 ("la tesis está recalculada
con 21 %, el VAN se anula con 12,4 %") dejó de ser verdad.

## Decisión
1. **BR-PAG-01 = 27 %** del monto bruto de la sesión, a cargo del Tutor.
   `tinku.mercadopago.marketplace-fee-percent: 27`; `ComisionPlataforma` sigue siendo el único cálculo.
2. **El modelo de cobro A (ADR-M5-02) no cambia.** MercadoPago descuenta su comisión (6,04 % con IVA)
   de los fondos del Tutor antes del `marketplace_fee`: el Tutor recibe 1 − 0,27 − 0,0604 = **66,96 %**
   (con 21 % recibía 72,96 %). El dueño acepta ese costo (DT1).
3. **Discrepancia con la tesis, declarada:** la economía unitaria de la tesis descuenta la pasarela
   como costo de Tinku; con el modelo A la paga el Tutor. El margen real de Tinku es entonces mayor
   que el de la tesis: el modelo financiero es una cota inferior conservadora. Es un ajuste de la
   tesis, no del código.
4. **Sin backfill.** Cada transacción conserva la comisión con la que se creó; ninguna migración
   aplicada se edita.
5. El adicional de resumen sigue entrando entero al `marketplace_fee` sin comisión encima (BR-PAG-11,
   ADR-M5-06); en el paquete mensual la comisión se calcula clase por clase (ADR-M5-03).

## Alternativas descartadas
- **Mantener 21 %:** el VAN base queda negativo con el modelo de la tesis vigente.
- **Que Tinku absorba la comisión de MercadoPago:** exigiría conocer la comisión real de cada pago
  (variable según el plazo de acreditación de cada cuenta); es una dependencia nueva, no configuración.

## Consecuencias
- Vuelven a valer los riesgos R-02 (desintermediación) y R-07 (cuesta más sumar Tutores), con seis
  puntos menos de neto para el Tutor. La métrica para revisar la comisión tras el piloto sigue
  diferida (BR-PAG-03).
  **Nota 2026-10-18 (T-TES-11b, sin piloto):** sin piloto, la revisión pasa a **trabajo futuro** (primer trimestre
  de operación con cobro real); las sesiones moderadas usan pagos en bypass y no aportan datos de comisión.
- La pestaña "Precio" (FR-PAG-019) toma el porcentaje del backend: muestra Tinku 27 % + MercadoPago ~6,04 %.
