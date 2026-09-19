### E2E Tests: Detalle de reserva — entrar a la clase y calificar

**Suite ID:** `RESERVA-DETALLE-E2E`
**Feature:** `/cuenta/reservas/[id]` — botón "Entrar a la clase" y calificación
post-sesión (M7), habilitados por `GET /api/sesiones/por-reserva/{id}`.

---

## Test Case: `RESERVA-DETALLE-E2E-001` - Entrar a la clase con sesión programada

**Priority:** `critical`

**Tags:** @e2e, @aula

**Description/Objective:** Una Reserva confirmada con Sesión ya programada
(`no_iniciada`) ofrece un link directo al aula — antes no existía ningún
camino en la UI para llegar a `/aula/[id]`.

### Expected Result:
- El link "Entrar a la clase" apunta a `/aula/{sesionId}`.

---

## Test Case: `RESERVA-DETALLE-E2E-002` - Calificar una sesión finalizada

**Priority:** `critical`

**Tags:** @e2e

**Description/Objective:** Con la Sesión en estado `finalizada`, aparece el
formulario de calificación (no el botón de entrar) y enviarlo confirma.

### Flow Steps:
1. Elegir 5 estrellas y enviar.

### Expected Result:
- Mensaje de confirmación tras enviar.

---

## Test Case: `RESERVA-DETALLE-E2E-003` - Sin sesión todavía, ninguno de los dos

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** Una Reserva que nunca se confirmó no tiene Sesión
programada (`GET /sesiones/por-reserva` devuelve 404) — la pantalla no debe
ofrecer ni entrar a una clase inexistente ni calificar algo que no pasó.

### Expected Result:
- Ni "Entrar a la clase" ni "Enviar calificación" están presentes.
