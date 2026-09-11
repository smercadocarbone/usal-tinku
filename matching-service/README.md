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

- `GET /health` — comunicación interna backend Java → Python (T-000-08).
- `POST /match` — ranking por similitud semántica (T-M2-04). Body:
  `{"texto_busqueda": "...", "tutor_ids_candidatos": ["<uuid>", ...]}`.
  Devuelve `[{"tutor_id": ..., "score": ...}]` ordenado por score descendente.
  Los candidatos sin embedding todavía no se rankean. Si el modelo o la base
  no están disponibles responde `503` — nunca fabrica un ranking falso.

## Cómo correr localmente

```bash
# Instalación y venv con uv (Rust): órdenes de magnitud más rápido que pip.
uv venv
uv pip install -r requirements.txt
source .venv/bin/activate
# Config de la base pgvector (misma base que el backend):
export TINKU_PG_HOST=localhost TINKU_PG_PORT=5432 \
       TINKU_PG_DBNAME=tinku TINKU_PG_USER=... TINKU_PG_PASSWORD=...
uvicorn main:app --reload --port 8000
```

El modelo `paraphrase-multilingual-MiniLM-L12-v2` (384 dims, español incluido)
se descarga la primera vez y se carga de forma perezosa en el primer `/match`.

## Lint y formato

```bash
ruff check . && ruff format . --check
```

## Verificar

```bash
curl http://localhost:8000/health
curl -X POST localhost:8000/match -H 'Content-Type: application/json' \
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