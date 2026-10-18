# Chunk M3-C — Cierre de T-M3-06 (kill-switch en el cliente) y habilitación de menores

**Branch:** `chunk/m3-c` · **Bloquea a:** `tinku.menores.sesiones-habilitadas=true` (AGENTS §3)

> **Para el modelo que implementa:** leé este archivo entero antes de tocar código. Después
> trabajá **una spec por vez, en el orden de §3**. Cada spec dice qué leer, qué archivos crear o
> modificar, qué tests escribir primero y cómo verificar. Si algo de una spec choca con el código
> real o con `AGENTS.md`, **pará y preguntá**; no lo resuelvas en silencio (AGENTS, encabezado).

## 1. Qué hay que lograr

El backend del kill-switch está completo y testeado (T-M3-07 a T-M3-11, ADR-M3-02, ADR-M3-03).
Falta todo el cliente: hoy no hay NSFWJS, TensorFlow.js ni MediaRecorder de video en `frontend/`,
así que ninguna clase real dispara el kill-switch. Por eso las clases con menores están apagadas
(T-TES-10).

Al terminar el chunk:
- el navegador de cada participante clasifica el video que recibe;
- dispara el kill-switch;
- sube el clip;
- maneja las dos ramas;
- se protege cuando el clasificador falla.

Las decisiones de producto ya están tomadas y escritas: **no se reabren**.

| Dónde | Qué |
|---|---|
| `docs/adr/ADR-M3-07.md` | Qué se clasifica, umbrales por rama, clip, reintentos, plazo de la rama adultos |
| `docs/Tabla_Tiempos_Tinku.md` | Las 8 filas `ADR-M3-07` (intervalo, confirmación, carga, sin respuesta, reintentos, plazo) |
| `docs/adr/ADR-M3-01.md` | Modelo y framework, más el anexo con la latencia medida |
| `docs/adr/ADR-M3-02.md` | El disparo es una afirmación del cliente; la plata espera a M9 |
| `docs/specs/Spec_M3_Aula_Virtual.md` | US-6, US-6bis, US-7, FR-AULA-003/004/010, casos borde 4 y 8 |

## 2. Lectura obligatoria antes de empezar

1. `AGENTS.md` completo, en especial §1.3, §3, §5 y §7.
2. `docs/adr/ADR-M3-07.md` completo.
3. `docs/specs/Spec_M3_Aula_Virtual.md`: US-6 a US-7, §3 FR-AULA-003 a 010, §5 casos borde.
4. `backend/src/main/java/com/tinku/aula/SesionService.java`, métodos `obtenerToken`,
   `pizarraHabilitada`, `ejecutarKillswitch`, `ramaMenor`, `ramaAdultos`, `confirmarRamaAdultos`,
   `cortar` y `programarSiFalta`.
5. `backend/src/main/java/com/tinku/aula/web/SesionController.java` y `AulaExceptionHandler.java`.
6. `frontend/src/app/aula/[id]/page.tsx` (≈1.200 líneas): `conectar()`, los `room.on(...)` y el
   render de tiles.
7. `frontend/src/lib/grabadorAudioResumen.ts`: es el modelo de estilo para los módulos nuevos.
8. `frontend/tests/aula/pizarra.spec.ts`: modelo de cómo se testea lógica pura con Playwright.

## 3. Specs y orden

_Estado actualizado el 2026-10-18._

| # | Spec | Estado | Tarea |
|---|------|--------|-------|
| 01 | `M3C-01-adr-y-tiempos.md` | **Hecha** (2026-10-14) | T-M3-06a |
| 02 | `M3C-02-backend.md` | **Hecha** (PR #85) | T-M3-06b |
| 03 | `M3C-03-clasificador-cliente.md` | **Hecha** (PR #85) | T-M3-06c |
| 04 | `M3C-04-buffer-evidencia.md` | **Hecha** (PR #85) | T-M3-06d |
| 05 | `M3C-05-integracion-aula.md` | **Hecha** (PR #85) | T-M3-06e |
| 06 | `M3C-06-tests-e2e.md` | **Hecha** (2026-10-16, 9 passed) | T-M3-06f |
| 07 | `M3C-07-benchmark.md` | **Hecha** (2026-10-16; falta opcional la fila de un celular real) | T-M3-06g |
| 08 | `M3C-08-entorno-pruebas.md` | Archivos listos; `tinku.site` hace de entorno de pruebas por pedido del dueño | T-M3-06h |
| 10 | `M3C-10-hallazgos-menores.md` | **Hecha** (2026-10-15, `fix/rem-a`) | T-M3-06j |
| 09 | `M3C-09-habilitar-menores.md` | Lo último; decide el dueño. Estado y pendientes del dueño en la spec (2026-10-18, sin piloto) | T-M3-06i |

Dependencias:
- 02 y 03 son independientes entre sí.
- 04 necesita 03.
- 05 necesita 02, 03 y 04.
- 06 necesita 05.
- 07 necesita 03.
- 08 y 10 se pueden hacer en cualquier momento; 10 es independiente del resto.
- 09 va al final.

## 4. Reglas para todo el chunk

- **Branch:** todo en `chunk/m3-c`. Nunca commits directos a `main`. Nunca `--amend` ni force-push.
- **Commits:**
  - al menos uno por spec, con el mensaje que indica cada spec;
  - si una spec separa implementación y test, son commits separados (AGENTS §5);
  - el test que reproduce el comportamiento faltante va primero y falla (RED), después la
    implementación (GREEN). Dejá en el mensaje del commit la línea del fallo RED.
- **Tiempos:** ningún número de tiempo sale de memoria. Todos están en la Tabla de Tiempos y se leen
  de una constante con un comentario que nombra la fila (`// Tabla de Tiempos: "Intervalo de
  clasificación del kill-switch"`).
- **Migraciones:** la última aplicada es `V46__m4_m5_paquete_mensual.sql`. La nueva es `V47__...`.
  Nunca se edita una migración existente (AGENTS §7).
- **Eventos:** no se crea ningún evento de dominio nuevo y no se cambia el payload de
  `sesion.killswitch_menor` ni `sesion.killswitch_adultos` (AGENTS §4).
- **Video:** nunca se persiste fuera del clip de un disparo. Nada de IndexedDB, `localStorage`, Cache
  API ni disco para frames o buffers (Artículo V).
- **Ante la duda, hay menor.** Si un dato para decidir la rama falta o falla, el cliente actúa como si
  hubiera un menor (AGENTS §3).
- **Verificación antes de cada commit:**
  - Backend, desde `backend/`:
    `JAVA_HOME=<jdk21> ./mvnw -B test`. Leé la línea `Tests run:`: nunca afirmes que pasa por el exit
    code. Baseline: 661 tests, 0 failures, 0 errors, 5 skipped. Cobertura ≥ 85,6 %.
  - Frontend, desde `frontend/`: `npm run lint`, `npm run build` y `npx playwright test tests/aula`.
- **Listas de tareas:** al cerrar cada spec, tildá su tarea en `docs/Tasks_Tinku_Implementacion.md`
  **y** actualizá la línea del Chunk M3-C en `docs/Tasks_Tinku_Chunks.md`, en el mismo commit
  (AGENTS §8). Agregá debajo de la tarea una línea con commit, tests nuevos y la línea `Tests run:`.

## 5. Lo que este chunk NO hace

- Pizarra con menor: sigue apagada (ADR-M3-06, enmienda v2.5).
- Botón de pánico manual: excluido del MVP (`Spec_M3` §6).
- Calibrar falsos positivos (BR-KS-04).
- Los hallazgos de `docs/operacion/HALLAZGOS_pruebas.md` que no figuren en M3C-09.
- Encender la flag de menores: lo hace el dueño (M3C-09).
