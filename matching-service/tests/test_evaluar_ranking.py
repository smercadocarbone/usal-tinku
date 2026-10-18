"""Tests de `tests/eval_relevancia/evaluar_ranking.py` (T-TES-11b §5) con un embebedor falso: no
bajan el modelo de embeddings. Verifican el parser del seed, las reglas del backend que el script
replica y las métricas contra un caso calculado a mano."""
from __future__ import annotations

import importlib.util
import math
from pathlib import Path

import pytest

RUTA = Path(__file__).resolve().parent / "eval_relevancia" / "evaluar_ranking.py"
_spec = importlib.util.spec_from_file_location("evaluar_ranking", RUTA)
er = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(er)


# ---------- parser del seed ----------

CATALOGO_FALSO = {
    ("secundario", "4°", "Matemática"): [("Funciones", "f"), ("Derivadas", "d")],
    ("secundario", "5°", "Matemática"): [("Integrales", "i")],
    ("primario", "1°", "Inglés"): [("Colors", "c"), ("Numbers", "n")],
}

JAVA_FALSO = '''
    private static final List<TutorSeed> TUTORES = List.of(
            new TutorSeed("1", "Ana", "Uno", "a@x", LocalDate.of(1990, 1, 1), 100, List.of(
                    tema(SEC, "4°", "Matemática", "Derivadas"),
                    tema(PRI, "1°", "Inglés", null))),
            new TutorSeed("2", "Beto", "Dos", "b@x", LocalDate.of(1990, 1, 1), 100,
                    trayecto(SEC, "Matemática", "4°", "5°")));
'''


def test_tutores_seed_leeTemaYTrayecto():
    tutores = {t.dni: t for t in er.tutores_seed(JAVA_FALSO, CATALOGO_FALSO)}
    assert [n for _, n, _ in tutores["1"].temas] == ["Derivadas", "Colors", "Numbers"]
    assert [n for _, n, _ in tutores["2"].temas] == ["Funciones", "Derivadas", "Integrales"]
    assert tutores["1"].niveles == {"secundario", "primario"}


def test_seedReal_incluyeLosTutoresPorTrayecto():
    fuente = (er.RAIZ / "backend/src/main/java/com/tinku/matching/service/TutorSeedRunner.java").read_text(encoding="utf-8")
    tutores = {t.dni: t for t in er.tutores_seed(fuente, er.cargar_catalogo())}
    # 7 con tema(...) + 30 con trayecto(...): el evaluar.py de la calibración solo veía los 7.
    assert len(tutores) == 37
    assert "31000028" in tutores and len(tutores["31000028"].temas) > 40


def test_consultasEtiquetadas_referencianTutoresDelSeed():
    fuente = (er.RAIZ / "backend/src/main/java/com/tinku/matching/service/TutorSeedRunner.java").read_text(encoding="utf-8")
    dnis = {t.dni for t in er.tutores_seed(fuente, er.cargar_catalogo())}
    consultas = er.cargar_consultas(RUTA.parent / "consultas_etiquetadas.csv")
    assert len(consultas) >= 100
    assert len({c.id for c in consultas}) == len(consultas)
    for c in consultas:
        assert c.grupo in {"relevante", "irrelevante", "sin_sentido"}
        assert c.relevantes <= dnis, c.id
        assert bool(c.relevantes) == (c.grupo == "relevante"), c.id


# ---------- reglas del backend ----------

@pytest.mark.parametrize("texto,esperado", [
    ("divisiones en primario", "primario"),
    ("Matemática para la SECUNDARIA", "secundario"),
    ("análisis para la facu", "universitario"),
    ("biología del CBC", "universitario"),
    ("derivadas", None),
])
def test_nivelDicho_comoInterpreteBusqueda(texto, esperado):
    assert er.nivel_dicho(texto) == esperado


@pytest.mark.parametrize("texto,util", [("a", False), ("123456", False), ("!!!!", False), ("xyz", True), ("ñññ", True)])
def test_textoUtil_tresLetras(texto, util):
    assert er.texto_util(texto) is util


def test_cortar_absolutoYRelativo():
    assert er.cortar([("a", 0.9), ("b", 0.5), ("c", 0.7)], 0.46, 0.25) == [("a", 0.9), ("c", 0.7)]
    assert er.cortar([("a", 0.44)], 0.46, 0.25) == []
    assert len(er.cortar([(str(i), 0.9) for i in range(60)], 0.46, 0.25)) == 50


# ---------- métricas sobre un caso hecho a mano ----------

def _embebedor_falso(vectores):
    def embebedor(textos):
        return [vectores[t] for t in textos]
    return embebedor


def test_evaluar_yMetricas_casoAMano():
    tutores = [
        er.Tutor("A", "A", [("secundario", "T1", "x")]),
        er.Tutor("B", "B", [("secundario", "T2", "x")]),
        er.Tutor("C", "C", [("primario", "T3", "x")]),
    ]
    # Vectores unitarios en el plano: el coseno es el producto escalar.
    ang = lambda g: [math.cos(math.radians(g)), math.sin(math.radians(g))]  # noqa: E731
    vectores = {"T1: x": ang(0), "T2: x": ang(30), "T3: x": ang(90),
                "q1": ang(10),           # A 0,985 · B 0,940 · C 0,174 → muestra A, B
                "q2 en primaria": ang(0),  # solo C es de primaria: 0,0 < 0,46 → nada
                "q3": ang(60)}           # A 0,5 · B 0,866 · C 0,866 → corte 0,616: B, C (empate)
    consultas = [
        er.Consulta("1", "q1", "relevante", {"B"}),
        er.Consulta("2", "q2 en primaria", "relevante", {"C"}),
        er.Consulta("3", "q3", "relevante", {"A", "C"}),
        er.Consulta("4", "!!", "sin_sentido", set()),
    ]
    res = er.evaluar(consultas, tutores, _embebedor_falso(vectores), 0.46, 0.25)
    assert [d for d, _ in res[0].mostrados] == ["A", "B"]
    assert res[1].mostrados == []
    assert {d for d, _ in res[2].mostrados} == {"B", "C"}
    assert res[3].mostrados == []

    m = er.resumen(res)
    # RR: q1 = 1/2, q2 = 0, q3 = C en posición 1 o 2 según el empate → se calcula igual que el script.
    rr3 = 1.0 / ([d for d, _ in res[2].mostrados].index("C") + 1)
    assert m["mrr"] == pytest.approx((0.5 + 0 + rr3) / 3)
    assert m["precision@1"] == pytest.approx((0 + 0 + (1 if res[2].mostrados[0][0] == "C" else 0)) / 3)
    assert m["precision@5"] == pytest.approx((1 / 2 + 0 + 1 / 2) / 3)
    assert m["recall@5"] == pytest.approx((1 + 0 + 1 / 2) / 3)
    assert m["relevantes_con_resultado"] == pytest.approx(2 / 3)
    assert m["sin_sentido_en_cero"] == 1.0


def test_reportes_seEscriben(tmp_path):
    tutores = [er.Tutor("A", "A", [("secundario", "T1", "x")])]
    res = er.evaluar([er.Consulta("1", "consulta", "relevante", {"A"})], tutores,
                     _embebedor_falso({"T1: x": [1.0, 0.0], "consulta": [1.0, 0.0]}), 0.46, 0.25)
    m = er.resumen(res)
    er.escribir_csv(res, tmp_path / "r.csv")
    er.escribir_md(m, tmp_path / "r.md", 0.46, 0.25, "falso", "2026-10-18")
    assert "Q" not in (tmp_path / "r.csv").read_text(encoding="utf-8").splitlines()[0]
    md = (tmp_path / "r.md").read_text(encoding="utf-8")
    assert "| MRR (relevantes) | 1,000 | ≥ 0,80 | sí |" in md
