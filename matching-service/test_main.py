"""Tests del contrato de /match y /health (T-M2-04).

Usan dependencias inyectadas — embedder y repo falsos — para que el test no
tenga que descargar el modelo sentence-transformers ni tocar una base real.
En produccion el endpoint usa las implementaciones reales (lazy); aca solo se
cubre el contrato de la API y la logica de ranking. El numero 384 de la
dimension del vector falso coincide con VECTOR(384) de la migracion V7.
"""

import threading
import time

import pytest
from fastapi.testclient import TestClient

import main as srv

client = TestClient(srv.app)

# Auditoria AUD-015: el servicio exige un token compartido con el backend en el
# header X-Matching-Token. Todos los tests corren con un token configurado salvo
# los que verifican el fail-closed explicito.
TOKEN_PRUEBA = "token-compartido-de-prueba"


@pytest.fixture(autouse=True)
def _matching_token_configurado(monkeypatch):
    monkeypatch.setenv("TINKU_MATCHING_TOKEN", TOKEN_PRUEBA)


def post_autenticado(url, **kwargs):
    """POST con el token compartido de prueba (AUD-015): la endpoint exige el
    header, salvo los tests del 401/503 que usan el client crudo a proposito."""
    kwargs.setdefault("headers", {})["X-Matching-Token"] = TOKEN_PRUEBA
    return client.post(url, **kwargs)


def fake_embedder(texto: str) -> list[float]:
    # Embedding determinista "por hash": textos iguales -> mismo vector,
    # textos distintos -> vectores distintos (suficiente para el contrato).
    if texto == "algebra":
        return [1.0] * 384
    if texto == "fisica":
        return [-1.0] * 384
    return [0.0] * 384


def test_health():
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_health_publico_sin_token_200():
    # /health lo usan el compose y el panel de salud de M8: queda publico.
    resp = client.get("/health")
    assert resp.status_code == 200


def _auth_fakes(monkeypatch):
    """Aisla el chequeo de auth del resto: sin esto, el /match con repo real cae a
    503 por base no disponible y el RED no probaría la autenticación."""
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_scores", lambda tutor_ids, consulta_embedding: [])


def test_match_sin_token_401(monkeypatch):
    _auth_fakes(monkeypatch)
    resp = client.post(
        "/match",
        json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]},
    )
    assert resp.status_code == 401


def test_match_token_incorrecto_401(monkeypatch):
    _auth_fakes(monkeypatch)
    resp = client.post(
        "/match",
        json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]},
        headers={"X-Matching-Token": "otro-token"},
    )
    assert resp.status_code == 401


def test_match_token_correcto_200(monkeypatch):
    _auth_fakes(monkeypatch)
    resp = client.post(
        "/match",
        json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]},
        headers={"X-Matching-Token": TOKEN_PRUEBA},
    )
    assert resp.status_code == 200


def test_sin_token_configurado_el_servicio_responde_503(monkeypatch):
    # Fail-closed (AUD-015): token vacio en el servicio = todo 503 salvo /health.
    _auth_fakes(monkeypatch)
    monkeypatch.setenv("TINKU_MATCHING_TOKEN", "")
    resp = client.post(
        "/match",
        json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]},
        headers={"X-Matching-Token": TOKEN_PRUEBA},
    )
    assert resp.status_code == 503


def test_match_ranking_por_similitud(monkeypatch):
    def fake_scores(tutor_ids, consulta_embedding):
        assert consulta_embedding == [1.0] * 384
        return [("t3", 0.9), ("t1", 0.5)]

    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_scores", fake_scores)

    resp = post_autenticado(
        "/match",
        json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1", "t3"]},
    )

    assert resp.status_code == 200
    assert resp.json() == [
        {"tutor_id": "t3", "score": 0.9},
        {"tutor_id": "t1", "score": 0.5},
    ]


def test_match_sin_candidatos_no_llama_al_repo(monkeypatch):
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    llamadas = []

    def fake_scores(tutor_ids, consulta_embedding):
        llamadas.append(True)
        return []

    monkeypatch.setattr(srv, "_scores", fake_scores)

    resp = post_autenticado(
        "/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": []}
    )
    assert resp.status_code == 200
    assert resp.json() == []
    assert llamadas == []  # el repo recibe la lista vacia tal cual llega


def test_match_503_cuando_modelo_o_base_no_disponibles(monkeypatch):
    def embed_roto(texto: str) -> list[float]:
        raise srv.MatchError("modelo de embeddings no disponible: sin red")

    monkeypatch.setattr(srv, "_embed", embed_roto)

    resp = post_autenticado(
        "/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]}
    )
    assert resp.status_code == 503  # nunca fabrica un ranking falso


class FakeRecomputeRepo:
    """Fake del repo de recompute (contrato 2c): registra las escrituras."""

    def __init__(self, perfiles, temas_pendientes=()):
        self.perfiles = perfiles
        self.escrituras = []  # [(tutor_id, vector | None)]
        self.pendientes = list(temas_pendientes)
        self.escrituras_temas = []  # [(tema_id, texto, vector)]

    def perfiles_con_temas(self):
        return self.perfiles

    def temas_pendientes(self):
        return self.pendientes

    def persistir_embeddings_temas(self, actualizaciones):
        self.escrituras_temas.extend(actualizaciones)

    def persistir_embeddings(self, actualizaciones):
        self.escrituras.extend(actualizaciones)


def test_texto_fuente_puro():
    # "{nombre}: {descripcion}" por tema, separados por ". "
    assert (
        srv.texto_fuente(
            [("Matemática", "operaciones con enteros"), ("División", "cómo dividir")]
        )
        == "Matemática: operaciones con enteros. División: cómo dividir"
    )


def test_recompute_arma_el_texto_fuente_pasado_al_embedder(monkeypatch):
    textos = []

    def spy_embed(texto: str) -> list[float]:
        textos.append(texto)
        return [0.0] * 384

    monkeypatch.setattr(srv, "_embed", spy_embed)
    monkeypatch.setattr(
        srv,
        "_recompute_repo",
        FakeRecomputeRepo(
            [
                (
                    "t1",
                    [
                        ("Matemática", "operaciones con enteros"),
                        ("División", "cómo dividir"),
                    ],
                )
            ]
        ),
    )

    resp = post_autenticado("/recompute-embeddings")

    assert resp.status_code == 200
    assert textos == ["Matemática: operaciones con enteros. División: cómo dividir"]


def test_recompute_embeddea_y_persiste_el_vector_de_cada_tutor_con_temas(monkeypatch):
    textos = []

    def spy_embed(texto: str) -> list[float]:
        textos.append(texto)
        return fake_embedder(texto)

    repo = FakeRecomputeRepo(
        [
            ("t1", [("Matemática", "operaciones con enteros")]),
            ("t2", [("Física", "leyes de Newton")]),
        ]
    )
    monkeypatch.setattr(srv, "_embed", spy_embed)
    monkeypatch.setattr(srv, "_recompute_repo", repo)

    resp = post_autenticado("/recompute-embeddings")

    assert resp.status_code == 200
    assert resp.json() == {"actualizados": 2, "temas_embebidos": 0}
    assert dict(repo.escrituras) == {"t1": [0.0] * 384, "t2": [0.0] * 384}


def test_recompute_tutor_sin_temas_escribe_embedding_null(monkeypatch):
    repo = FakeRecomputeRepo(
        [
            ("t1", [("Matemática", "operaciones con enteros")]),
            ("t2", []),
        ]
    )
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_recompute_repo", repo)

    resp = post_autenticado("/recompute-embeddings")

    assert resp.status_code == 200
    assert resp.json() == {"actualizados": 2, "temas_embebidos": 0}
    assert repo.escrituras == [
        ("t1", [0.0] * 384),
        ("t2", None),
    ]  # NULL borra el vector viejo


def test_recompute_idempotente_mismas_escrituras_sin_error(monkeypatch):
    repo = FakeRecomputeRepo([("t1", [("Matemática", "operaciones con enteros")])])
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_recompute_repo", repo)

    for _ in range(2):
        resp = post_autenticado("/recompute-embeddings")
        assert resp.status_code == 200
        assert resp.json() == {"actualizados": 1, "temas_embebidos": 0}
    assert repo.escrituras == [("t1", [0.0] * 384), ("t1", [0.0] * 384)]


def test_recompute_embebe_cada_tema_pendiente_con_su_texto(monkeypatch):
    # V42: /match puntua por el mejor tema del Tutor, asi que cada tema elegido
    # necesita su propio embedding (con el texto con el que se hizo).
    repo = FakeRecomputeRepo(
        [("t1", [("Fracciones", "suma y resta")])],
        temas_pendientes=[("tema-1", "Fracciones: suma y resta")],
    )
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_recompute_repo", repo)

    resp = post_autenticado("/recompute-embeddings")

    assert resp.status_code == 200
    assert resp.json() == {"actualizados": 1, "temas_embebidos": 1}
    assert repo.escrituras_temas == [("tema-1", "Fracciones: suma y resta", [0.0] * 384)]


def test_sql_de_scores_toma_el_mejor_tema_y_cae_al_perfil():
    # El score es el MAXIMO por tema (no un embedding diluido de todos los temas);
    # sin temas embebidos todavia, usa el embedding del perfil.
    sql = " ".join(srv.SQL_SCORES.split())
    assert "MAX(1 - (t.embedding <=> %(q)s::vector))" in sql
    assert "COALESCE(" in sql and "ptm.embedding" in sql
    assert "GROUP BY ptm.tutor_id" in sql


def test_recompute_503_cuando_modelo_no_disponible(monkeypatch):
    def embed_roto(texto: str) -> list[float]:
        raise srv.MatchError("modelo de embeddings no disponible: sin red")

    monkeypatch.setattr(srv, "_embed", embed_roto)
    monkeypatch.setattr(
        srv,
        "_recompute_repo",
        FakeRecomputeRepo([("t1", [("Matemática", "operaciones con enteros")])]),
    )

    resp = post_autenticado("/recompute-embeddings")

    assert (
        resp.status_code == 503
    )  # misma firma que /match, nunca fabrica un embedding falso
    assert "detail" in resp.json()


def test_precarga_el_embedder_en_el_arranque(monkeypatch):
    llamado = []

    def espia() -> None:
        llamado.append(True)

    monkeypatch.setattr(srv, "_cargar_embedder", espia)
    srv._precargar_embedder()
    assert llamado == [True]


def test_precarga_tolerante_si_el_modelo_no_esta_disponible(monkeypatch):
    def roto():  # sin red a HuggingFace, por ejemplo
        raise RuntimeError("sin red")

    monkeypatch.setattr(srv, "_cargar_embedder", roto)
    srv._precargar_embedder()  # no lanza: la primera request reintenta el lazy


def test_carga_lazy_concurrente_carga_el_modelo_una_sola_vez(monkeypatch):
    # AUD-036.7: dos requests concurrentes con el embedder aun sin cargar (p. ej.
    # tras una precarga que fallo) no pueden cargar el modelo dos veces.
    cargas = []

    def crear_lento():
        time.sleep(0.05)
        cargas.append(1)
        return lambda texto: [0.0] * 384

    monkeypatch.setattr(srv, "_crear_modelo", crear_lento)
    monkeypatch.setattr(srv, "_embedder", None)

    resultados = []

    def pedir():
        resultados.append(srv._cargar_embedder())

    h1 = threading.Thread(target=pedir)
    h2 = threading.Thread(target=pedir)
    h1.start()
    h2.start()
    h1.join()
    h2.join()

    assert len(cargas) == 1  # el modelo se carga una sola vez
    assert len(resultados) == 2


# --------------------------------------------------------------- /sugerir-temas


def _embedder_ejes(texto: str) -> list[float]:
    """Vectores por palabra clave: lo que habla de fracciones apunta al mismo eje."""
    t = texto.lower()
    if "fraccion" in t:
        return [1.0, 0.0, 0.0]
    if "celula" in t:
        return [0.0, 1.0, 0.0]
    return [0.0, 0.0, 1.0]


def test_sugerir_temas_ordena_por_similitud_y_respeta_el_limite(monkeypatch):
    monkeypatch.setattr(srv, "_embed", _embedder_ejes)
    srv._cache_temas.clear()
    r = post_autenticado("/sugerir-temas", json={
        "texto": "Doy clases de fracciones a chicos de primaria",
        "temas": [
            {"id": "t-cel", "texto": "La celula: partes"},
            {"id": "t-fra", "texto": "Fracciones: suma y resta"},
            {"id": "t-otro", "texto": "Revolucion de Mayo"},
        ],
        "limite": 2,
    })
    assert r.status_code == 200
    ids = [s["id"] for s in r.json()]
    assert ids[0] == "t-fra"
    assert len(ids) == 2


def test_sugerir_temas_sin_token_401_y_vacio_devuelve_lista_vacia(monkeypatch):
    monkeypatch.setattr(srv, "_embed", _embedder_ejes)
    assert client.post("/sugerir-temas", json={"texto": "x", "temas": []}).status_code == 401
    r = post_autenticado("/sugerir-temas", json={"texto": "  ", "temas": [{"id": "a", "texto": "b"}]})
    assert r.status_code == 200 and r.json() == []


def test_sugerir_temas_modelo_caido_503(monkeypatch):
    def roto(_texto):
        raise srv.MatchError("sin modelo")

    monkeypatch.setattr(srv, "_embed", roto)
    r = post_autenticado("/sugerir-temas", json={"texto": "fracciones", "temas": [{"id": "a", "texto": "b"}]})
    assert r.status_code == 503
