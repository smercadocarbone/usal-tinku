### E2E Tests: Detalle de reserva — entrar a la clase y calificar

**Suite ID:** `RESERVA-DETALLE-E2E`
**Feature:** `/cuenta/reservas/[id]` — botón "Entrar a la clase", calificación
post-sesión (M7) y resumen automático (M6), habilitados por
`GET /api/sesiones/por-reserva/{id}` y `GET /api/sesiones/{id}/resumen`.

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

## Test Case: `RESERVA-DETALLE-E2E-004` - Resumen disponible se muestra

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** Antes de este cambio el módulo M6 generaba el
resumen automático de la sesión pero no había ningún endpoint ni pantalla
para leerlo — la funcionalidad completa era inalcanzable.

---

## Test Case: `RESERVA-DETALLE-E2E-005` - Sin resumen disponible, no se muestra nada

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** `disponible: false` colapsa todos los estados
no-generado (pendiente/fallido/suspendido por seguridad/etc.) — la pantalla
simplemente no ofrece la tarjeta, sin distinguir el motivo.

---

## Test Case: `RESERVA-DETALLE-E2E-006` - Calificación ya cargada, editar dentro de la ventana

**Priority:** `critical`

**Tags:** @e2e

**Description/Objective:** Antes de este cambio no había forma de recuperar
la propia calificación al volver a cargar la pantalla — GET
`/api/sesiones/{id}/calificacion` la habilita.

---

## Test Case: `RESERVA-DETALLE-E2E-007` - Vencida la ventana de 48hs, sin editar/borrar

**Priority:** `medium`

**Tags:** @e2e

---

## Test Case: `RESERVA-DETALLE-E2E-008` - Borrar la propia calificación

**Priority:** `medium`

**Tags:** @e2e

---

## Test Case: `RESERVA-DETALLE-E2E-003` - Sin sesión todavía, ninguno de los dos

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** Una Reserva que nunca se confirmó no tiene Sesión
programada (`GET /sesiones/por-reserva` devuelve 404) — la pantalla no debe
ofrecer ni entrar a una clase inexistente ni calificar algo que no pasó.

### Expected Result:
- Ni "Entrar a la clase" ni "Enviar calificación" están presentes.
