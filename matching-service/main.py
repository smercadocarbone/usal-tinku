"""
Servicio de Motor de Matching (M2) — Tinku.

Unica excepcion al monolito modular (Constitucion, Articulo VIII): vive
como proceso Python separado exclusivamente porque el backend Java no tiene
un ecosistema maduro de sentence-transformers/FAISS. NUNCA debe adquirir
logica de negocio de otros modulos (autorizacion, suspensiones, reputacion) —
esa logica vive en el backend Java, que llama a este servicio solo con un
texto de busqueda y una lista ya acotada de tutor_ids candidatos.

Ver Plan_M2_Motor_Matching.md, secciones 1 y 3, y ADR-M2-01 (pendiente:
si el indice de embeddings vive aca en memoria o en pgvector).
"""

from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="tinku-matching-service", version="0.1.0")


@app.get("/health")
def health():
    """Usado por T-000-08 para verificar la comunicacion interna desde el
    backend Java antes de implementar cualquier logica de matching real."""
    return {"status": "ok", "service": "tinku-matching-service"}


class MatchRequest(BaseModel):
    texto_busqueda: str
    tutor_ids_candidatos: list[str]  # lista YA acotada por el backend Java (autorizacion, suspensiones)


class MatchResult(BaseModel):
    tutor_id: str
    score: float


@app.post("/match", response_model=list[MatchResult])
def match(request: MatchRequest) -> list[MatchResult]:
    """
    Placeholder — ADR-M2-01 pendiente antes de implementar el calculo real
    de similitud semantica (sentence-transformers + FAISS o pgvector).

    IMPORTANTE: este endpoint NUNCA debe consultar autorizacion, suspensiones
    de M9, ni reputacion de M7 — esas exclusiones ya deben venir resueltas
    en `tutor_ids_candidatos` antes de llegar aca (Plan tecnico de M2,
    seccion 3, pasos 2-4).
    """
    return [
        MatchResult(tutor_id=tid, score=0.0)
        for tid in request.tutor_ids_candidatos
    ]
