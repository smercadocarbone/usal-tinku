"""Tests del contrato de /match y /health (T-M2-04).

Usan dependencias inyectadas — embedder y repo falsos — para que el test no
tenga que descargar el modelo sentence-transformers ni tocar una base real.
En produccion el endpoint usa las implementaciones reales (lazy); aca solo se
cubre el contrato de la API y la logica de ranking. El numero 384 de la
dimension del vector falso coincide con VECTOR(384) de la migracion V7.
"""

from fastapi.testclient import TestClient

import main as srv

client = TestClient(srv.app)


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


def test_match_ranking_por_similitud(monkeypatch):
    def fake_scores(tutor_ids, consulta_embedding):
        assert consulta_embedding == [1.0] * 384
        return [("t3", 0.9), ("t1", 0.5)]

    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_scores", fake_scores)

    resp = client.post(
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

    resp = client.post(
        "/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": []}
    )
    assert resp.status_code == 200
    assert resp.json() == []
    assert llamadas == []  # el repo recibe la lista vacia tal cual llega


def test_match_503_cuando_modelo_o_base_no_disponibles(monkeypatch):
    def embed_roto(texto: str) -> list[float]:
        raise srv.MatchError("modelo de embeddings no disponible: sin red")

    monkeypatch.setattr(srv, "_embed", embed_roto)

    resp = client.post(
        "/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]}
    )
    assert resp.status_code == 503  # nunca fabrica un ranking falso


class FakeRecomputeRepo:
    """Fake del repo de recompute (contrato 2c): registra las escrituras."""

    def __init__(self, perfiles):
        self.perfiles = perfiles
        self.escrituras = []  # [(tutor_id, vector | None)]

    def perfiles_con_temas(self):
        return self.perfiles

    def guardar_embedding(self, tutor_id, vector):
        self.escrituras.append((tutor_id, vector))


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

    resp = client.post("/recompute-embeddings")

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

    resp = client.post("/recompute-embeddings")

    assert resp.status_code == 200
    assert resp.json() == {"actualizados": 2}
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

    resp = client.post("/recompute-embeddings")

    assert resp.status_code == 200
    assert resp.json() == {"actualizados": 2}
    assert repo.escrituras == [
        ("t1", [0.0] * 384),
        ("t2", None),
    ]  # NULL borra el vector viejo


def test_recompute_idempotente_mismas_escrituras_sin_error(monkeypatch):
    repo = FakeRecomputeRepo([("t1", [("Matemática", "operaciones con enteros")])])
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    monkeypatch.setattr(srv, "_recompute_repo", repo)

    for _ in range(2):
        resp = client.post("/recompute-embeddings")
        assert resp.status_code == 200
        assert resp.json() == {"actualizados": 1}
    assert repo.escrituras == [("t1", [0.0] * 384), ("t1", [0.0] * 384)]


def test_recompute_503_cuando_modelo_no_disponible(monkeypatch):
    def embed_roto(texto: str) -> list[float]:
        raise srv.MatchError("modelo de embeddings no disponible: sin red")

    monkeypatch.setattr(srv, "_embed", embed_roto)
    monkeypatch.setattr(
        srv,
        "_recompute_repo",
        FakeRecomputeRepo([("t1", [("Matemática", "operaciones con enteros")])]),
    )

    resp = client.post("/recompute-embeddings")

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
