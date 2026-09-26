# tinku-matching-service

Único proceso separado del monolito (Constitución, Artículo VIII). Ver
`Plan_M2_Motor_Matching.md` antes de tocar este servicio — en particular
**ADR-M2-01 (resuelto): el índice de embeddings vive en `pgvector`** dentro
de PostgreSQL; este proceso solo computa embeddings y consulta la columna
`embedding` (`VECTOR(384)`) de `matching.perfiles_tutor_matching`.

## Regla de oro de este servicio

**Nunca debe conocer reglas de negocio de otros módulos** (autorización de
Tutores, suspensiones de M9, reputación de M7). El backend Java resuelve
todo eso antes de llamar acá — este servicio solo recibe un texto de
búsqueda y una lista ya acotada de `tutor_ids` candidatos, y devuelve un
ranking de similitud semántica. Si en algún momento este servicio necesita
saber "¿está este tutor suspendido?", es una señal de que algo se diseñó
mal — esa pregunta se responde en el backend Java, antes de la llamada.

## Endpoints

- `GET /health` — comunicación interna backend Java → Python (T-000-08). **Público** (lo usan el compose y el panel de salud de M8).
- `POST /match` — ranking por similitud semántica (T-M2-04). Body:
  `{"texto_busqueda": "...", "tutor_ids_candidatos": ["<uuid>", ...]}`.
  Devuelve `[{"tutor_id": ..., "score": ...}]` ordenado por score descendente.
  Los candidatos sin embedding todavía no se rankean. Si el modelo o la base
  no están disponibles responde `503` — nunca fabrica un ranking falso.
- `POST /recompute-embeddings` — repuebla `embedding` de todos los perfiles
  (contrato 2c). Todos los vectores se embeddean primero y se persisten en una
  sola transacción (AUD-015): un fallo a mitad revierte todo.
- `POST /sugerir-temas` — asistente de "Mis materias" (FR-MATCH-010). Body:
  `{"texto": "...", "temas": [{"id": "...", "texto": "nombre: descripción"}], "limite": 8}`.
  Devuelve `[{"id": ..., "score": ...}]` por similitud. El catálogo llega ya filtrado
  desde Java; los embeddings de los temas se cachean en memoria por texto.

## Autenticación (AUD-015)

`/match`, `/recompute-embeddings` y `/sugerir-temas` exigen el header `X-Matching-Token` con un
token compartido entre el backend y este servicio — **el mismo valor** en
`TINKU_MATCHING_TOKEN` (servicio) y `MATCHING_SERVICE_TOKEN` (backend). Nada de
usuarios ni JWT (Constitución, Artículo VII).

- Sin el header, o con un valor distinto → `401`.
- Si `TINKU_MATCHING_TOKEN` está vacío en el servicio → **fail-closed**: todos
  los endpoints responden `503` excepto `/health`. Nunca "sin token = abierto".

## Cómo correr localmente

```bash
# Instalación y venv con uv (Rust): órdenes de magnitud más rápido que pip.
uv venv
uv pip install -r requirements.txt
source .venv/bin/activate
# Config de la base pgvector (misma base que el backend):
export TINKU_PG_HOST=localhost TINKU_PG_PORT=5432 \
       TINKU_PG_DBNAME=tinku TINKU_PG_USER=... TINKU_PG_PASSWORD=...
# Token compartido (mismo valor que MATCHING_SERVICE_TOKEN del backend):
export TINKU_MATCHING_TOKEN=... 
uvicorn main:app --reload --port 8000
```

El modelo `paraphrase-multilingual-MiniLM-L12-v2` (384 dims, español incluido)
se descarga la primera vez y se precarga en el arranque del servicio (B13) —
si la precarga falla (sin red a HuggingFace), la primera request lo reintenta
con la carga lazy sin tumbar el servicio.

## Lint y formato

```bash
ruff check . && ruff format . --check
```

## Verificar

```bash
curl http://localhost:8000/health                      # 200 (público)
curl http://localhost:8000/match                       # 401 sin token
curl -X POST localhost:8000/match -H "X-Matching-Token: $TINKU_MATCHING_TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"texto_busqueda":"algebra lineal","tutor_ids_candidatos":["<uuid>"]}'
```

## Tests

```bash
uv pip install -r requirements.txt
python -m pytest test_main.py -q
```

Los tests inyectan un embedder y un repo falsos (contrato + ranking); el
cómputo real se verificó end-to-end contra un contenedor `pgvector/pgvector:pg16`
con embeddings reales del modelo (ocurrencia "algebra" / "literatura" para
tutores de matemática y de literatura).