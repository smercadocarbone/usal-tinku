### E2E Tests: Olvidé mi contraseña

**Suite ID:** `RECUPERAR-PASSWORD-E2E` / `RESETEAR-PASSWORD-E2E`
**Feature:** `/recuperar-password` y `/resetear-password`, habilitadas por
`POST /api/usuarios/recuperar-password` y `POST /api/usuarios/resetear-password`.

Token opaco de un solo uso, nunca un JWT (ver `PasswordResetService` en el
backend) — estos tests cubren solo el contrato y la navegación del frontend.

---

## Test Case: `RECUPERAR-PASSWORD-E2E-001` - Pedir el enlace

**Priority:** `critical`

**Tags:** @e2e

### Expected Result:
- Siempre el mismo mensaje de éxito (el backend nunca revela si el DNI existe).

---

## Test Case: `RESETEAR-PASSWORD-E2E-001` - Sin token en la URL

**Priority:** `medium`

**Tags:** @e2e

---

## Test Case: `RESETEAR-PASSWORD-E2E-002` - Contraseñas que no coinciden

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** Validación del lado del cliente, sin llamar al backend.

---

## Test Case: `RESETEAR-PASSWORD-E2E-003` - Token válido

**Priority:** `critical`

**Tags:** @e2e

---

## Test Case: `RESETEAR-PASSWORD-E2E-004` - Token inválido o vencido

**Priority:** `high`

**Tags:** @e2e
