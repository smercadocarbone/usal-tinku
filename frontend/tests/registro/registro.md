### E2E Tests: Registro de Usuario adulto

**Suite ID:** `REGISTRO-E2E`
**Feature:** Wizard de 4 pasos (rol → datos → verificación de DNI → credenciales), M1/US-1.

---

## Test Case: `REGISTRO-E2E-001` - Alta feliz de adulto

**Priority:** `critical`

**Tags:** @e2e, @registro

**Description/Objective:** Un adulto completa los 4 pasos y termina autenticable.

**Preconditions:**
- `/api/usuarios/verificar-dni` y `/api/usuarios/registro` mockeados en éxito.

### Flow Steps:
1. Elegir "Soy mayor de edad".
2. Completar nombre/apellido/DNI/fecha de nacimiento.
3. Subir foto de DNI y verificar.
4. Completar email/contraseña, aceptar T&C, crear cuenta.

### Expected Result:
- Redirige a `/login?registrado=1`.

---

## Test Case: `REGISTRO-E2E-002` - Corte por menor de edad (Artículo II)

**Priority:** `critical`

**Tags:** @e2e, @registro, @seguridad-menor

**Description/Objective:** Verificar DNI que resulta menor de edad no crea cuenta.

**Preconditions:**
- `/api/usuarios/verificar-dni` mockeado con 403 (menor).

### Flow Steps:
1. Elegir "Soy mayor de edad", completar datos con una fecha de nacimiento de menor.
2. Subir foto de DNI y verificar.

### Expected Result:
- Mensaje "Sos menor de edad." visible.
- Sigue en `/registro` — nunca avanza al paso de credenciales ni redirige a `/login`.

### Notes:
- El texto exacto lo pone el backend (`err.message`); acá solo se afirma el
  encabezado fijo del componente ("Sos menor de edad.").
