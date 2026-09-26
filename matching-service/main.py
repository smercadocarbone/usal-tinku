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
  lista de candidatos que llega YA acotada por el backend Java. El score de cada
  Tutor es el de su tema mas parecido a la consulta (V42).
- POST /recompute-embeddings (M2-F, contrato 2c): repopula `embedding` de TODOS
  los perfiles desde sus `tema_ids` (idempotente, sin reglas de negocio).
- POST /sugerir-temas        (asistente de "Mis materias"): ordena temas del catalogo
  por similitud con el texto libre del Tutor.
"""

import hmac
import os
import threading
from collections.abc import Callable, Iterable

import logging
import psycopg
from fastapi import Depends, FastAPI, Header, HTTPException
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


def _token_esperado() -> str | None:
    """Token compartido con el backend (AUD-015). Vacio/ausente = fail-closed."""
    token = os.environ.get("TINKU_MATCHING_TOKEN", "")
    return token if token else None


def _requiere_token(x_matching_token: str | None = Header(default=None)) -> None:
    """Dependencia de auth de /match y /recompute-embeddings (AUD-015).

    Token compartido entre dos procesos propios, en un header (Articulo VII:
    nada de OAuth, usuarios ni JWT). Fail-closed: sin token configurado en el
    servicio, TODO responde 503 salvo /health. Se compara con hmac.compare_digest
    para no filtrar el valor por timing (nunca `==`).
    """
    esperado = _token_esperado()
    if esperado is None:
        raise Unavailable(
            "matching-service sin TINKU_MATCHING_TOKEN configurado (fail-closed)"
        )
    if x_matching_token is None or not hmac.compare_digest(x_matching_token, esperado):
        raise HTTPException(status_code=401, detail="X-Matching-Token ausente o incorrecto")


def _conectar() -> psycopg.Connection:
    """Conexion a Postgres con las mismas TINKU_PG_* usadas por RepoScores."""
    return psycopg.connect(
        host=os.environ["TINKU_PG_HOST"],
        port=int(os.environ.get("TINKU_PG_PORT", "5432")),
        dbname=os.environ["TINKU_PG_DBNAME"],
        user=os.environ["TINKU_PG_USER"],
        password=os.environ["TINKU_PG_PASSWORD"],
    )


# Score del Tutor = la MEJOR similitud entre la consulta y cada uno de SUS temas
# (V42: embedding por tema del catalogo). Con un solo embedding por Tutor hecho con
# todos sus temas juntos, quien daba muchos temas quedaba "diluido" y salia mas abajo
# en cada uno (2026-09-26). Sin temas embebidos todavia, cae al embedding del perfil.
SQL_SCORES = """
    SELECT ptm.tutor_id::text,
           COALESCE(MAX(1 - (t.embedding <=> %(q)s::vector)),
                    MAX(1 - (ptm.embedding <=> %(q)s::vector))) AS score
      FROM matching.perfiles_tutor_matching ptm
      LEFT JOIN matching.temas t
             ON t.id = ANY(ptm.tema_ids) AND t.embedding IS NOT NULL
     WHERE ptm.tutor_id = ANY(%(ids)s::uuid[])
     GROUP BY ptm.tutor_id
    HAVING COALESCE(MAX(1 - (t.embedding <=> %(q)s::vector)),
                    MAX(1 - (ptm.embedding <=> %(q)s::vector))) IS NOT NULL
     ORDER BY score DESC
"""


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
                cur.execute(SQL_SCORES, {"q": Vector(consulta_embedding), "ids": ids})
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

    def temas_pendientes(self) -> list[tuple[str, str]]:
        """Temas que algun Tutor eligio y cuyo embedding falta o se hizo con otro texto:
        [(tema_id, texto_fuente)]. El catalogo tiene ~1.400 temas; solo se embeben los
        usados, y una sola vez (cambian solo si una migracion cambia su texto)."""
        conn = _conectar()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT t.id::text, t.nombre, t.descripcion, t.embedding_fuente
                      FROM matching.temas t
                     WHERE EXISTS (SELECT 1 FROM matching.perfiles_tutor_matching ptm
                                    WHERE t.id = ANY(ptm.tema_ids))
                    """
                )
                filas = cur.fetchall()
            pendientes = []
            for tema_id, nombre, descripcion, fuente in filas:
                texto = texto_fuente([(nombre, descripcion)])
                if fuente != texto:
                    pendientes.append((tema_id, texto))
            return pendientes
        finally:
            conn.close()

    def persistir_embeddings_temas(
        self, actualizaciones: Iterable[tuple[str, str, list[float]]]
    ) -> None:
        """Guarda (tema_id, texto_fuente, vector) en UNA transaccion."""
        conn = _conectar()
        try:
            register_vector(conn)
            with conn.cursor() as cur:
                for tema_id, texto, vector in actualizaciones:
                    cur.execute(
                        "UPDATE matching.temas SET embedding = %s, embedding_fuente = %s WHERE id = %s::uuid",
                        (Vector(vector), texto, tema_id),
                    )
            conn.commit()
        finally:
            conn.close()

    def persistir_embeddings(
        self, actualizaciones: Iterable[tuple[str, list[float] | None]]
    ) -> None:
        """Persiste TODAS las actualizaciones en UNA conexion y UNA transaccion
        (AUD-015): un fallo a mitad revierte todo, no deja el indice en estado
        parcial — antes era una conexion nueva por perfil (N+1)."""
        conn = _conectar()
        try:
            register_vector(conn)
            with conn.cursor() as cur:
                for tutor_id, vector in actualizaciones:
                    cur.execute(
                        "UPDATE matching.perfiles_tutor_matching SET embedding = %s WHERE tutor_id = %s::uuid",
                        (None if vector is None else Vector(vector), tutor_id),
                    )
            conn.commit()
        finally:
            conn.close()


_recompute_repo: RecomputeRepo = RecomputeRepo()


def _crear_modelo() -> Callable[[str], list[float]]:
    """Baja y construye el modelo sentence-transformers (MISMO modelo lazy de /match)."""
    from sentence_transformers import SentenceTransformer  # import tardio: pesado

    modelo = SentenceTransformer(MODELO)
    return lambda texto: modelo.encode(texto).tolist()


_embedder_lock = threading.Lock()


def _cargar_embedder() -> Callable[[str], list[float]]:
    """Carga el modelo sentence-transformers una sola vez (lazy, hilo seguro —
    AUD-036.7).

    B13 precarga el modelo en el arranque y cubre el caso normal; este lock con
    doble chequeo protege el fallback documentado de B13 (precarga fallida -> la
    primera request reintenta el lazy): dos requests concurrentes en ese caso no
    pueden cargar el modelo dos veces (~34s de descarga/modelo por request).
    """
    global _embedder
    if _embedder is None:
        with _embedder_lock:
            if _embedder is None:
                _embedder = _crear_modelo()
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


@app.post("/match", response_model=list[MatchResult], dependencies=[Depends(_requiere_token)])
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


class TemaCandidato(BaseModel):
    id: str
    texto: str  # '{nombre}: {descripcion}' — lo arma Java desde el catalogo


class SugerirTemasRequest(BaseModel):
    texto: str
    temas: list[TemaCandidato]  # catalogo ya filtrado en Java (nivel, etc.)
    limite: int = 8


class SugerenciaTema(BaseModel):
    id: str
    score: float


# Embeddings de los temas del catalogo por texto: el catalogo cambia poco y embeddear
# cientos de temas en cada pedido costaria segundos. Acotado para no crecer sin limite.
_cache_temas: dict[str, list[float]] = {}
_MAX_CACHE_TEMAS = 5000


def _embed_tema(texto: str) -> list[float]:
    vector = _cache_temas.get(texto)
    if vector is None:
        vector = _embed(texto)
        if len(_cache_temas) >= _MAX_CACHE_TEMAS:
            _cache_temas.clear()
        _cache_temas[texto] = vector
    return vector


def _coseno(a: list[float], b: list[float]) -> float:
    num = sum(x * y for x, y in zip(a, b))
    den = (sum(x * x for x in a) ** 0.5) * (sum(y * y for y in b) ** 0.5)
    return 0.0 if den == 0 else num / den


@app.post("/sugerir-temas", response_model=list[SugerenciaTema], dependencies=[Depends(_requiere_token)])
def sugerir_temas(request: SugerirTemasRequest) -> list[SugerenciaTema]:
    """Asistente de "Mis materias": ordena temas del catalogo por similitud con lo que el
    Tutor cuenta que ensena. Sin reglas de negocio: el catalogo llega ya acotado desde Java,
    y la decision de que temas guardar la toma el Tutor. Mismo modelo que /match."""
    if not request.texto.strip() or not request.temas:
        return []
    try:
        consulta = _embed(request.texto)
        puntuados = [(t.id, _coseno(consulta, _embed_tema(t.texto))) for t in request.temas]
    except MatchError as exc:
        raise Unavailable(str(exc)) from exc
    puntuados.sort(key=lambda par: par[1], reverse=True)
    limite = max(1, min(request.limite, 20))
    return [SugerenciaTema(id=tid, score=score) for tid, score in puntuados[:limite]]


class Unavailable(Exception):
    """Respuesta 503: el ranking real no se puede computar en este momento."""


@app.post("/recompute-embeddings", dependencies=[Depends(_requiere_token)])
def recompute_embeddings() -> dict:
    """Repopula `embedding` de TODOS los perfiles desde sus `tema_ids` (2c).

    Tambien embebe cada tema elegido por algun Tutor cuyo embedding falte o se haya
    hecho con otro texto (V42), que es lo que usa /match.

    Idempotente (mismo estado -> mismo resultado) y SIN reglas de negocio:
    no lee autorizacion, reputacion ni activo_para_matching. Toma los
    `tema_ids` ya validados en Java, arma el texto fuente '{nombre}:
    {descripcion}' de cada tema ('. ' entre temas), lo embeddea con el MISMO
    modelo lazy de /match y escribe el vector — NULL si el perfil no tiene
    temas. Si el modelo o la base no estan disponibles se responde 503 de
    forma explicita; nunca fabrica un embedding falso. Todos los embeddings se
    embebedean primero y se persisten despues en UNA transaccion (AUD-015).
    """
    try:
        perfiles = _recompute_repo.perfiles_con_temas()
        actualizaciones = [
            (tutor_id, None if not temas else _embed(texto_fuente(temas)))
            for tutor_id, temas in perfiles
        ]
        # V42: ademas, un embedding por tema elegido (el score de /match es el del mejor tema).
        temas = [
            (tema_id, texto, _embed(texto))
            for tema_id, texto in _recompute_repo.temas_pendientes()
        ]
        _recompute_repo.persistir_embeddings(actualizaciones)
        _recompute_repo.persistir_embeddings_temas(temas)
    except MatchError as exc:
        raise Unavailable(str(exc)) from exc
    except Exception as exc:
        raise Unavailable(f"base de pgvector no disponible: {exc}") from exc
    return {"actualizados": len(perfiles), "temas_embebidos": len(temas)}


@app.exception_handler(Unavailable)
def _unavailable_handler(_request, exc: Unavailable):
    from fastapi.responses import JSONResponse

    return JSONResponse(status_code=503, content={"detail": str(exc)})
