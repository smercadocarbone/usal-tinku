### E2E Tests: Editar cuenta (email y contraseña)

**Suite ID:** `EDITAR-CUENTA-E2E`
**Feature:** `/cuenta` — sección "Editar cuenta", habilitada por
`GET/PATCH /api/usuarios/me` y `PATCH /api/usuarios/me/password`.

---

## Test Case: `EDITAR-CUENTA-E2E-001` - Actualizar el email

**Priority:** `critical`

**Tags:** @e2e

**Description/Objective:** El email actual se precarga y se puede reemplazar.

### Expected Result:
- Tras guardar, aparece "Email actualizado.".

---

## Test Case: `EDITAR-CUENTA-E2E-002` - Email ya usado por otra cuenta

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** El backend responde 409 si el email pertenece a
otro usuario; el mensaje se muestra tal cual.

---

## Test Case: `EDITAR-CUENTA-E2E-003` - Cambiar la contraseña

**Priority:** `critical`

**Tags:** @e2e

**Description/Objective:** Con la contraseña actual correcta, el cambio
confirma y limpia ambos campos.

---

## Test Case: `EDITAR-CUENTA-E2E-004` - Contraseña actual incorrecta no desloguea

**Priority:** `high`

**Tags:** @e2e

**Description/Objective:** El backend responde 403 (no 401) para "contraseña
actual incorrecta" — un 401 dispara el logout global del interceptor de
`lib/api.ts`. Verifica que el usuario sigue en `/cuenta` tras el error.
