### E2E Tests: Cuenta — baja de menor

**Suite ID:** `CUENTA-E2E`
**Feature:** `/cuenta` (PanelAdulto) — baja de cualquier menor a cargo usando el
listado real de `GET /api/usuarios/menores` (antes solo se podía dar de baja al
menor recién dado de alta en la misma sesión del navegador).

---

## Test Case: `CUENTA-E2E-001` - Listado real de menores para dar de baja

**Priority:** `critical`

**Tags:** @e2e

### Flow Steps:
1. Entrar a `/cuenta` como Adulto Responsable con un menor ya dado de alta.

### Expected Result:
- El `<select>` de "Menor" muestra el menor real.
- El cartel viejo ("el listado... pendiente en backend") no aparece más.

---

## Test Case: `CUENTA-E2E-002` - Sin menores, aviso en vez de selector vacío

**Priority:** `medium`

**Tags:** @e2e

### Flow Steps:
1. Entrar a `/cuenta` como Adulto Responsable sin menores dados de alta.

### Expected Result:
- Se ve "No tenés menores a cargo todavía." y no hay `<select>` en el DOM.

---

## Test Case: `CUENTA-E2E-003` - Baja con reservas futuras exige confirmación

**Priority:** `high`

**Tags:** @e2e, @seguridad-menor

**Description/Objective:** Dar de baja a un menor con reservas futuras (409/422 del
backend) no cancela nada en silencio — pide una confirmación explícita.

### Flow Steps:
1. "Dar de baja" sobre un menor cuyo DELETE sin confirmar devuelve 409.

### Expected Result:
- Aparece la advertencia de reservas futuras y el botón "Confirmar baja".
