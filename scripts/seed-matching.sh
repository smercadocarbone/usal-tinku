#!/usr/bin/env bash
# Completa el pipeline de matching para los tutores del seed: aprueba la
# credencial, activa matching y persiste embeddings reales (pgvector) con el
# modelo del contenedor matching. Idempotente.
# Uso: scripts/seed-matching.sh
set -euo pipefail

COMPOSE="${COMPOSE:-docker compose}"

echo "==> Aprobando credencial y activando matching (SQL sobre la BD de dev)"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -v ON_ERROR_STOP=1 <<'SQL'
UPDATE identidad.credenciales_academicas
   SET estado = 'APROBADO', revisado_at = now()
 WHERE estado = 'PENDIENTE'
   AND tutor_id IN (SELECT id FROM identidad.usuarios WHERE dni IN ('30224455','30224456'));

UPDATE identidad.usuarios SET activo_para_matching = true
 WHERE dni IN ('30224455','30224456');
SQL

echo "==> Computando embeddings (modelo del contenedor matching) y persistiendo en pgvector"
$COMPOSE exec -T matching python - <<'PY'
import os
import psycopg
from pgvector import Vector
from pgvector.psycopg import register_vector
from sentence_transformers import SentenceTransformer

conn = psycopg.connect(
    host=os.environ["TINKU_PG_HOST"],
    port=int(os.environ.get("TINKU_PG_PORT", "5432")),
    dbname=os.environ["TINKU_PG_DBNAME"],
    user=os.environ["TINKU_PG_USER"],
    password=os.environ["TINKU_PG_PASSWORD"],
)
register_vector(conn)

MODELO = "paraphrase-multilingual-MiniLM-L12-v2"

# Perfil de prueba de cada tutor seed: materias del catalogo V7 + texto que
# se va a embedder. En produccion esto vendra del alta de materias/nivel (M2).
TUTORES = {
    "30224455": {  # Jorge Martinez: profesor de matematica de secundario
        "materias": ["secundario|Matemática", "secundario|Física", "secundario|Química"],
        "texto": "Profesor particular de matemática, física y química para nivel secundario.",
    },
    "30224456": {  # Maria Fernandez: programacion universitaria
        "materias": ["universitario|Programación", "universitario|Estructura de Datos", "universitario|Cálculo"],
        "texto": "Tutora de programación, estructura de datos y cálculo para nivel universitario.",
    },
}

with conn.cursor() as cur:
    modelo = SentenceTransformer(MODELO)
    for dni, perfil in TUTORES.items():
        cur.execute("SELECT u.id FROM identidad.usuarios u WHERE u.dni = %s", (dni,))
        tutor_id = cur.fetchone()[0]
        if not tutor_id:
            print(f"  SKIP {dni}: no existe"); continue
        cur.execute("SELECT id FROM matching.materias_niveles WHERE nivel || '|' || materia = ANY(%s)", (perfil["materias"],))
        ids = [r[0] for r in cur.fetchall()]
        embedding = modelo.encode(perfil["texto"]).tolist()
        cur.execute(
            """
            INSERT INTO matching.perfiles_tutor_matching (tutor_id, materias_niveles_ids, embedding, activo_para_matching)
            VALUES (%s, %s, %s, true)
            ON CONFLICT (tutor_id) DO UPDATE
               SET materias_niveles_ids = EXCLUDED.materias_niveles_ids,
                   embedding = EXCLUDED.embedding,
                   activo_para_matching = EXCLUDED.activo_para_matching
            """,
            (tutor_id, ids, Vector(embedding)),
        )
        print(f"  upsert {dni}: {len(ids)} materias, embedding 384d")
    conn.commit()
conn.close()
PY

echo "==> Estado final"
$COMPOSE exec -T db psql -U tinku_dev -d tinku -c \
  "SELECT u.dni, u.activo_para_matching, p.materias_niveles_ids IS NOT NULL AS tiene_materias, p.embedding IS NOT NULL AS tiene_embedding
     FROM identidad.usuarios u
     LEFT JOIN matching.perfiles_tutor_matching p ON p.tutor_id = u.id
    WHERE u.tipo = 'TUTOR';"