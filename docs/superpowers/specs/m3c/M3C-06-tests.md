# M3C-06 — Tests de punta a punta del kill-switch

**Branch:** `chunk/m3-c` · **Riesgo:** medio · **Bloqueada por:** M3C-02 a M3C-05

## 1. Problema

AGENTS §5 pide un test de integración de punta a punta por historia del Spec activo, y que los casos
borde de `Spec_M3` §5 sean tests. El backend del kill-switch los tiene, pero ninguno empieza en el
cliente: nadie probó que una detección en el navegador termine en una Alerta, un corte y un clip.

Las specs 02–05 traen sus propios tests unitarios y de integración. Esta agrega los de punta a punta
y deja registro de qué cubre cada uno.

## 2. Implementación

1. **Gancho de prueba del clasificador.** Para forzar una detección sin contenido sensible:
   - `src/lib/killswitch/clasificador.ts` acepta un `Modelo` inyectable (ya lo pide M3C-02).
   - En los tests de Playwright se reemplaza por uno que devuelve `Porn = 0,99` a partir de un
     momento dado.
   - **Nunca** un parámetro de URL ni una variable `NEXT_PUBLIC_*` que exista en el bundle de
     producción. El reemplazo se hace con `page.addInitScript` sobre una propiedad que solo lee el
     modo test, o con un build de test separado. Aunque forzar un disparo no da más poder que un
     `curl` (ADR-M3-02), no se deja una puerta así en producción.
   - Nunca se usan imágenes sensibles en el repo ni en los tests.
2. **`frontend/tests/aula/killswitch.spec.ts`** (API mockeada, igual que `pizarra.spec.ts`): reúne
   los casos de frontend de M3C-03, M3C-04 y M3C-05 en un solo archivo, uno por historia.
3. **E2E contra un backend real** (en el entorno de M3C-08, o con `docker compose` local y
   `TINKU_MENORES_SESIONES_HABILITADAS=true` solo en ese entorno): `frontend/tests/aula/killswitch.e2e.spec.ts`,
   **excluido del pipeline normal** (necesita backend, LiveKit y el bypass). Corre a mano con
   `PLAYWRIGHT_BASE_URL` apuntando al entorno de pruebas.
   - Dos contextos de navegador con `--use-fake-device-for-media-stream` (Tutor y Estudiante o Menor).
   - Reserva pagada en modo bypass → los dos entran → detección forzada.
   - Se verifica contra la API con el JWT de admin de Moderación: la Alerta existe, tiene clip, y la
     reserva quedó con el escrow `pausado_alerta`.
4. **Tabla de trazabilidad** en `Spec_M3` (o en el commit de cierre): historia o caso borde → test.

## 3. Casos obligatorios

| Historia / caso | Test |
|---|---|
| US-6 rama menor (Tutor detectado) | E2E: corte para los dos, Alerta `rama=menor` con clip, escrow `pausado_alerta`, aviso al AR |
| US-6 rama menor (menor detectado) | E2E: mismo corte; el Tutor no queda suspendido (riesgo aceptado en Spec_M3) |
| US-6bis (clasificador no carga, con menor) | Playwright: no se pide token |
| US-6bis (clasificador cae a mitad, con menor) | Playwright + E2E: desconexión y `/clasificador/caida` |
| US-6bis (adultos) | Playwright: entra igual y registra |
| US-7 "No" | E2E: la sesión sigue en curso; sin Alerta ni clip |
| US-7 "Sí" | E2E: corte, Alerta `rama=adultos` con clip |
| US-7 sin respuesta (D5) | Backend: vence y se resuelve como "No" |
| Caso borde 4 | E2E con menor: el disparo desde cualquiera de los dos lados corta |
| Caso borde 8 | Playwright: los dos casos de US-6bis con menor |
| T-M3-11 (cliente forzando la rama) | Backend existente, sigue verde |

## 4. Criterios de aceptación

- Suite del backend con JDK 21: `Tests run:` ≥ baseline 661 más los nuevos, 0 failures, 0 errors
  (AGENTS §5). Cobertura de líneas ≥ 85,6 %.
- `npx playwright test tests/aula` verde.
- E2E corrido una vez en el entorno de pruebas, con la salida pegada en el commit o en el PR.

## 5. NO tocar

- Los tests existentes del kill-switch: se agregan casos, no se reescriben.
