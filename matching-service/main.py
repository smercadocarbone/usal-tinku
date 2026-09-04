"""
Servicio de Motor de Matching (M2) — Tinku.

Unica excepcion al monolito modular (Constitucion, Articulo VIII): vive
como proceso Python separado exclusivamente porque el backend Java no tiene
un ecosistema maduro de sentence-transformers. NUNCA debe adquirir logica de
negocio de otros modulos (autorizacion, suspensiones, reputacion) — esa
logica vive en el backend Java, que llama a este servicio solo con un texto
de busqueda y una lista ya acotada de tutor_ids candidatos.

Indice de embeddings: pgvector (ADR-M2-01) — la columna `embedding` vive en
`matching.perfiles_tutor_matching`, no hay indice en memoria que mantener ni
snapshot que reconstruir al reiniciar el proceso (Plan_M2_Motor_Matching.md,
secciones 1 y 3).

Endpoints:
- GET  /health   (T-000-08): comunicacion interna backend Java -> Python.
- POST /match    (T-M2-04): ranking por similitud semantica sobre la lista
  de candidatos que llega YA acotada por el backend Java.
"""

import os
from typing import Callable, Iterable

import psycopg
from fastapi import FastAPI
from pydantic import BaseModel
from pgvector import Vector
from pgvector.psycopg import register_vector

app = FastAPI(title="tinku-matching-service", version="0.2.0")

# Modelo multilingual (espanol incluido) de 384 dims — coincide con la
# columna `embedding VECTOR(384)` de la migracion V7 (ADR-M2-01).
MODELO = "paraphrase-multilingual-MiniLM-L12-v2"

# Se cargan de forma perezosa la primera vez que se usan; los tests
# inyectan falsos en estas pruebas (misma filosofia que los componentes
# aislados del backend Java: esto nunca falsifica el calculo real en produccion,
# solo permite testear la logica de ranking/contrato sin bajar el modelo).
_embedder: Callable[[str], list[float]] | None = None


class MatchRequest(BaseModel):
    texto_busqueda: str
    tutor_ids_candidatos: list[str]  # lista YA acotada por el backend Java (autorizacion, suspensiones)


class MatchResult(BaseModel):
    tutor_id: str
    score: float


class RepoScores:
    """Lee los embeddings de pgvector y calcula la similitud con la consulta.

    Esta clase NO conoce reglas de negocio: recibe la lista de candidatos ya
    acotada y devuelve solo el ranking por similitud de esos candidatos.
    """

    def obtener_scores(self, tutor_ids: Iterable[str], consulta_embedding: list[float]) -> list[tuple[str, float]]:
        conn = psycopg.connect(
            host=os.environ["TINKU_PG_HOST"],
            port=int(os.environ.get("TINKU_PG_PORT", "5432")),
            dbname=os.environ["TINKU_PG_DBNAME"],
            user=os.environ["TINKU_PG_USER"],
            password=os.environ["TINKU_PG_PASSWORD"],
        )
        try:
            register_vector(conn)
            ids = list(tutor_ids)
            if not ids:
                return []
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT tutor_id::text,
                           1 - (embedding <=> %s::vector) AS score
                      FROM matching.perfiles_tutor_matching
                     WHERE tutor_id = ANY(%s::uuid[])
                       AND embedding IS NOT NULL
                     ORDER BY embedding <=> %s::vector
                    """,
                    (Vector(consulta_embedding), ids, Vector(consulta_embedding)),
                )
                return [(str(tid), float(score)) for tid, score in cur.fetchall()]
        finally:
            conn.close()


_repo: RepoScores = RepoScores()


def _cargar_embedder() -> Callable[[str], list[float]]:
    """Carga el modelo sentence-transformers una sola vez (lazy)."""
    global _embedder
    if _embedder is None:
        from sentence_transformers import SentenceTransformer  # import tardio: pesado

        modelo = SentenceTransformer(MODELO)
        _embedder = lambda texto: modelo.encode(texto).tolist()  # noqa: E731
    return _embedder


class MatchError(RuntimeError):
    """El servicio no puede computar el ranking (modelo o base no disponibles)."""


def _embed(texto: str) -> list[float]:
    try:
        return _cargar_embedder()(texto)
    except Exception as exc:  # noqa: BLE001 — el fallo de carga se expone como 503
        raise MatchError(f"modelo de embeddings no disponible: {exc}") from exc


def _scores(tutor_ids: Iterable[str], consulta_embedding: list[float]) -> list[tuple[str, float]]:
    try:
        return _repo.obtener_scores(tutor_ids, consulta_embedding)
    except Exception as exc:  # noqa: BLE001 — config/base caida se expone como 503
        raise MatchError(f"base de pgvector no disponible: {exc}") from exc


@app.get("/health")
def health():
    """Usado por T-000-08 para verificar la comunicacion interna desde el
    backend Java antes de implementar cualquier logica de matching real."""
    return {"status": "ok", "service": "tinku-matching-service"}


@app.post("/match", response_model=list[MatchResult])
def match(request: MatchRequest) -> list[MatchResult]:
    """
    Ranking por similitud semantica (T-M2-04, ADR-M2-01).

    IMPORTANTE: este endpoint NUNCA consulta autorizacion, suspensiones de M9
    ni reputacion de M7 — esas exclusiones ya deben venir resueltas en
    `tutor_ids_candidatos` antes de llegar aca (Plan tecnico de M2, seccion 3,
    pasos 2-4). Los tutores de la lista que no tengan embedding aun no se
    rankean (no aparecen en el resultado).

    Si el modelo o la base no estan disponibles se responde 503 de forma
    explicita — el servicio nunca fabrica un ranking falso.
    """
    if not request.tutor_ids_candidatos:
        return []
    try:
        consulta_embedding = _embed(request.texto_busqueda)
        ranked = _scores(request.tutor_ids_candidatos, consulta_embedding)
    except MatchError as exc:
        raise Unavailable(str(exc)) from exc
    return [MatchResult(tutor_id=tid, score=score) for tid, score in ranked]


class Unavailable(Exception):
    """Respuesta 503: el ranking real no se puede computar en este momento."""


@app.exception_handler(Unavailable)
def _unavailable_handler(_request, exc: Unavailable):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=503, content={"detail": str(exc)})