### E2E Tests: Panel de Administración — Tickets de soporte

**Suite ID:** `ADMIN-TICKETS-E2E`
**Feature:** `/admin` (tab "Tickets de soporte") — cambio de estado, habilitado
por `PATCH /api/admin/tickets/{id}`.

El backend ya tenía el endpoint de transición de estado
(`abierto → en_proceso → resuelto → cerrado`, agregado en una auditoría
anterior) pero el panel seguía siendo de solo lectura: nunca lo llamaba.

---

## Test Case: `ADMIN-TICKETS-E2E-001` - Cambiar el estado de un ticket

**Priority:** `critical`

**Tags:** @e2e

---

## Test Case: `ADMIN-TICKETS-E2E-002` - Un error no hace desaparecer el ticket

**Priority:** `medium`

**Tags:** @e2e
