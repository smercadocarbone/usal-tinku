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
- GET  /health               (T-000-08): comunicacion interna backend Java -> Python.
- POST /match                (T-M2-04): ranking por similitud semantica sobre la
  lista de candidatos que llega YA acotada por el backend Java.
- POST /recompute-embeddings (M2-F, contrato 2c): repopula `embedding` de TODOS
  los perfiles desde sus `tema_ids` (idempotente, sin reglas de negocio).
"""

import os
from collections.abc import Callable, Iterable

import logging
import psycopg
from fastapi import FastAPI
from pgvector import Vector
from pgvector.psycopg import register_vector
from pydantic import BaseModel

app = FastAPI(title="tinku-matching-service", version="0.2.0")

logger = logging.getLogger("tinku-matching-service")

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
    tutor_ids_candidatos: list[
        str
    ]  # lista YA acotada por el backend Java (autorizacion, suspensiones)


class MatchResult(BaseModel):
    tutor_id: str
    score: float


def _conectar() -> psycopg.Connection:
    """Conexion a Postgres con las mismas TINKU_PG_* usadas por RepoScores."""
    return psycopg.connect(
        host=os.environ["TINKU_PG_HOST"],
        port=int(os.environ.get("TINKU_PG_PORT", "5432")),
        dbname=os.environ["TINKU_PG_DBNAME"],
        user=os.environ["TINKU_PG_USER"],
        password=os.environ["TINKU_PG_PASSWORD"],
    )


class RepoScores:
    """Lee los embeddings de pgvector y calcula la similitud con la consulta.

    Esta clase NO conoce reglas de negocio: recibe la lista de candidatos ya
    acotada y devuelve solo el ranking por similitud de esos candidatos.
    """

    def obtener_scores(
        self, tutor_ids: Iterable[str], consulta_embedding: list[float]
    ) -> list[tuple[str, float]]:
        conn = _conectar()
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


class RecomputeRepo:
    """Lee perfiles con sus temas y persiste el embedding (contrato 2c, M2-F).

    SIN reglas de negocio: consume los `tema_ids` ya validados en Java y solo
    escribe `embedding` (o lo deja NULL) para que /match pueda rankear.
    """

    def perfiles_con_temas(self) -> list[tuple[str, list[tuple[str, str]]]]:
        """Todos los perfiles como [(tutor_id, [(nombre, descripcion), ...])].

        Los temas de cada perfil se resuelven en el orden del array `tema_ids`;
        los ids sin fila en `matching.temas` (fuera de catalogo) se ignoran. Un
        unico query resuelve todos los temas de golpe (`id = ANY(ids)`), sin
        leer filas por tutor.
        """
        conn = _conectar()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    "SELECT tutor_id::text, tema_ids FROM matching.perfiles_tutor_matching"
                )
                perfiles = cur.fetchall()
                ids = {tid for _, tema_ids in perfiles for tid in tema_ids}
                temas = {}
                if ids:
                    cur.execute(
                        "SELECT id, nombre, descripcion FROM matching.temas WHERE id = ANY(%s::uuid[])",
                        (list(ids),),
                    )
                    temas = {
                        tid: (nombre, descripcion)
                        for tid, nombre, descripcion in cur.fetchall()
                    }
            return [
                (tutor_id, [temas[tid] for tid in tema_ids if tid in temas])
                for tutor_id, tema_ids in perfiles
            ]
        finally:
            conn.close()

    def guardar_embedding(self, tutor_id: str, vector: list[float] | None) -> None:
        """Persiste el embedding de 384 dims del tutor (NULL si el perfil no tiene temas)."""
        conn = _conectar()
        try:
            register_vector(conn)
            with conn.cursor() as cur:
                cur.execute(
                    "UPDATE matching.perfiles_tutor_matching SET embedding = %s WHERE tutor_id = %s::uuid",
                    (None if vector is None else Vector(vector), tutor_id),
                )
            conn.commit()
        finally:
            conn.close()


_recompute_repo: RecomputeRepo = RecomputeRepo()


def _cargar_embedder() -> Callable[[str], list[float]]:
    """Carga el modelo sentence-transformers una sola vez (lazy)."""
    global _embedder
    if _embedder is None:
        from sentence_transformers import SentenceTransformer  # import tardio: pesado

        modelo = SentenceTransformer(MODELO)
        _embedder = lambda texto: modelo.encode(texto).tolist()
    return _embedder


@app.on_event("startup")
def _precargar_embedder() -> None:
    """B13: baja el modelo en el arranque, no en la primera búsqueda — la
    primera búsqueda tras levantar el stack tardaba ~34s. Si acá falla (p. ej.
    sin acceso a HuggingFace), no se tira abajo el servicio: la primera request
    reintenta con la carga lazy de `_cargar_embedder`."""
    try:
        _cargar_embedder()
    except Exception as exc:  # noqa: BLE001 — degradar a lazy, no morir
        logger.warning("no se pudo precargar el embedder en el arranque: %s", exc)


class MatchError(RuntimeError):
    """El servicio no puede computar el ranking (modelo o base no disponibles)."""


def texto_fuente(temas: list[tuple[str, str]]) -> str:
    """'{nombre}: {descripcion}' por tema, separados por '. ' (contrato 2c).

    Funcion pura: el texto es la unica fuente del embedding del Tutor, asi la
    busqueda por nombre ("como dividir") matchea el tema, no la materia.
    """
    return ". ".join(f"{nombre}: {descripcion}" for nombre, descripcion in temas)


def _embed(texto: str) -> list[float]:
    try:
        return _cargar_embedder()(texto)
    except Exception as exc:
        raise MatchError(f"modelo de embeddings no disponible: {exc}") from exc


def _scores(
    tutor_ids: Iterable[str], consulta_embedding: list[float]
) -> list[tuple[str, float]]:
    try:
        return _repo.obtener_scores(tutor_ids, consulta_embedding)
    except Exception as exc:
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


@app.post("/recompute-embeddings")
def recompute_embeddings() -> dict:
    """Repopula `embedding` de TODOS los perfiles desde sus `tema_ids` (2c).

    Idempotente (mismo estado -> mismo resultado) y SIN reglas de negocio:
    no lee autorizacion, reputacion ni activo_para_matching. Toma los
    `tema_ids` ya validados en Java, arma el texto fuente '{nombre}:
    {descripcion}' de cada tema ('. ' entre temas), lo embeddea con el MISMO
    modelo lazy de /match y escribe el vector — NULL si el perfil no tiene
    temas. Si el modelo o la base no estan disponibles se responde 503 de
    forma explicita; nunca fabrica un embedding falso.
    """
    try:
        perfiles = _recompute_repo.perfiles_con_temas()
        for tutor_id, temas in perfiles:
            vector = None if not temas else _embed(texto_fuente(temas))
            _recompute_repo.guardar_embedding(tutor_id, vector)
    except MatchError as exc:
        raise Unavailable(str(exc)) from exc
    except Exception as exc:
        raise Unavailable(f"base de pgvector no disponible: {exc}") from exc
    return {"actualizados": len(perfiles)}


@app.exception_handler(Unavailable)
def _unavailable_handler(_request, exc: Unavailable):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=503, content={"detail": str(exc)})
