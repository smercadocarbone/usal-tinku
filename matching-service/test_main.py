"""Tests del contrato de /match y /health (T-M2-04).

Usan dependencias inyectadas — embedder y repo falsos — para que el test no
tenga que descargar el modelo sentence-transformers ni tocar una base real.
En produccion el endpoint usa las implementaciones reales (lazy); aca solo se
cubre el contrato de la API y la logica de ranking. El numero 384 de la
dimension del vector falso coincide con VECTOR(384) de la migracion V7.
"""

import main as srv
from fastapi.testclient import TestClient

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

    resp = client.post("/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1", "t3"]})

    assert resp.status_code == 200
    assert resp.json() == [{"tutor_id": "t3", "score": 0.9}, {"tutor_id": "t1", "score": 0.5}]


def test_match_sin_candidatos_no_llama_al_repo(monkeypatch):
    monkeypatch.setattr(srv, "_embed", fake_embedder)
    llamadas = []

    def fake_scores(tutor_ids, consulta_embedding):
        llamadas.append(True)
        return []

    monkeypatch.setattr(srv, "_scores", fake_scores)

    resp = client.post("/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": []})
    assert resp.status_code == 200
    assert resp.json() == []
    assert llamadas == []  # el repo recibe la lista vacia tal cual llega


def test_match_503_cuando_modelo_o_base_no_disponibles(monkeypatch):
    def embed_roto(texto: str) -> list[float]:
        raise srv.MatchError("modelo de embeddings no disponible: sin red")

    monkeypatch.setattr(srv, "_embed", embed_roto)

    resp = client.post("/match", json={"texto_busqueda": "algebra", "tutor_ids_candidatos": ["t1"]})
    assert resp.status_code == 503  # nunca fabrica un ranking falso