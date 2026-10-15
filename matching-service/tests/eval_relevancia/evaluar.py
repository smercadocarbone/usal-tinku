"""R-E E-1 (T1-M1): calibración del corte de relevancia de la búsqueda.

Dos modos:
  --offline  Reproduce el puntaje del matching-service sin base de datos: embebe cada tema del
             catálogo (docs/catalogo_seed/*.json) como "{nombre}: {descripcion}" con el MISMO modelo
             que main.py, arma los tutores del seed (TutorSeedRunner.java) y puntúa cada tutor con el
             máximo coseno entre la consulta y sus temas (igual que SQL_SCORES de main.py).
  --url URL  Pega contra un matching-service real (POST /match) con los ids de tutores que se le pasen
             en --tutores (archivo con un UUID por línea). Es la acción O-11 del plan, contra staging.

Salida: resultados.csv (texto, esperado, score_top1, score_top5, n_sobre_absoluto) y un resumen por
grupo en la salida estándar. No toca producción ni datos personales: las consultas son inventadas.
"""
from __future__ import annotations

import argparse
import csv
import json
import re
import statistics
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
MODELO = "paraphrase-multilingual-MiniLM-L12-v2"


def catalogo() -> dict[tuple[str, str, str], list[tuple[str, str]]]:
    temas: dict[tuple[str, str, str], list[tuple[str, str]]] = {}
    for nivel in ("primario", "secundario", "universitario"):
        for t in json.loads((RAIZ / "docs/catalogo_seed" / f"{nivel}.json").read_text(encoding="utf-8")):
            clave = (t["nivel"], t["anio_o_carrera"], t["materia"])
            temas.setdefault(clave, []).append((t["tema"], t["descripcion"]))
    return temas


def tutores_seed(cat) -> list[list[tuple[str, str]]]:
    fuente = (RAIZ / "backend/src/main/java/com/tinku/matching/service/TutorSeedRunner.java").read_text(encoding="utf-8")
    niveles = {"PRI": "primario", "SEC": "secundario", "UNI": "universitario"}
    tutores = []
    for bloque in fuente.split("new TutorSeed(")[1:]:
        temas = []
        for nivel, curso, materia, nombre in re.findall(
                r'tema\((PRI|SEC|UNI), "([^"]*)", "([^"]*)", (null|"[^"]*")\)', bloque):
            disponibles = cat.get((niveles[nivel], curso, materia), [])
            if nombre == "null":
                temas.extend(disponibles)
            else:
                temas.extend(t for t in disponibles if t[0] == nombre.strip('"'))
        if temas:
            tutores.append(temas)
    return tutores


def puntajes_offline(consultas: list[str]) -> list[list[float]]:
    from sentence_transformers import SentenceTransformer, util

    modelo = SentenceTransformer(MODELO)
    tutores = tutores_seed(catalogo())
    textos = sorted({f"{n}: {d}" for temas in tutores for n, d in temas})
    indice = {t: i for i, t in enumerate(textos)}
    emb_temas = modelo.encode(textos, convert_to_tensor=True, normalize_embeddings=True)
    emb_q = modelo.encode(consultas, convert_to_tensor=True, normalize_embeddings=True)
    sim = util.cos_sim(emb_q, emb_temas)
    salida = []
    for qi in range(len(consultas)):
        por_tutor = [max(float(sim[qi][indice[f"{n}: {d}"]]) for n, d in temas) for temas in tutores]
        salida.append(sorted(por_tutor, reverse=True))
    return salida


def puntajes_url(consultas: list[str], url: str, ids: list[str], token: str | None) -> list[list[float]]:
    import httpx

    cabeceras = {"X-Matching-Token": token} if token else {}
    salida = []
    for q in consultas:
        r = httpx.post(f"{url.rstrip('/')}/match", json={"tutor_ids": ids, "texto_busqueda": q},
                       headers=cabeceras, timeout=60)
        r.raise_for_status()
        salida.append(sorted((float(x["score"]) for x in r.json()), reverse=True))
    return salida


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--url")
    ap.add_argument("--tutores")
    ap.add_argument("--token")
    ap.add_argument("--absoluto", type=float, default=0.3)
    args = ap.parse_args()

    aqui = Path(__file__).parent
    filas = list(csv.DictReader((aqui / "consultas.csv").open(encoding="utf-8")))
    consultas = [f["texto"] for f in filas]
    if args.offline:
        scores = puntajes_offline(consultas)
    else:
        ids = [l.strip() for l in Path(args.tutores).read_text().splitlines() if l.strip()]
        scores = puntajes_url(consultas, args.url, ids, args.token)

    with (aqui / "resultados.csv").open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["texto", "esperado", "score_top1", "score_top5", "n_sobre_absoluto"])
        for fila, s in zip(filas, scores):
            top5 = s[4] if len(s) > 4 else (s[-1] if s else 0.0)
            w.writerow([fila["texto"], fila["esperado"], f"{s[0] if s else 0:.4f}", f"{top5:.4f}",
                        sum(1 for x in s if x >= args.absoluto)])

    for grupo in ("relevante", "irrelevante", "sin_sentido"):
        tops = [s[0] for f, s in zip(filas, scores) if f["esperado"] == grupo and s]
        print(f"{grupo:12s} n={len(tops):2d} top1 min={min(tops):.3f} mediana={statistics.median(tops):.3f} max={max(tops):.3f}")


if __name__ == "__main__":
    main()
