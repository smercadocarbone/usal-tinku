# ADR-M5-04 — Comisión de plataforma al 21 % (modifica BR-PAG-01)

**Estado:** Aceptado (decisión del dueño del producto, 2026-09-28). Implementado en T-TES-15.
Modifica BR-PAG-01 de `docs/specs/Spec_M5_Motor_Pagos.md` y deja superado a T-TES-05
(`docs/superpowers/specs/tesis/T05-comision-27.md`). No cambia la fila "Pagos" del Registro de
Decisiones Técnicas de la Constitución, porque esa fila no fija el porcentaje.

## Contexto

**Historial de BR-PAG-01:**

| Desde | Comisión | Por qué |
| --- | --- | --- |
| Diseño original | 15 % | Valor inicial del Spec de M5. |
| 2026-09-23 (T-TES-05) | 27 % | En el diseño original, **la plataforma** pagaba la comisión de procesamiento de MercadoPago (6,04 % con IVA). Con 27 %, el Tutor recibía el 73 % del precio y el VAN del escenario base de la tesis (Cap. 5, modelo de entonces) dejaba de ser negativo. |
| 2026-09-28 (este ADR) | 21 % | Con el modelo A (ADR-M5-02), MercadoPago le cobra su comisión **al vendedor**, que es el Tutor. |

**Cómo reparte MercadoPago con el modelo A.**
- Cada Tutor cobra con su propio token, por OAuth.
- La documentación de Checkout Pro para marketplace
  (<https://www.mercadopago.com.ar/developers/es/docs/checkout-pro/how-tos/integrate-marketplace>)
  dice textualmente: "La comisión de Mercado Pago se descuenta de los fondos recibidos por el
  vendedor. Es decir, primero se descuenta la comisión de Mercado Pago, y la comisión del
  marketplace se descuenta del saldo restante."
- Resultado: con 27 %, al Tutor le quedaba 1 − 0,27 − 0,0604 = **66,96 %** del precio, seis
  puntos menos que lo que se había calibrado.

**Por qué importa ese neto.**
- **Riesgo R-02 de la tesis:** con menos neto, al Tutor le conviene más arreglar las clases por
  fuera de la plataforma.
- **Riesgo R-07:** cuesta más sumar Tutores.
- El Artículo IV de la Constitución pide revisar la comisión priorizando el acceso, no solo el
  margen.

## Decisión

1. **La comisión de plataforma pasa a 21 % del monto bruto de la sesión**, a cargo del Tutor.
   - Con la comisión de MercadoPago descontada antes, al Tutor le queda
     1 − 0,21 − 0,0604 = **72,96 %**, prácticamente el 73 % con el que se calibró T-TES-05.
   - La tesis (Cap. 5) ya está recalculada con 21 %. El VAN del escenario base se anula recién
     con 12,4 %, así que 21 % deja margen.
2. **Sigue siendo configuración:** `tinku.mercadopago.marketplace-fee-percent: 21` en
   `application.yml`, y el mismo default en el `@Value` de `ComisionPlataforma`.
   `ComisionPlataforma` sigue siendo el único cálculo: la preferencia (`marketplace_fee`) y
   `Transaccion.comisionPlataforma` dan siempre el mismo número.
3. **Lo que no cambia:**
   - El adicional de resumen (BR-PAG-11, $770) sigue entrando entero al `marketplace_fee`; la
     comisión no se calcula sobre él.
   - En el paquete mensual (ADR-M5-03), la comisión se sigue calculando clase por clase.
4. **Sin backfill.** `comision_plataforma` se congela por transacción: las transacciones
   anteriores conservan su 27 %, y ninguna migración aplicada se edita.

## Alternativas descartadas

- **Mantener 27 %.** Le da más margen a Tinku, pero el Tutor se queda con 66,96 %, seis puntos
  menos que lo calibrado. Eso empeora R-02 y R-07, justo en el piloto, cuando hay que
  conseguir Tutores.
- **Bajar a 15 %.** Maximiza el neto del Tutor (78,96 %), pero el VAN del escenario base queda en
  USD 2.494 y el del escenario pesimista, muy negativo. No deja margen para desvíos del piloto.

## Consecuencias

- **BR-PAG-01 = 21 %.** El historial queda en Spec_M5, y los comentarios de código citan
  BR-PAG-01 sin el número, para que no vuelvan a quedar viejos.
- **Pestaña "Precio" del Tutor (FR-PAG-019).** Muestra el porcentaje que devuelve el backend, así
  que pasa a decir 21 % sin cambios. El neto que calcula todavía no descuenta la comisión de
  MercadoPago: queda como decisión aparte del dueño, fuera de este ADR.
- **Transacciones anteriores.** Conservan la comisión con la que se crearon: los reportes de
  cobros muestran 27 % en las viejas y 21 % en las nuevas.
- **Revisión.** La métrica para revisar la comisión después del piloto sigue diferida
  (BR-PAG-03).
