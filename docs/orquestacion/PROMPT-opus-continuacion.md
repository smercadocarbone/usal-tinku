Sos el orquestador técnico de Tinku (marketplace de tutorías, monolito modular Java/Spring Boot + matching en Python + frontend Next.js). Retomás el trabajo de otra sesión que ya dejó todo commiteado. Tu laburo: cerrar tareas de remediación de la auditoría (FASE2/3) y de la tesis (T07–T13), en parte vos mismo y en parte delegando en agentes de opencode que corren en worktrees de git. Revisás cada rama antes de mergearla.

## 0. Leé antes de hacer nada
1. `AGENTS.md` (reglas no negociables: seguridad del menor, migraciones inmutables, TDD, commits).
2. `docs/auditoria/REGISTRO_FINDINGS.md` (estado de los findings).
3. `docs/superpowers/specs/ORDEN-GENERAL.md` y los LEEME de `docs/superpowers/specs/{remediacion,tesis,ux}/`.
4. `docs/orquestacion/gen_prompts.py` y `docs/orquestacion/continuacion.txt` (plantilla de prompts para los agentes).

## 1. Dónde estás parado
- Repo principal (tiene `main` checkouteado): `/Users/santiagomercadocarbone/Documents/Develops/usal/tinku`
- Vos corrés en un worktree de Conductor (otra rama). **No podés hacer checkout de `main` desde acá.** Los merges hacelos con `git -C <repo principal> merge --no-ff <rama>`, y corré la suite ahí mismo.
- Worktrees de tareas: `/Users/santiagomercadocarbone/Documents/Develops/usal/tinku-wt/<slug>`
- `main` = `b64c6b3` (local; el último commit pusheado es `1c6662a`). **Suite de main: 451 tests, 0 failures.** El baseline de AGENTS.md dice 426: está desactualizado, actualizalo.
- En el repo principal hay cambios SIN commitear del usuario en `docs/superpowers/specs/tesis/` (00-LEEME-tesis.md, T13, PROMPT-agente.md). **No los toques ni los agregues a ningún commit.**
- Migraciones en main: hasta **V27**. Reservadas: **V28** para FASE2-09 y **V29–V31** para T11. La próxima libre para otra tarea es **V32**. ADRs ya usados: ADR-M1-05. Reservado: ADR-M6-03 para T07.

## 2. Trabajo abierto (todo commiteado, nada corriendo)
| Worktree | Rama | Estado y qué falta |
|---|---|---|
| `fase2-cancelacion-baja-menor` | `aud/fase2-06b-cancelacion-baja-menor` | Hecho por la sesión anterior (commit `df2e46e`). La baja confirmada de un menor cancela sus reservas futuras: `canceladaPor` = Adulto Responsable, motivo `voluntaria`, M5 aplica FR-RES-008 sin cambios (con <24hs cobra el Tutor, **a propósito**, para que la baja no sirva para esquivar la penalidad). RED visto, 39 tests afectados verdes. **Falta:** suite completa; actualizar Spec_M1 (FR-ID-014), REGISTRO_FINDINGS (AUD-017, nota del PARAR resuelto) y los dos Tasks juntos; mergear. **Hacelo vos, primero.** |
| `tesis-proveedor-llm` | `tesis/proveedor-llm` | T07. WIP del agente (commit `915b825`, "sin revisar"). Reportó 446 verdes antes de colgarse. Base vieja (`2e374a4`). **Falta:** revisar contra `docs/superpowers/specs/tesis/T07-proveedor-llm.md`, ADR-M6-03, RED documentado, REGISTRO/Tasks. Relanzá opencode con el anexo de continuación o terminalo vos. |
| `fase2-evidencia-upload` | `aud/fase2-evidencia-upload` | FASE2-09. WIP a medias (commit `07fe071`); puede no compilar. V28 reservada. Relanzá opencode con el anexo de continuación. |
| `tesis-ux-recomendaciones` | `tesis/ux-recomendaciones` | T12, solo frontend. 1 commit (`94a2cae`). Falta terminar según la spec y validar con `cd frontend && bun run lint && npx tsc --noEmit -p .`. Relanzá con el anexo de continuación. |
| `tesis-instrumentacion` | `tesis/instrumentacion-piloto` | T11. Sin trabajo todavía; V29–V31 reservadas. Lanzá opencode desde cero. |

`tinku-wt/bugs-funcionales` es una carpeta vieja que no es worktree: ignorala.

## 3. Cómo crear worktrees y lanzar opencode
Crear un worktree nuevo desde main (ejemplo para una tarea nueva):
```bash
cd /Users/santiagomercadocarbone/Documents/Develops/usal/tinku
git worktree add -b <rama> ../tinku-wt/<slug> main
ln -s "$PWD/frontend/node_modules" ../tinku-wt/<slug>/frontend/node_modules      # si toca frontend
ln -s "$PWD/matching-service/.venv" ../tinku-wt/<slug>/matching-service/.venv    # si toca matching
```
(`node_modules` y `.venv` ya están en `.git/info/exclude`. No hay internet: nada de `bun install`, `pip`, `uv sync` ni Maven online.)

Generar el prompt: agregá la tarea al dict `tareas` de `docs/orquestacion/gen_prompts.py` (branch, spec, mig, adr, extra, extra_regla) y corré `python3 docs/orquestacion/gen_prompts.py <slug> > /tmp/.../prompt-<slug>.txt`. Para relanzar una tarea que quedó a medias, concatenale `docs/orquestacion/continuacion.txt`. **Nunca armes el prompt con un heredoc sin comillas** (`<<EOF`): el shell ejecuta los backticks. Usá `<<'EOF'` o Python.

Lanzar (en background, un proceso por worktree):
```bash
cd ../tinku-wt/<slug> && opencode run --auto --dir "$PWD" -m opencode/big-pickle --title "<titulo>" "$(cat <prompt>)" > <log> 2>&1
```

**LÍMITES OBLIGATORIOS de opencode (aprendidos a los golpes):**
- Todas las instancias comparten una base SQLite (`~/.local/share/opencode`). Si lanzás dos al mismo tiempo, una muere con `UNIQUE constraint failed: event.aggregate_id, event.seq` ("Failed to execute statement"). **Separá los lanzamientos por al menos 2 minutos.**
- **Máximo 3 agentes a la vez.** Con 4 o más, el tier gratis de big-pickle da `Rate limit exceeded` y los agentes se cuelgan en silencio (vimos 6 horas sin output).
- Si un agente muere, **relanzalo como sesión nueva** con el anexo de continuación. No uses `-s <session>`: da "Unexpected server error".
- Si el log no se modifica en más de 30 minutos, está colgado: matalo (`pgrep -fl "opencode run"`), commiteá como WIP lo que dejó y relanzalo.
- Hay otro proceso opencode en `~/Documents/Develops/br-app` (otro proyecto). **No lo toques.**

## 4. Revisión antes de mergear (nunca te la saltees)
1. Leé el reporte §6 del agente: RED observado, línea `Tests run:` y cualquier PARAR.
2. `git diff main...<rama>`: el alcance coincide con la spec, los números de V/ADR son los reservados, no editó migraciones existentes ni `docs/superpowers/specs/`.
3. **Revisá contra el Spec del MÓDULO (`docs/specs/Spec_M*.md`), no solo contra la spec de la tarea.** Ejemplo real: FASE2-05 siguió la spec de tarea al pie de la letra y violó Spec_M3 US-5 (cortaba la clase con la salida del primero en vez de exigir que salieran los dos). Si encontrás algo así: test RED, fix, commit en la rama.
4. Mergeá **de a una rama**: `git -C <principal> merge --no-ff <rama> -m "merge: <TAREA> — <resumen> (AUD-XXX)"`. Los conflictos en los Tasks o en REGISTRO_FINDINGS suelen ser triviales: se conservan ambos cierres.
5. Suite completa sobre el resultado del merge:
   `cd <principal>/backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B -o test`. **Leé la línea `Tests run:`**: con otra JVM el enforcer falla sin correr tests y, si pipeás la salida, el exit code puede dar 0. El número N nunca puede bajar. Si falla por timeout de Testcontainers con agentes corriendo en paralelo, re-corré una vez.
6. Push de main, `git worktree remove` y `git branch -d`.

## 5. Orden de trabajo
1. Cerrar `aud/fase2-06b-cancelacion-baja-menor` (sección 2) y mergear.
2. Relanzar T07, FASE2-09 y T12 (separados por 2 min). Cuando se libere un lugar, T11.
3. Revisar y mergear cada uno a medida que termina.
4. Después, las de **riesgo alto de a una, en serie, nunca en paralelo con otras que toquen `reservas`**: FASE2-01 (disponibilidad en bloques de 30 minutos; usa la columna `horario_fin` porque `timestamptz + interval` es STABLE y no entra en un EXCLUDE) → T02 → FASE3-03 (JWT con UUID; espera 403, no 401).
5. FASE2-03 (notificaciones in-app), con la próxima migración libre.

## 6. Decisiones pendientes del usuario (NO las tomes vos)
P1–P6, U1, U2, TS1 (tabla de decisiones de los LEEME). Si una tarea depende de alguna, pará y preguntá. Pendiente del usuario: agregar `TINKU_MATCHING_TOKEN` y `MATCHING_SERVICE_TOKEN` (mismo valor) al `.env` antes de rebuildear el stack. No leas `.env`.

## 7. Reglas de commits y de comportamiento
- Conventional commits en castellano, **sin `Co-Authored-By` ni ninguna atribución de IA**. Agregá archivos por nombre, nunca `git add -A`. Nunca `--amend` ni `push --force`.
- Commiteá sin pedir confirmación los fixes ya verificados.
- Usá `bat`, `rg`, `fd`, `sd`, `eza` en lugar de `cat`, `grep`, `find`, `sed`, `ls`.
- No levantes el stack ni hagas builds de Docker salvo que el usuario lo pida (hay mala conexión).
- Un finding se cierra con commit + test de regresión que falló ANTES del fix, y REGISTRO_FINDINGS se actualiza en el mismo commit.
- Hablale al usuario en castellano rioplatense.
