### E2E Tests: Pago de una reserva

**Suite ID:** `PAGAR-E2E`
**Feature:** `/pagar` — genera preferencia de pago (M5) y respeta el modo Bypass (ADR-M5-01).

---

## Test Case: `PAGAR-E2E-001` - Modo Bypass confirma sin cobrar

**Priority:** `critical`

**Tags:** @e2e, @pago

**Description/Objective:** Con la pasarela deshabilitada (toggle de M8), la reserva se
confirma sin procesar ningún cobro real y sin salir de la app.

### Flow Steps:
1. Entrar a `/pagar?reserva=r-1` con `bypass: true`.
2. Confirmar "Confirmar reserva (simulado)".

### Expected Result:
- Aviso de pasarela deshabilitada visible antes de confirmar.
- Mensaje de confirmación simulada después de confirmar.
- La URL sigue siendo `/pagar` — nunca navega a un dominio externo.

---

## Test Case: `PAGAR-E2E-002` - Pasarela real muestra el monto y la advertencia de salida

**Priority:** `high`

**Tags:** @e2e, @pago

**Description/Objective:** Con la pasarela habilitada, el Estudiante ve el precio final
exacto (Artículo III — sin desglose de comisión) antes de salir a MercadoPago.

### Flow Steps:
1. Entrar a `/pagar?reserva=r-1` con `bypass: false`.

### Expected Result:
- Se ve el monto formateado y el botón "Pagar con MercadoPago".
- Se ve la advertencia de que va a salir del sitio.

### Notes:
- No hace click en "Pagar con MercadoPago": el click real navega a un
  dominio externo (`window.location.assign`), fuera del alcance de un test
  aislado del frontend.

---

## Test Case: `PAGAR-E2E-003` - Contactar a soporte si falla el pago

**Priority:** `medium`

**Tags:** @e2e, @pago

**Description/Objective:** Cuando `POST /api/pagos/preferencia` falla, la persona puede
avisarle a Soporte Financiero sin salir de la pantalla (`FormularioSoporte`,
origen `M5.pago_fallido` — el único origen de soporte ya registrado para este caso
en `admin.mapeo_origen_rol`).

### Flow Steps:
1. Entrar a `/pagar?reserva=r-1` con `POST /api/pagos/preferencia` devolviendo 422.
2. Abrir "Contactar a soporte" y confirmar "Enviar a soporte".

### Expected Result:
- El textarea viene prellenado mencionando el número de reserva.
- Tras enviar, aparece "Le avisamos a soporte."

### Notes:
- `origenModulo` va fijo en `M5.pago_fallido`: es el único valor que no
  devuelve 422 por la FK de `mapeo_origen_rol` (ver `FormularioSoporte.tsx`).
