# FASE3-04 — Frontend honesto: catálogo falso y middleware (AUD-026, AUD-016)

**Branch:** `aud/fase3-p2-calidad` · **Riesgo:** bajo · **Findings:** AUD-026, AUD-016 ·
**Parte B bloqueada por:** **P6**.

Validación en frontend: `cd frontend && bun run lint && npx tsc --noEmit -p .` y los E2E de
Playwright (`bun run test:e2e`). **No** modifiques `frontend/tests/` para que pasen (A9): si un E2E
se rompe, entendé por qué primero.

## A. El catálogo falso que parece real (AUD-026)

**Hoy:** `frontend/src/lib/api.ts`, `getCatalogos`: el `.catch` devuelve `catalogoMock` ante
**cualquier** error que no sea un `ApiError` distinto de 404. Un error de red (`TypeError` de
`fetch`, backend caído) **no** es `ApiError`, así que **con el backend caído el usuario ve un
catálogo inventado que parece real** y puede intentar reservar sobre datos que no existen.
Tiene un `FIXME AUD-026` justo ahí.

**Qué hacer:** el fallback a `catalogoMock` solo en desarrollo: `process.env.NODE_ENV !== "production"`
**y** 404. En producción, cualquier error se propaga y la pantalla muestra su estado de error (que
la spec de UX va a mejorar). Borrá el `FIXME AUD-026` en el mismo commit que lo cierra.
Verificá dónde se usa `getCatalogos` y que cada pantalla tenga un estado de error visible
(aunque sea mínimo: el rediseño es de UX).

## B. El middleware que parece seguridad (AUD-016) — PARAR (P6)

**Hoy:** `frontend/src/middleware.ts` solo verifica que **exista** la cookie `tinku_jwt`; no valida
firma ni expiración. `document.cookie = "tinku_jwt=x"` carga el shell de `/admin`. La autorización
real la hace el backend (todo `/api/admin/**` exige un Admin activo), así que **no hay fuga de
datos**: es un problema de honestidad del código y de UX (se ve un panel vacío con errores).

**Opción recomendada (P6): renombrar y documentar.** El middleware pasa a llamarse lo que es —
redirección de UX para usuarios sin sesión—, con un comentario que diga explícitamente que **no** es
un control de seguridad y dónde está el control real. Bonus barato y útil: decodificar el payload
(sin verificar firma) para redirigir a `/login` si el `exp` ya pasó, en vez de dejar entrar a una
pantalla que va a fallar. Eso mejora la UX sin prometer seguridad.

**Opción alternativa (si P6 elige verificar):** `jose` (dependencia nueva → ADR previo, A5), el
secreto del JWT disponible en el runtime edge del frontend (otra superficie donde vive el secreto:
decilo en el ADR), y verificación de firma y `exp`.

Borrá el `FIXME AUD-016` en el commit que lo cierra.

## Criterios de aceptación

- Lint, typecheck y E2E en verde. `REGISTRO_FINDINGS.md`: AUD-026 y AUD-016 → `CERRADO`.
- Manual (A): con el backend apagado, la pantalla que usa el catálogo muestra un error, no datos
  inventados, en un build de producción (`bun run build && bun run start`).
