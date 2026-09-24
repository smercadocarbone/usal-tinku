### E2E Tests: Reserva de una clase

**Suite ID:** `RESERVAR-E2E`
**Feature:** `/reservar` — reserva directa contra franjas publicadas, M4 FR-RES-001.

---

## Test Case: `RESERVAR-E2E-001` - Reserva directa feliz

**Priority:** `critical`

**Tags:** @e2e, @reserva

**Description/Objective:** Un adulto elige una franja puntual publicada, confirma y
la reserva creada lo manda a pagar.

### Flow Steps:
1. Entrar a `/reservar?tutor=t-1` con una franja puntual publicada.
2. Elegir la franja y un horario dentro de ella.
3. Confirmar "Reservar y pagar".

### Expected Result:
- Redirige a `/pagar?reserva=r-1`.

---

## Test Case: `RESERVAR-E2E-002` - Un Menor no puede reservar directo (Artículo II)

**Priority:** `critical`

**Tags:** @e2e, @reserva, @seguridad-menor

**Description/Objective:** La Constitución (Art. II) prohíbe que un Menor pague o
gestione la reserva por sí mismo — debe hacerlo el Adulto Responsable.

**Preconditions:**
- JWT de sesión con `tipo: "MENOR"` en el payload.

### Flow Steps:
1. Entrar a `/reservar?tutor=t-1` autenticado como Menor.

### Expected Result:
- Se ve el aviso "Podés pedir esta clase, pero la confirma tu Adulto Responsable."
  con un botón "Copiar este mensaje" (B5: el aviso tiene una acción concreta, no
  es un callejón sin salida).
- Copiar el mensaje pega en el portapapeles un texto listo para enviarle al AR
  que menciona el nombre del tutor.
- El botón "Reservar y pagar" no existe en el DOM (no solo está oculto por CSS).
