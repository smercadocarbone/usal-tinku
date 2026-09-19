### E2E Tests: Búsqueda de tutores

**Suite ID:** `BUSCAR-E2E`
**Feature:** `/buscar` — búsqueda por texto libre contra M2, FR-MATCH-005.

---

## Test Case: `BUSCAR-E2E-001` - Resultado autorizado con datos de perfil

**Priority:** `critical`

**Tags:** @e2e, @busqueda

**Description/Objective:** Un texto libre devuelve resultados con nombre, materia y precio del perfil público.

### Flow Steps:
1. Escribir un texto libre en el buscador y enviar.

### Expected Result:
- Se ve la tarjeta con el nombre completo del Tutor y el enlace "Ver perfil".

---

## Test Case: `BUSCAR-E2E-002` - Resultado no autorizado (menor sin autorización)

**Priority:** `high`

**Tags:** @e2e, @busqueda, @seguridad-menor

**Description/Objective:** Un resultado `noAutorizado: true` (FR-MATCH-005) nunca expone
un camino directo de contacto — solo el botón de solicitar autorización.

### Flow Steps:
1. Buscar un texto que devuelve un resultado `noAutorizado: true`.

### Expected Result:
- Aparece el botón "Solicitar autorización" en la tarjeta del resultado.

### Notes:
- No verifica el llamado a un endpoint de solicitud: ese flujo todavía no
  está conectado del lado del frontend (ver auditoría de `/buscar`).
