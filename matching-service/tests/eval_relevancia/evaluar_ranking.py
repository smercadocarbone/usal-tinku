"""T-TES-11b §1.a / §5: evaluación offline del ranking del matching contra un set etiquetado.

Lee `consultas_etiquetadas.csv` (id, texto, grupo, relevantes = DNIs del seed separados por `|`),
arma los Tutores del seed con TODOS sus temas (`tema(...)` y `trayecto(...)` de
`TutorSeedRunner.java`, resueltos contra `docs/catalogo_seed/*.json`), embebe cada tema como
"{nombre}: {descripcion}" (igual que /recompute-embeddings), puntúa cada Tutor con el máximo coseno
entre la consulta y sus temas (igual que `SQL_SCORES` de main.py) y aplica lo que hace el backend
antes de mostrar (`MatchingOrquestador` + `AjusteRankingService`):

  1. menos de 3 letras → sin resultados (sin llamar al modelo);
  2. si el texto dice el nivel ("primaria", "secundario", "facultad"...) solo quedan Tutores con
     algún tema de ese nivel (`InterpreteBusqueda.nivel`);
  3. corte = max(absoluto, mejor − relativo); se muestran como mucho 50.

No replica la "recomendación por área" (FR-MATCH-011), que solo actúa cuando no hay resultados
directos: las métricas miden los resultados DIRECTOS. Tampoco la reputación (el seed no tiene
calificaciones, así que no cambia el orden).

Salida: `reporte_ranking.csv` (una fila por consulta) y `reporte_ranking.md` (métricas por grupo y
contra los umbrales de T11b). El embebedor es inyectable: los tests usan uno falso.

Uso:
    python -I tests/eval_relevancia/evaluar_ranking.py [--absoluto 0.46] [--relativo 0.25] [--salida DIR]
                                                       [--barrido 0.44 0.48 0.50 ...]
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import re
import statistics
import unicodedata
from collections.abc import Callable, Sequence
from dataclasses import dataclass, field
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[3]
AQUI = Path(__file__).resolve().parent
MODELO = "paraphrase-multilingual-MiniLM-L12-v2"
MAXIMO_RESULTADOS = 50
KS = (1, 3, 5)

# Umbrales de T11b §1.a (reemplazan "I-01 > 60 % en el piloto").
UMBRALES = {
    "mrr": 0.80,
    "recall@5": 0.60,
    "relevantes_con_resultado": 0.90,
    "irrelevantes_en_cero": 0.80,
    "sin_sentido_en_cero": 1.00,
}

Embebedor = Callable[[Sequence[str]], list[list[float]]]
NIVELES = {"PRI": "primario", "SEC": "secundario", "UNI": "universitario"}


@dataclass
class Tutor:
    dni: str
    nombre: str
    temas: list[tuple[str, str, str]] = field(default_factory=list)  # (nivel, tema, descripcion)

    @property
    def niveles(self) -> set[str]:
        return {n for n, _, _ in self.temas}


@dataclass
class Consulta:
    id: str
    texto: str
    grupo: str
    relevantes: set[str]


@dataclass
class Resultado:
    consulta: Consulta
    mostrados: list[tuple[str, float]]  # (dni, score) en el orden en que se muestran
    mejor_score: float


# ---------- datos ----------

def cargar_catalogo(raiz: Path = RAIZ) -> dict[tuple[str, str, str], list[tuple[str, str]]]:
    temas: dict[tuple[str, str, str], list[tuple[str, str]]] = {}
    for nivel in ("primario", "secundario", "universitario"):
        ruta = raiz / "docs/catalogo_seed" / f"{nivel}.json"
        for t in json.loads(ruta.read_text(encoding="utf-8")):
            temas.setdefault((t["nivel"], t["anio_o_carrera"], t["materia"]), []).append((t["tema"], t["descripcion"]))
    return temas


def tutores_seed(fuente_java: str, catalogo: dict[tuple[str, str, str], list[tuple[str, str]]]) -> list[Tutor]:
    """Tutores de `TutorSeedRunner.TUTORES` con sus temas resueltos contra el catálogo.

    A diferencia de `evaluar.py` (calibración del 2026-10-15), lee también `trayecto(...)`: sin eso
    quedaban afuera los 30 Tutores definidos por trayecto.
    """
    tutores = []
    for bloque in fuente_java.split("new TutorSeed(")[1:]:
        cab = re.match(r'\s*"(\d+)",\s*"([^"]+)",\s*"([^"]+)"', bloque)
        if not cab:
            continue
        tutor = Tutor(dni=cab.group(1), nombre=f"{cab.group(2)} {cab.group(3)}")
        for nivel, curso, materia, nombre in re.findall(
                r'tema\((PRI|SEC|UNI),\s*"([^"]*)",\s*"([^"]*)",\s*(null|"[^"]*")\)', bloque):
            disponibles = catalogo.get((NIVELES[nivel], curso, materia), [])
            elegidos = disponibles if nombre == "null" else [t for t in disponibles if t[0] == nombre.strip('"')]
            tutor.temas.extend((NIVELES[nivel], n, d) for n, d in elegidos)
        for nivel, materia, cursos in re.findall(r'trayecto\((PRI|SEC|UNI),\s*"([^"]*)"((?:,\s*"[^"]*")+)\)', bloque):
            for curso in re.findall(r'"([^"]*)"', cursos):
                tutor.temas.extend((NIVELES[nivel], n, d) for n, d in catalogo.get((NIVELES[nivel], curso, materia), []))
        if tutor.temas:
            tutores.append(tutor)
    return tutores


def cargar_consultas(ruta: Path) -> list[Consulta]:
    with ruta.open(encoding="utf-8") as f:
        return [Consulta(id=r["id"], texto=r["texto"], grupo=r["grupo"],
                         relevantes={x for x in r["relevantes"].split("|") if x})
                for r in csv.DictReader(f)]


# ---------- reglas del backend ----------

def _normalizar(texto: str) -> str:
    sin_tildes = "".join(c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn")
    return sin_tildes.lower()


_PRIMARIO = re.compile(r"\bprimari[oa]s?\b")
_SECUNDARIO = re.compile(r"\bsecundari[oa]s?\b|\bsecu\b|\bcolegio\b")
_UNIVERSITARIO = re.compile(r"\buniversi\w*|\bfacultad\b|\bfacu\b|\bcbc\b")


def nivel_dicho(texto: str) -> str | None:
    """Port de `InterpreteBusqueda.nivel`."""
    t = _normalizar(texto)
    if _PRIMARIO.search(t):
        return "primario"
    if _SECUNDARIO.search(t):
        return "secundario"
    if _UNIVERSITARIO.search(t):
        return "universitario"
    return None


def texto_util(texto: str) -> bool:
    """Port de `MatchingOrquestador.tieneTextoUtil`: al menos 3 letras."""
    return sum(1 for c in texto if c.isalpha()) >= 3


def cortar(puntajes: list[tuple[str, float]], absoluto: float, relativo: float) -> list[tuple[str, float]]:
    """Port de `AjusteRankingService.ajustar` (sin reputación) + tope de 50."""
    if not puntajes:
        return []
    mejor = max(s for _, s in puntajes)
    corte = max(absoluto, mejor - relativo)
    quedan = sorted(((d, s) for d, s in puntajes if s >= corte), key=lambda x: -x[1])
    return quedan[:MAXIMO_RESULTADOS]


# ---------- evaluación ----------

def _coseno(a: Sequence[float], b: Sequence[float]) -> float:
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(x * x for x in b))
    if na == 0 or nb == 0:
        return 0.0
    return sum(x * y for x, y in zip(a, b)) / (na * nb)


def evaluar(consultas: list[Consulta], tutores: list[Tutor], embebedor: Embebedor,
            absoluto: float, relativo: float) -> list[Resultado]:
    textos_temas = sorted({f"{n}: {d}" for t in tutores for _, n, d in t.temas})
    indice = {t: i for i, t in enumerate(textos_temas)}
    emb_temas = embebedor(textos_temas)
    utiles = [c for c in consultas if texto_util(c.texto)]
    emb_consultas = dict(zip((c.id for c in utiles), embebedor([c.texto for c in utiles]))) if utiles else {}

    resultados = []
    for c in consultas:
        if c.id not in emb_consultas:
            resultados.append(Resultado(c, [], 0.0))
            continue
        q = emb_consultas[c.id]
        nivel = nivel_dicho(c.texto)
        puntajes = []
        for t in tutores:
            score = max(_coseno(q, emb_temas[indice[f"{n}: {d}"]]) for _, n, d in t.temas)
            puntajes.append((t.dni, score))
        mejor = max((s for _, s in puntajes), default=0.0)
        if nivel is not None:
            del_nivel = {t.dni for t in tutores if nivel in t.niveles}
            puntajes = [(d, s) for d, s in puntajes if d in del_nivel]
        resultados.append(Resultado(c, cortar(puntajes, absoluto, relativo), mejor))
    return resultados


def precision_at(r: Resultado, k: int) -> float:
    top = [d for d, _ in r.mostrados[:k]]
    return sum(1 for d in top if d in r.consulta.relevantes) / len(top) if top else 0.0


def recall_at(r: Resultado, k: int) -> float:
    if not r.consulta.relevantes:
        return 0.0
    top = [d for d, _ in r.mostrados[:k]]
    return sum(1 for d in top if d in r.consulta.relevantes) / len(r.consulta.relevantes)


def reciproco(r: Resultado) -> float:
    for i, (d, _) in enumerate(r.mostrados, start=1):
        if d in r.consulta.relevantes:
            return 1.0 / i
    return 0.0


def resumen(resultados: list[Resultado]) -> dict[str, float | int]:
    rel = [r for r in resultados if r.consulta.grupo == "relevante"]
    irr = [r for r in resultados if r.consulta.grupo == "irrelevante"]
    sin = [r for r in resultados if r.consulta.grupo == "sin_sentido"]
    m: dict[str, float | int] = {"n_relevante": len(rel), "n_irrelevante": len(irr), "n_sin_sentido": len(sin)}
    if rel:
        for k in KS:
            m[f"precision@{k}"] = statistics.fmean(precision_at(r, k) for r in rel)
            m[f"recall@{k}"] = statistics.fmean(recall_at(r, k) for r in rel)
        m["mrr"] = statistics.fmean(reciproco(r) for r in rel)
        m["relevantes_con_resultado"] = sum(1 for r in rel if r.mostrados) / len(rel)
        m["relevantes_sin_resultado"] = 1 - m["relevantes_con_resultado"]
    if irr:
        m["irrelevantes_en_cero"] = sum(1 for r in irr if not r.mostrados) / len(irr)
    if sin:
        m["sin_sentido_en_cero"] = sum(1 for r in sin if not r.mostrados) / len(sin)
    m["sin_resultados_total"] = sum(1 for r in resultados if not r.mostrados) / len(resultados) if resultados else 0.0
    return m


# ---------- salida ----------

def escribir_csv(resultados: list[Resultado], ruta: Path) -> None:
    with ruta.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["id", "texto", "grupo", "n_relevantes", "n_mostrados", "mejor_score", "top5_dni",
                    "precision@5", "recall@5", "rr"])
        for r in resultados:
            w.writerow([r.consulta.id, r.consulta.texto, r.consulta.grupo, len(r.consulta.relevantes),
                        len(r.mostrados), f"{r.mejor_score:.4f}", "|".join(d for d, _ in r.mostrados[:5]),
                        f"{precision_at(r, 5):.4f}", f"{recall_at(r, 5):.4f}", f"{reciproco(r):.4f}"])


def _pct(x: float) -> str:
    return f"{x * 100:.1f} %".replace(".", ",")


def _num(x: float) -> str:
    return f"{x:.3f}".replace(".", ",")


def escribir_md(m: dict[str, float | int], ruta: Path, absoluto: float, relativo: float, modelo: str,
                fecha: str, barrido: list[tuple[float, dict[str, float | int]]] | None = None) -> None:
    filas = [
        ("MRR (relevantes)", "mrr", _num, "≥ 0,80"),
        ("recall@5 (relevantes)", "recall@5", _num, "≥ 0,60"),
        ("Relevantes con al menos un resultado", "relevantes_con_resultado", _pct, "≥ 90 %"),
        ("Irrelevantes en 0", "irrelevantes_en_cero", _pct, "≥ 80 %"),
        ("Sin sentido en 0", "sin_sentido_en_cero", _pct, "100 %"),
    ]
    lineas = [
        "# Evaluación offline del ranking del matching (T-TES-11b §1.a)",
        "",
        f"- Fecha: {fecha} · Modelo: `{modelo}` · Corte: absoluto {absoluto}, relativo {relativo}",
        f"- Consultas: {m['n_relevante']} relevantes, {m['n_irrelevante']} irrelevantes, "
        f"{m['n_sin_sentido']} sin sentido (`consultas_etiquetadas.csv`)",
        "- Resultados DIRECTOS (sin la recomendación por área, FR-MATCH-011). Detalle por consulta: "
        "`reporte_ranking.csv`.",
        "",
        "## Contra los umbrales de T11b",
        "",
        "| Métrica | Valor | Umbral | Cumple |",
        "|---|---|---|---|",
    ]
    for nombre, clave, fmt, umbral in filas:
        if clave in m:
            ok = "sí" if m[clave] >= UMBRALES[clave] - 1e-9 else "**no**"
            lineas.append(f"| {nombre} | {fmt(float(m[clave]))} | {umbral} | {ok} |")
    lineas += ["", "## Todas las métricas (grupo relevante)", "", "| k | precision@k | recall@k |", "|---|---|---|"]
    for k in KS:
        if f"precision@{k}" in m:
            lineas.append(f"| {k} | {_num(float(m[f'precision@{k}']))} | {_num(float(m[f'recall@{k}']))} |")
    lineas += ["", f"Tasa de búsquedas sin resultados directos (todas las consultas): "
                   f"{_pct(float(m['sin_resultados_total']))}", ""]
    if barrido:
        lineas += ["## Barrido del umbral absoluto (relativo fijo)", "",
                   "| Absoluto | MRR | recall@5 | Relevantes con resultado | Irrelevantes en 0 | Sin sentido en 0 |",
                   "|---|---|---|---|---|---|"]
        for a, mb in barrido:
            lineas.append(f"| {_num(a)} | {_num(float(mb['mrr']))} | {_num(float(mb['recall@5']))} | "
                          f"{_pct(float(mb['relevantes_con_resultado']))} | {_pct(float(mb['irrelevantes_en_cero']))} | "
                          f"{_pct(float(mb['sin_sentido_en_cero']))} |")
        lineas.append("")
    ruta.write_text("\n".join(lineas), encoding="utf-8")


def con_memoria(embebedor: Embebedor) -> Embebedor:
    """Embebe cada texto una sola vez (el barrido reusa los mismos embeddings)."""
    cache: dict[str, list[float]] = {}

    def embebe(textos: Sequence[str]) -> list[list[float]]:
        faltan = [t for t in dict.fromkeys(textos) if t not in cache]
        if faltan:
            cache.update(zip(faltan, embebedor(faltan)))
        return [cache[t] for t in textos]
    return embebe


def embebedor_real(modelo: str = MODELO) -> Embebedor:
    from sentence_transformers import SentenceTransformer

    st = SentenceTransformer(modelo)
    return lambda textos: st.encode(list(textos), normalize_embeddings=True).tolist()


def main(argv: list[str] | None = None) -> None:
    import datetime

    ap = argparse.ArgumentParser()
    ap.add_argument("--absoluto", type=float, default=0.46, help="MATCHING_SCORE_MINIMO vigente")
    ap.add_argument("--relativo", type=float, default=0.25, help="MATCHING_MARGEN_RELATIVO vigente")
    ap.add_argument("--consultas", type=Path, default=AQUI / "consultas_etiquetadas.csv")
    ap.add_argument("--salida", type=Path, default=AQUI)
    ap.add_argument("--barrido", type=float, nargs="*", default=[],
                    help="umbrales absolutos extra para comparar (p. ej. 0.44 0.48 0.50)")
    args = ap.parse_args(argv)

    catalogo = cargar_catalogo()
    fuente = (RAIZ / "backend/src/main/java/com/tinku/matching/service/TutorSeedRunner.java").read_text(encoding="utf-8")
    tutores = tutores_seed(fuente, catalogo)
    consultas = cargar_consultas(args.consultas)
    embebedor = con_memoria(embebedor_real())
    resultados = evaluar(consultas, tutores, embebedor, args.absoluto, args.relativo)
    m = resumen(resultados)
    barrido = [(a, resumen(evaluar(consultas, tutores, embebedor, a, args.relativo)))
               for a in sorted(set(args.barrido) | ({args.absoluto} if args.barrido else set()))]
    args.salida.mkdir(parents=True, exist_ok=True)
    escribir_csv(resultados, args.salida / "reporte_ranking.csv")
    escribir_md(m, args.salida / "reporte_ranking.md", args.absoluto, args.relativo, MODELO,
                datetime.date.today().isoformat(), barrido)
    print(f"{len(tutores)} tutores, {len(consultas)} consultas")
    for k, v in m.items():
        print(f"{k:28s} {v:.4f}" if isinstance(v, float) else f"{k:28s} {v}")


if __name__ == "__main__":
    main()
