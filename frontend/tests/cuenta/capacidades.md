### E2E Tests: Capacidades (Estudiante / Adulto Responsable)

**Suite ID:** `CAPACIDADES-E2E`
**Feature:** `/cuenta` — sección "Editar cuenta" > "Capacidades", habilitada
por `PATCH /api/usuarios/me/capacidades`.

El backend ya soportaba activar/desactivar capacidades desde el alta, pero
no había ningún llamador en el frontend: un Adulto que se registró solo
como Estudiante no tenía forma de convertirse en Adulto Responsable más
adelante (para poder dar de alta un menor) sin pedirlo a soporte.

---

## Test Case: `CAPACIDADES-E2E-001` - Activar Adulto Responsable

**Priority:** `critical`

**Tags:** @e2e

---

## Test Case: `CAPACIDADES-E2E-002` - No se puede desactivar con menores a cargo

**Priority:** `high`

**Tags:** @e2e, @seguridad-menor

**Description/Objective:** FR-ID-016 — el backend responde 409 y el mensaje
se muestra tal cual.

---

## Test Case: `CAPACIDADES-E2E-003` - Un Tutor no ve el control

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** El concepto de capacidad Estudiante/Adulto
Responsable es del eje Adulto/Menor — un perfil Tutor no lo usa.
