### E2E Tests: Perfil de Tutor — denuncia y autorización

**Suite ID:** `TUTORES-E2E`
**Feature:** `/tutores/[id]` — denuncia de perfil (M9, US-1) y autorización de Tutor
para un menor (M1, FR-ID-009).

---

## Test Case: `TUTORES-E2E-001` - Un adulto denuncia el perfil de un tutor

**Priority:** `critical`

**Tags:** @e2e, @seguridad-menor

**Description/Objective:** `FormularioDenuncia` con `sesionId` ausente (denuncia de
perfil, no de una clase puntual) llega a `POST /api/denuncias` y confirma.

### Flow Steps:
1. Entrar al perfil, abrir "Denunciar", elegir un motivo y enviar.

### Expected Result:
- Aparece "Denuncia registrada."

---

## Test Case: `TUTORES-E2E-002` - Un Menor no ve el botón de denunciar (Artículo II)

**Priority:** `critical`

**Tags:** @e2e, @seguridad-menor

**Description/Objective:** El backend ya rechaza con 403 una Denuncia de un Menor
(FR-SEC-001) — el frontend además no muestra el botón, para no ofrecer un camino que
va a fallar.

### Flow Steps:
1. Entrar al perfil con una sesión de Menor.

### Expected Result:
- Se ve el aviso de pedirle al Adulto Responsable.
- El botón "Denunciar" no existe en el DOM (no solo oculto por CSS).

---

## Test Case: `TUTORES-E2E-003` - Un Adulto Responsable autoriza al tutor

**Priority:** `critical`

**Tags:** @e2e

**Description/Objective:** Con el listado real de menores (`GET /api/usuarios/menores`,
antes ausente), un AR puede autorizar un Tutor para un menor concreto.

### Flow Steps:
1. Entrar al perfil con una sesión con `cap_ar: true`.
2. Elegir el menor en el `<select>` y confirmar "Autorizar para este menor".

### Expected Result:
- Aparece un mensaje de éxito con el nombre del menor autorizado.

---

## Test Case: `TUTORES-E2E-004` - Sin menores a cargo, no se ofrece autorizar

**Priority:** `medium`

**Tags:** @e2e

**Description/Objective:** Un AR sin menores dados de alta ve un aviso claro en vez
de un `<select>` vacío o un botón que fallaría.

### Flow Steps:
1. Entrar al perfil con `cap_ar: true` y `GET /api/usuarios/menores` devolviendo `[]`.

### Expected Result:
- Se ve el aviso de que todavía no hay menores dados de alta.
- El `<select>` de autorización no existe en el DOM.
