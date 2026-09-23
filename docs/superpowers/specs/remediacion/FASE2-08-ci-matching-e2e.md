# FASE2-08 — CI de `matching-service` y verificación del contrato frontend ↔ backend (AUD-031)

**Branch:** `aud/fase2-p1-integridad` · **Riesgo:** bajo · **Finding:** AUD-031 ·
**Bloqueada por:** **P3** (qué tipo de verificación de contrato).

## 1. Problema (verificado al 2026-09-22)

- `.github/workflows/` tiene solo `ci-backend.yml` (paths `backend/**`, `mvn -B verify`) y
  `ci-frontend.yml`. **`matching-service/test_main.py` no corre en ningún lado.**
- Los E2E de Playwright **mockean `/api/**`** con `page.route()`: verifican el frontend contra sus
  propios mocks. **El contrato HTTP entre frontend y backend no se verifica en ninguna capa.**
  Evidencia real en el repo: `frontend/src/lib/api.ts` tenía `"APROBADA"` mientras el enum Java
  serializaba `"APROBADO"` (ver el comentario junto a `EstadoCredencial`), y nadie lo notó hasta una
  revisión manual.

## 2. Parte A — CI de `matching-service` (sin bloqueo)

**Nuevo:** `.github/workflows/ci-matching.yml`, disparado por `paths: ["matching-service/**"]` en
push y pull_request, igual que `ci-backend.yml`. Pasos: checkout, instalar `uv`, `uv sync` (o
`uv pip install -r requirements.txt`), `uv run pytest -q`, `uv run ruff check .` y
`uv run ruff format --check .`. `test_main.py` ya usa embedder y repositorio falsos: **no** necesita
bajar el modelo ni una base real. Verificalo corriendo exactamente esos comandos en local antes de
commitear.

## 3. Parte B — contrato (PARAR: P3)

**Recomendación (P3): tests de contrato por fixtures**, baratos y en cada PR:

1. **Backend**, test nuevo `contrato/ContratoApiTest.java`: arma instancias representativas de los
   DTOs de respuesta que consume el frontend (empezá por `ReservaResponse`, `CredencialResponse`,
   `CredencialColaResponse`, `SesionResponse`, `TokenSesionResponse`, `TutorPerfilResponse`,
   `UsuarioResponse`, `TarifaTutorResponse`, y **todos los enums que viajan en JSON**), las
   serializa con el `ObjectMapper` de Spring y compara contra fixtures commiteados en
   `contracts/*.json` en la raíz del repo. Si no coinciden → falla con un mensaje que diga "cambió
   el contrato: actualizá `contracts/` y los tipos de `frontend/src/lib/api.ts` en el mismo PR".
2. **Frontend**, `frontend/src/contracts.check.ts` (no en `tests/`, ver A9): importa cada fixture
   (`resolveJsonModule`) y lo asigna con `satisfies` al tipo correspondiente de `api.ts`. Lo verifica
   `tsc --noEmit` en `ci-frontend.yml`. Un enum que no coincide rompe el typecheck.
3. Los enums van como **uniones literales** en `api.ts` (ya es el estilo actual): con `satisfies`,
   `"APROBADA"` contra `"APROBADO"` no compila.

Si el usuario elige en P3 el **smoke E2E real** además: workflow **nocturno** (`schedule`) que
levanta `docker compose`, siembra datos (`scripts/seed-*.sh`) y recorre registro → login → buscar →
reservar → pagar en Modo Bypass. Nunca bloquea PRs.

## 4. Tests / verificación

- Parte A: el workflow corre verde en el PR (pegá el link o la salida del job).
- Parte B, RED: cambiá temporalmente un valor en un fixture y mostrá que el test del backend y el
  typecheck del frontend fallan; después revertí. Pegá las dos salidas.

## 5. Criterios de aceptación

- `REGISTRO_FINDINGS.md` AUD-031 → `CERRADO` (si P3 elige solo contrato, dejá anotado que el smoke
  E2E real quedó fuera por decisión, con referencia a P3).
- README raíz, sección CI: los tres workflows.

## 6. NO tocar

- `frontend/tests/` (A9). Los E2E con mocks siguen siendo útiles para UI; no se reemplazan.
