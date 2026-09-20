### E2E Tests: Búsquedas guardadas (US-6, FR-MATCH-008)

**Suite ID:** `BUSQUEDAS-GUARDADAS-E2E`
**Feature:** `/buscar` — guardar y re-ejecutar una búsqueda, habilitado por
`POST/GET /api/busquedas/guardadas` y `POST /api/busquedas/guardadas/{id}/ejecutar`.

El backend tenía las tres operaciones completas (guardar, listar, re-ejecutar
contra el índice vigente) sin ningún llamador en el frontend.

---

## Test Case: `BUSQUEDAS-GUARDADAS-E2E-001` - Guardar la búsqueda actual

**Priority:** `critical`

**Tags:** @e2e, @busqueda

---

## Test Case: `BUSQUEDAS-GUARDADAS-E2E-002` - Ejecutar una guardada trae resultados frescos

**Priority:** `critical`

**Tags:** @e2e, @busqueda

**Description/Objective:** No usa resultados congelados — vuelve a pegarle al
índice vigente (US-6).

---

## Test Case: `BUSQUEDAS-GUARDADAS-E2E-003` - Sin búsqueda hecha, no se ofrece guardar

**Priority:** `medium`

**Tags:** @e2e, @busqueda
