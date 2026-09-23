# T03 — CAP para tutores de menores: frontend y moderación

**Branch:** `tesis/cap-menores` (sub-branch `tesis/cap-frontend`) · **Riesgo:** medio ·
**Bloqueada por:** T02

## 1. Contexto

`ebf6cc0` también quitó 89 líneas de `frontend/src/app/registro/tutor/page.tsx` y cambió
`cuenta/page.tsx`. Recuperar desde `git show ebf6cc0^:<ruta>` y adaptar a la semántica nueva:
el CAP es **opcional en el registro** y solo necesario para enseñar a menores. Coordinar con
`docs/superpowers/specs/ux/06-tutor.md` y `08-admin.md` para no pisar el rediseño.

## 2. Implementación

1. **Registro y panel del Tutor:** opción "Quiero dar clases a menores". Si la marca, se muestra
   la carga del CAP con instrucciones (se tramita en argentina.gob.ar / Mi Argentina, PDF) y el
   estado: pendiente, aprobado (con fecha de vencimiento), rechazado, en revisión legal o vencido.
   Aviso 30 días antes del vencimiento, si la Tabla de Tiempos tiene esa fila (A3; si no, **PARAR**).
2. **Perfil público del Tutor:** indicador "Habilitado para clases con menores", visible para
   Adultos Responsables. **No** mostrar el documento ni el estado de revisión a terceros.
3. **Adulto Responsable:** al autorizar un Tutor para un Menor, si el Tutor no está habilitado,
   mensaje claro con el motivo (el 409 de T02), sin exponer datos del CAP.
4. **Panel de administración — Moderación y Seguridad:** cola de CAP pendientes con el visor del
   PDF (endpoint de T02), acciones aprobar / rechazar (BR-CAP-01) / enviar a revisión legal
   (BR-CAP-02) y registro de auditoría (ya existe el interceptor de M8).

## 3. Tests

- Playwright: registro de Tutor con y sin la opción de menores; cola de moderación con aprobación.
  Los E2E hoy simulan la API (AUD-031): si FASE2-08 ya está mergeada, usar su estrategia de
  contratos.

## 4. Criterios de aceptación

- Un Tutor que no marca la opción completa el registro **igual que hoy** (sin fricción nueva).
