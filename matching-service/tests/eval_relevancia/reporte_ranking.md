# Evaluación offline del ranking del matching (T-TES-11b §1.a)

- Fecha: 2026-10-18 · Modelo: `paraphrase-multilingual-MiniLM-L12-v2` · Corte: absoluto 0.46, relativo 0.25
- Consultas: 85 relevantes, 20 irrelevantes, 15 sin sentido (`consultas_etiquetadas.csv`)
- Resultados DIRECTOS (sin la recomendación por área, FR-MATCH-011). Detalle por consulta: `reporte_ranking.csv`.

## Contra los umbrales de T11b

| Métrica | Valor | Umbral | Cumple |
|---|---|---|---|
| MRR (relevantes) | 0,920 | ≥ 0,80 | sí |
| recall@5 (relevantes) | 0,949 | ≥ 0,60 | sí |
| Relevantes con al menos un resultado | 100,0 % | ≥ 90 % | sí |
| Irrelevantes en 0 | 60,0 % | ≥ 80 % | **no** |
| Sin sentido en 0 | 80,0 % | 100 % | **no** |

## Todas las métricas (grupo relevante)

| k | precision@k | recall@k |
|---|---|---|
| 1 | 0,882 | 0,493 |
| 3 | 0,725 | 0,846 |
| 5 | 0,640 | 0,949 |

Tasa de búsquedas sin resultados directos (todas las consultas): 20,0 %

## Barrido del umbral absoluto (relativo fijo)

| Absoluto | MRR | recall@5 | Relevantes con resultado | Irrelevantes en 0 | Sin sentido en 0 |
|---|---|---|---|---|---|
| 0,400 | 0,920 | 0,952 | 100,0 % | 35,0 % | 66,7 % |
| 0,440 | 0,920 | 0,952 | 100,0 % | 60,0 % | 73,3 % |
| 0,460 | 0,920 | 0,949 | 100,0 % | 60,0 % | 80,0 % |
| 0,480 | 0,920 | 0,947 | 100,0 % | 75,0 % | 93,3 % |
| 0,500 | 0,919 | 0,945 | 100,0 % | 80,0 % | 93,3 % |
| 0,520 | 0,904 | 0,920 | 98,8 % | 85,0 % | 93,3 % |
| 0,540 | 0,900 | 0,913 | 97,6 % | 95,0 % | 100,0 % |
| 0,560 | 0,871 | 0,875 | 95,3 % | 95,0 % | 100,0 % |
