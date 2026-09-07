# ADR-M2-01 — Ubicación del índice de embeddings: pgvector en PostgreSQL

## Estado
Aceptado (Chunk M2-A). Decisión confirmada con el desarrollador antes de
implementar (Plan M2, sección 5): el índice semántico vive en **pgvector**,
la extensión de PostgreSQL, consultada por el servicio Python. Es la fila
"índice semántico (pgvector vs. en memoria)" del Registro de Decisiones
Técnicas de la Constitución.

## Contexto
M2 (Motor de Matching Semántico) necesita calcular similitud semántica entre
el texto de una búsqueda y los perfiles de Tutor. El embedding de cada Tutor
(`perfiles_tutor_matching.embedding_vector` / `embedding`) debe guardarse en
algún lado. Dos opciones posibles:

1. **En memoria del proceso Python** (índice FAISS + snapshot a disco).
2. **En `pgvector` dentro de PostgreSQL**, consultado por el servicio Python.

La extensión del Tutor vive en Postgres; la pregunta es dónde persiste el
vector de 384 dimensiones (dimensionalidad de los modelos
sentence-transformers del Plan, sección 3).

## Decisión
**pgvector**: la columna `embedding` es de tipo `vector(384)` dentro de la
tabla `matching.perfiles_tutor_matching`. El servicio Python calcula el
embedding del perfil y lo **persiste en Postgres**; al buscar, y para el
universo completo (sin restricción de autorización), ejecuta la búsqueda de
similitud (kNN) contra la columna.

Motivos:
- **Simplifica la operación (Artículo I, VII):** no hay que reconstruir el
  índice al reiniciar el proceso Python — el índice ES la columna de la base.
  Es el único dato persistente que el motor de matching necesita; persistirlo
  con el resto de los datos del monolito evita un segundo mecanismo de
  snapshot/copia que sí o sí habría que operar en la opción en memoria.
- **Escala del piloto:** primero se acota la lista de candidatos en Java
  (autorización, suspendidos) y recién después se busca por similitud sobre
  ese subconjunto — el volumen por búsqueda es chico, y la búsqueda kNN de
  pgvector es más que suficiente (Plan M2, sección 5).
- **Menos código en Python:** el proceso separado (única excepción al
  Artículo VIII) se queda en lo mínimo: calcular embeddings y consultar
  similitud — sin un índice en memoria propio que mantener.

## Alternativas descartadas / consideradas
- **En memoria (FAISS + snapshot):** evita round-trips extra a la base y no
  requiere la extensión, pero agrega un mecanismo de persistencia/reindexación
  al reiniciar el proceso, más código de estado en el servicio Python. A escala
  piloto la latencia extra de un query a Postgres es irrelevante frente a la
  simplicidad operativa de pgvector. Descartado por simplicidad (Artículo VII).

## Implementación
- **Migración** `V7__m2_matching.sql`: `CREATE EXTENSION vector` (en el schema
  `public`, default del `search_path`, para que el tipo `vector` siempre
  resuelva) + columna `embedding VECTOR(384)` en `perfiles_tutor_matching`.
- **Imagen de infraestructura:** el entorno donde corra Postgres debe incluir
  pgvector (p. ej. imagen oficial `pgvector/pgvector:pg16`).
- **Tests con Testcontainers:** todos los tests que levantan el contexto (7
  clases) usan la imagen `pgvector/pgvector:pg16` para que Flyway aplique V7.
- **Servicio Python (`/match`):** consulta la columna `embedding`;
  **T-M2-04** implementará el cálculo real.

## Consecuencias
- PostgreSQL de despliegue debe tener pgvector habilitado (extensión + imagen o
  paquete adecuado) — nuevo requisito de infraestructura, alineado con el
  presupuesto USD 0-100/mes (pgvector es open source, sin costo de licencia).
- La búsqueda por similitud directa sobre el universo completo sólo ocurre para
  adultos sin restricción; para menores la lista llega acotada de Java.
- El proceso Python no mantiene estado de índice propio (stateless respecto a
  persistencia), en línea con el diseño de Plan M2 sección 1.

## Registro de Decisiones Técnicas (Constitución)
Actualiza la fila "índice semántico (pgvector vs. en memoria)": **pgvector** —
decidida vía este ADR el 2026-09-04, confirmada con el desarrollador.