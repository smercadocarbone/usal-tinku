### E2E Tests: Mis denuncias recibidas y alertas de seguridad

**Suite ID:** `SEGURIDAD-E2E`
**Feature:** `/cuenta/seguridad` — habilitada por `GET /api/denuncias/recibidas`,
`GET /api/alertas-seguridad/mias` y los `POST .../descargo` (M9).

El backend ya soportaba el descargo (derecho a réplica) pero no existía
ningún endpoint ni pantalla para que el propio denunciado/detectado se
enterara de que un caso existía — el plazo corría en silencio.

---

## Test Case: `SEGURIDAD-E2E-001` - Sin casos, avisa en vez de listas vacías

**Priority:** `medium`

**Tags:** @e2e

---

## Test Case: `SEGURIDAD-E2E-002` - Denuncia sin descargo, enviarlo confirma

**Priority:** `critical`

**Tags:** @e2e, @seguridad-menor

---

## Test Case: `SEGURIDAD-E2E-003` - Denuncia con descargo ya enviado, no reofrece el formulario

**Priority:** `medium`

**Tags:** @e2e

---

## Test Case: `SEGURIDAD-E2E-004` - Alerta de kill-switch ofrece dar la versión del tutor

**Priority:** `medium`

**Tags:** @e2e, @seguridad-menor
