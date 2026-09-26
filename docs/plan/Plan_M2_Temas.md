# Plan Técnico M2-F — Catálogo granular de temas + búsqueda por nombre

**Basado en:** Spec_M2 (US-5 catálogo cerrado, US-1/2 búsqueda, FR-MATCH-006), Plan_M2,
ADR-M2-01 (pgvector).
**Estado:** Contratos cerrados por el orquestador (no los rediseñan los subagentes).
**Rama:** `chunk/m2-f-temas`. **Chunk:** M2-F (ver Tasks_Tinku_Chunks.md).

Este plan define las interfaces fijas entre las 4 áreas (migración/backend/python/frontend)
y las decisiones de diseño del orquestador. Los subagentes implementan ESTO; no lo rediseñan.

---

## 1. Modelo de catálogo (decisión cerrada)

Jerarquía: **nivel → curso/año (o carrera) → materia → TEMA**.

- **TEMA es la unidad atómica** y lleva una `descripcion` en texto de "qué se toca".
  El texto de las descripciones ES la fuente del embedding del Tutor: buscar
  "cómo dividir" hace match semántico con el tema "División", no con "Matemática 4°".
- PRIMARIO: años 1°–6° (NAP). SECUNDARIO: años 1°–6°, ciclo básico + orientado.
  UNIVERSITARIO: carreras de grado → materias del ciclo básico (1°–2° año) → temas.
- Reemplaza el uso de `materias_niveles` / `materias_niveles_ids` por código nuevo.
  Las tablas viejas quedan en la BD sin uso nuevo; **V7 no se toca** (AGENTS §7).

> ⚠️ **Corrección de fuente (hecha por el orquestador tras verificación):** la referencia
> "Res. CFE 371/23" del encargo NO existe (corresponde a res. 371/20, protocolo ETP de
> COVID-19). No es citable. Las fuentes reales del secundario son: **Res. CFE 84/09**
> (estructura ciclo básico + orientado), **Res. CFE 93/09** (Régimen Académico), la serie
> **NAP** del Ministerio de Educación (CFCyE 247/05 y 249/05 ciclo básico; CFE 135/11,
> 141/11, 180/12, 181/12, 182/12, 191/12) para los contenidos por área y año, y los
> **diseños curriculares provinciales** (referencia: PBA, secundaria 1°–6°; CABA NES
> 5°, Res. 321-MEGC/15) para la grilla real de materias por año. La Argentina es federal:
> 6 años (PBA, mayoría) o 5 años (CABA). El seed usa PBA 1°–6° como grilla de referencia
> y los NAP como núcleo común, citando ambos. Detalle en la entrega de S1.

## 2. Contrato 2a — Esquema (migración V12, ya con skeleton en el repo)

```sql
CREATE TABLE matching.trayectos (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nivel          TEXT NOT NULL CHECK (nivel IN ('primario','secundario','universitario')),
    anio_o_carrera TEXT NOT NULL,          -- '4°' (texto) o 'Ingeniería'
    materia        TEXT NOT NULL,
    CONSTRAINT uq_trayecto UNIQUE (nivel, anio_o_carrera, materia)
);

CREATE TABLE matching.temas (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trayecto_id UUID NOT NULL REFERENCES matching.trayectos(id),
    nombre      TEXT NOT NULL,
    descripcion TEXT NOT NULL,             -- 'qué se toca'
    orden       INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tema UNIQUE (trayecto_id, nombre)
);

ALTER TABLE matching.perfiles_tutor_matching
    ADD COLUMN tema_ids UUID[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_perfiles_tutor_matching_tema_ids
    ON matching.perfiles_tutor_matching USING GIN (tema_ids);
```

Decisiones de orquestador:
- **GIN sobre `tema_ids` SÍ** (contrato 2a lo condicionaba a datos): el seed objetivo es
  ~1.400 temas y el filtro por nombre/materia recorre la pertenencia de candidatos a temas
  por búsqueda; el índice es una línea y barato. Las queries de filtro usan
  `t.id = ANY(ptm.tema_ids)`, soportado por el GIN.
- **El seed NO va en V12** (el encargo deja el esqueleto sin seed). Va en **V20__m2_temas_seed.sql** (el esqueleto quedó en **V19__m2_temas.sql**, renumerados del V12/V13 originales por colisión con V12/V13 de main tras el merge).
  (generado desde los datos de S1 en FASE 3). Regla del repo: nunca se edita una migración
  aplicada; V12/V13 se crean sin aplicarse en este branch, en orden.
- El seed usa `INSERT ... SELECT` resolviendo `trayecto_id` por la UNIQUE
  `(nivel, anio_o_carrera, materia)`, con `VALUES` por fila y `orden` secuencial por trayecto.
- `CREATE EXTENSION IF NOT EXISTS unaccent SCHEMA public` va en la migración que aplique el
  seed (V13) o en V12 (a elección del backend, siempre antes del primer uso). El tipo/función
  resuelve por search_path a `public` igual que `vector` (patrón de V7).

## 3. Contrato 2b — API backend (JSON)

### GET /api/catalogos  — árbol por rama (autenticado, JWT)
```
[{nivel:"primario", cursos:[{nombre:"4°", materias:["nombre":"Matemática",
   temas:[{id:<uuid>, nombre, descripcion}]}]}]}]
```
- Filtros opcionales que combinan con AND: `?nivel=&curso=&materia=` (valores EXACTOS de
  `nivel`, `anio_o_carrera`, `materia`). Sin filtros → árbol completo.
- `temas` ordenados por `orden`, materias/cursos por nombre. `id` es el UUID de `temas`.
- El árbol completo (3 ramas) es la carga base del perfil Tutor; el frontend puede pedir
  por rama para autocomplete.

### GET /api/tutores/me/temas  → `{"tema_ids":[...]}`
- Cualquier perfil autenticado; devuelve los propios. Sin fila en `perfiles_tutor_matching`
  → `{"tema_ids":[]}`.

### PUT /api/tutores/me/temas  → body `{"tema_ids":["<uuid>",...]}`
- SOLO perfil `TUTOR` (403 si no). Solo persiste ids: guardado del array `tema_ids`,
  **sin tocar `embedding`** (se repopula vía recompute de 2c, no en este PUT).
- Upsert de la fila `perfiles_tutor_matching` (puede no existir): `INSERT ... ON CONFLICT
  (tutor_id) DO UPDATE SET tema_ids = EXCLUDED.tema_ids`. `activo_para_matching` no se toca.
- Validación: si algún id no pertenece al catálogo vigente → **404** (ids desconocidos);
  body malformado / fuera de rango / único → **422**. Lista vacía es válida (limpia temas).

### POST /api/busquedas  — body actualizado, respuesta sin cambios
Body: `{"texto_busqueda"?, "nombre"?, "filtro_materia"?}`
- **Al menos un campo no-vacío**; si los tres vienen vacíos → **422** (hoy exige
  `texto_busqueda`; pasa a ser opcional si hay `nombre` o `filtro_materia`). `nombre`
  (≈200) y `filtro_materia` (≈100) también acotados.
- Respuesta NO cambia: `[{tutor_id, score, no_autorizado}]` (mismo shape `BusquedaResponse`).
- **Orden de operaciones del orquestador** (no cambia el flujo existente, solo lo extiende):
  1. `MatchingContextoService.resolverContexto` + candidatos (autorización del menor,
     activos para matching, FR-MATCH-004/007) — **en Java, siempre primero**.
  2. **Nuevo:** si hay `nombre` y/o `filtro_materia`, se acotan los candidatos EN JAVA con
     una native query que cruza `perfiles_tutor_matching.tema_ids` ↔ `temas`:
       - `filtro_materia` → `trayectos.materia = :materia` (exacto).
       - `nombre` → `public.unaccent(t.nombre) ILIKE '%' || public.unaccent(:nombre) || '%'`
         (parcial + tolerante a tildes + case-insensitive). Decisión: **unaccent + ILIKE**,
         no pg_trgm (catálogo cerrado ~1.400 filas, búsqueda de substring: un barrido con
         unaccent es microsegundos; pg_trgm no aporta similitud fuzzy requerida por el contrato).
  3. Llamada a Python `/match` con el texto efectivo y los candidatos FINALES.
       - texto efectivo = `texto_busqueda` si viene; si no, `nombre`; si no, `filtro_materia`.
       - Si el texto efectivo queda vacío (no puede pasar: validación 2b) → 422.
  4. Marcado `no_autorizado` + ajuste por reputación (sin cambios).
- Guardado de búsqueda (`POST /api/busquedas/guardadas`): sigue almacenando el **texto
  efectivo** (columna `texto_busqueda` NOT NULL). Para búsquedas solo-por-nombre se
  persiste el nombre como texto; re-ejecutar reproduce la intención semántica sin el
  acotamiento por nombre (documentado, limitación aceptada en este chunk).

## 4. Contrato 2c — matching-service (Python)

> **Enmendado por ADR-M2-03 (2026-09-26):** el recompute además embebe cada tema elegido
> (`matching.temas.embedding`, V42) y `/match` puntúa a cada Tutor por su tema más parecido;
> el embedding del perfil queda como respaldo. El contrato de `/match` no cambia.

- **`/match` NO cambia** (contrato interno snake_case `MatchRequest`/`MatchResult` intacto).
- **Nuevo endpoint `POST /recompute-embeddings`** → `{"actualizados": <n>}`:
  - Lee de Postgres (`matching.perfiles_tutor_matching` + `matching.temas` +
    `matching.trayectos`), como `RepoScores` con psycopg (+ `register_vector` no aplica:
    escribe vectores, sí requiere `Vector`).
  - Para cada perfil con `tema_ids <> '{}'`: texto fuente = por cada tema elegido
    `"{nombre}: {descripcion}"`, concatenados con `". "`. Embedding con el MISMO modelo
    `paraphrase-multilingual-MiniLM-L12-v2` (384 dims, lazy load).
  - Escribe `embedding = <vector>` en `perfiles_tutor_matching` por tutor; si `tema_ids`
    vacío → `embedding = NULL` (borra el vector viejo).
  - **Idempotente** (mismo estado → mismo resultado, vuelve a grabar el mismo vector),
    **sin reglas de negocio**: no lee autorización, ni reputación, ni `activo_para_matching`.
    Solo toma los `tema_ids` ya validados en Java y actualiza el embedding. Mismo espíritu
    que `/health`. `MatchError` → 503 si modelo/base caídos.
  - Env vars de BD: las mismas `TINKU_PG_*` ya usadas por `RepoScores`.
- Tests (pytest, `test_main.py` ampliado): inyectar embedder falso y repo fake igual que hoy.

## 5. Contrato 2d — Frontend (Next.js)

- **Perfil Tutor** (sección en `/cuenta`): árbol colapsable nivel → curso/carrera → materia
  → temas; cada tema muestra su descripción (inline/tooltip). Al seleccionar → `PUT
  /api/tutores/me/temas`; al volver → `GET /api/tutores/me/temas` (precarga). Solo visible
  para un perfil `TUTOR`.
- **Página de búsqueda (nueva, `/busqueda`)**: input de texto libre + input de nombre +
  selectores de rama del catálogo (nivel/curso/materia, data de `GET /api/catalogos`).
  Resultados en cards y si `no_autorizado:true` en un perfil de menor → botón
  **"Solicitar autorización"** (notificación al AR — no hay endpoint de solicitud en este
  chunk; el botón muestra un aviso local sin llamar a la API, marcado TODO).
- **Decisión de orquestador (cerrada en FASE 0):** la respuesta de `POST /api/busquedas`
  NO cambia (`tutor_id, score, no_autorizado`) — el card muestra `tutor_id` (etiqueta
  "Tutor #…"). Mostrar "materias/temas que cubre" requeriría un endpoint de perfil público
  de Tutor que no existe (queda diferido, señalado en el resumen — no se inventa un endpoint
  nuevo en este chunk).
- S4 puede mockear `GET /api/catalogos` localmente mientras el backend no aterrice; la
  integración real la hace el orquestador en FASE 3.
- `middleware.ts` protege `/busqueda` y `/cuenta` (mismo patrón actual).

## 6. Fases de trabajo

| Fase | Quién | Entrega |
|---|---|---|
| 0 | orquestador | este doc + V12 skeleton + tasks; commit `chunk/m2-f-temas` |
| 1 (paralelo) | S1 datos · S2 backend · S3 python | S1: CSV/JSON por rama (nivel, anio_o_carrera, materia, tema, descripcion) + fuentes + ranking de prioridad. S2: endpoints 2b + tests. S3: recompute 2c + tests. |
| 2 (paralelo) | S4 frontend | UI 2d contra contratos 2b (mock local permitido) |
| 3 | orquestador | seed S1 → V13; conciliar contratos reales; `make test` + build frontend; sin `--amend` |
| 4 | orquestador | E2E: tutor con tema "División" rankea en "cómo dividir"; nombre parcial; menor `no_autorizado`; ADR si hace falta |

## 7. Trazabilidad y casos borde obligatorios

- US-5 (catálogo cerrado granular): solo se puede elegir temas del catálogo vigente
  (404/422 si no) — FR-MATCH-006.
- US-2 (menor): filtros de seguridad se mantienen; con nombre/materia los candidatos del
  menor siguen siendo solo los autorizados y el marcado `no_autorizado` no cambia
  (FR-MATCH-004/005/007 intactos, tests de M2 existentes siguen verdes).
- Casos borde a cubrir en tests de integración: PUT /temas de un no-Tutor → 403; tema
  inexistente → 404; búsqueda sin ningún filtro → 422; búsqueda solo-por-nombre → es válida
  y acota candidatos; nombre con tilde matchea "División" sin acento y viceversa; menor con
  autorizados + filtro por nombre → solo sus autorizados.