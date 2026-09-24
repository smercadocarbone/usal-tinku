import sys
WT="/Users/santiagomercadocarbone/Documents/Develops/usal/tinku-wt"
base = """Sos un agente de desarrollo trabajando SIN supervisión humana en tiempo real, dentro del worktree {dir} (rama `{branch}`). No hay nadie para responder preguntas.

TAREA: implementar `{spec}`.

Antes de tocar código, leé completos, en este orden: `AGENTS.md`; `docs/superpowers/specs/remediacion/00-LEEME-opencode.md`;{extra} `docs/superpowers/specs/ORDEN-GENERAL.md`; y la spec.

REGLAS DE ESTA CORRIDA (si chocan con la mecánica de la spec, ganan estas):
1. Trabajá SOLO dentro de {dir}. Commiteá en la rama actual (`{branch}`). NUNCA hagas push, merge, rebase, ni checkout de otra rama. No toques `main`.
2. Hay otros agentes trabajando en paralelo en otros worktrees. Números reservados para vos: migraciones de Flyway: {mig}. ADR: {adr}. No uses ningún otro número. Si necesitás una migración o un ADR sin número asignado, PARÁ.
3. Protocolo: test RED primero (guardá la salida del fallo para el reporte), fix mínimo, y suite completa con: `cd backend && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./mvnw -B -o test` (con `-o`, offline: la conexión a internet es mala y todas las dependencias ya están en ~/.m2). Baseline de hoy: 455 tests, 0 failures. N nunca puede bajar de 455.
4. NUNCA preguntes. Si llegás a un PARAR de la spec, a una decisión pendiente (tabla de decisiones del LEEME en `_pendiente_`), o a algo que no cierra: NO improvises. Dejá commiteado hasta el último punto verde, escribí el motivo en el reporte y terminá.
5. No instales dependencias nuevas ni descargues nada (npm, pip, uv sync, maven online). {extra_regla}
6. No modifiques nada bajo `docs/superpowers/specs/`. Sí actualizá `docs/auditoria/REGISTRO_FINDINGS.md`, `docs/Tasks_Tinku_Implementacion.md` y `docs/Tasks_Tinku_Chunks.md` (juntos) y los Specs de módulo como pide el protocolo.
7. Commits en conventional commits, en castellano, SIN `Co-Authored-By` ni ninguna atribución de IA. Agregá archivos por nombre: nunca `git add -A` ni `git add .`.
8. Al terminar, tu ÚLTIMO mensaje es el reporte (formato §6 del LEEME): commits (hash + mensaje), línea `Tests run:` final, el RED observado, y todo PARAR con su motivo."""

tareas = {
 "tesis-ux-recomendaciones": dict(branch="tesis/ux-recomendaciones", spec="docs/superpowers/specs/tesis/T12-ux-recomendaciones.md",
   mig="ninguna", adr="ninguno", extra=" `docs/superpowers/specs/tesis/00-LEEME-tesis.md`; `docs/superpowers/specs/ux/00-LEEME-ux.md`;",
   extra_regla="Esta tarea es solo de frontend: si no tocás backend, NO corras la suite de Maven (el punto 3 no aplica); validá con `cd frontend && bun run lint && npx tsc --noEmit -p .` y reportá esa salida en lugar de `Tests run:`. node_modules ya está enlazado: no corras bun install. No corras Playwright (necesita el stack levantado). Si la spec te obliga a tocar backend, entonces sí aplica el punto 3 completo."),
 "tesis-instrumentacion": dict(branch="tesis/instrumentacion-piloto", spec="docs/superpowers/specs/tesis/T11-instrumentacion-piloto.md",
   mig="V29, V30 y V31 (en ese orden, solo esas)", adr="ninguno", extra=" `docs/superpowers/specs/tesis/00-LEEME-tesis.md`;",
   extra_regla="Si un test falla por timeout de conexión a la base o a Testcontainers (hay varios agentes en paralelo), re-corré la suite completa UNA vez antes de concluir que algo está roto. Si tocás frontend (panel de M8, encuesta NPS), validá además con `cd frontend && bun run lint && npx tsc --noEmit -p .` (node_modules ya está enlazado: no corras bun install; no corras Playwright)."),
 "fase2-evidencia-upload": dict(branch="aud/fase2-evidencia-upload", spec="docs/superpowers/specs/remediacion/FASE2-09-evidencia-upload.md",
   mig="V28 (solo esa, si la necesitás)", adr="ninguno", extra="",
   extra_regla="La retención del clip (§3 de la spec) exige verificar BR-KS-02 antes de agregar la fila a la Tabla de Tiempos: si no lo fija explícitamente, PARÁ."),
 "fase2-desconexion": dict(branch="aud/fase2-desconexion", spec="docs/superpowers/specs/remediacion/FASE2-05-webhook-desconexion.md",
   mig="V26 (solo esa)", adr="ninguno", extra="", extra_regla=""),
 "fase2-baja-menor": dict(branch="aud/fase2-baja-menor", spec="docs/superpowers/specs/remediacion/FASE2-06-baja-menor-anonimizacion.md",
   mig="V27 (solo esa)", adr="ADR-M1-05", extra="", extra_regla=""),
 "fase2-matching-auth": dict(branch="aud/fase2-matching-auth", spec="docs/superpowers/specs/remediacion/FASE2-04-matching-auth-pool.md",
   mig="ninguna", adr="ADR-M2-02 (solo si elegís agregar una dependencia)", extra="",
   extra_regla="En §3 de la spec elegí la opción SIN dependencia nueva (reutilizar una conexión por request y agrupar /recompute-embeddings en una transacción): no hay internet para instalar psycopg_pool. Los tests de Python se corren con `cd matching-service && .venv/bin/python -m pytest -q` (el .venv ya existe; ruff no está instalado: no lo instales y dejalo anotado en el reporte). La precarga del embedder (B13) ya está en main: el lock de carga perezosa puede ya no hacer falta; verificalo y explicalo."),
 "tesis-proveedor-llm": dict(branch="tesis/proveedor-llm", spec="docs/superpowers/specs/tesis/T07-proveedor-llm.md",
   mig="ninguna", adr="ADR-M6-03",
   extra=" `docs/superpowers/specs/tesis/00-LEEME-tesis.md`;",
   extra_regla="Todo el cliente de Gemini se testea contra un servidor HTTP simulado (MockRestServiceServer o HttpServer local): ningún test llama a Google ni necesita una API key real."),
}
d=sys.argv[1]; t=tareas[d]
print(base.format(dir=f"{WT}/{d}", **t))
