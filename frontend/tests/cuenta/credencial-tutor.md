### E2E Tests: Estado real de la credencial del Tutor

**Suite ID:** `CREDENCIAL-TUTOR-E2E`
**Feature:** `/cuenta` (rol Tutor) — banner de credencial, habilitado por
`GET /api/tutores/me/credencial` y `POST /api/tutores/credenciales`.

Antes de este cambio el panel mostraba siempre el mismo texto fijo de "en
revisión", sin importar si el Tutor había cargado alguna vez una credencial,
y no existía ninguna pantalla para cargarla.

---

## Test Case: `CREDENCIAL-TUTOR-E2E-001` - Sin credencial cargada, ofrece el formulario

**Priority:** `critical`

**Tags:** @e2e

### Expected Result:
- El cartel fijo viejo ("Tus credenciales estan en revision") no aparece más.
- Se ofrece el formulario de carga (tipo de documento + archivo).

---

## Test Case: `CREDENCIAL-TUTOR-E2E-002` - PENDIENTE, no ofrece el formulario

**Priority:** `high`

**Tags:** @e2e

---

## Test Case: `CREDENCIAL-TUTOR-E2E-003` - APROBADA

**Priority:** `medium`

**Tags:** @e2e

---

## Test Case: `CREDENCIAL-TUTOR-E2E-004` - RECHAZADA, permite volver a cargar

**Priority:** `high`

**Tags:** @e2e
