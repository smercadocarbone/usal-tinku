# Evaluación técnica (T-TES-11b §1) — resultados

**Fecha:** 2026-10-18 · **Rama:** `feat/evaluacion-sin-piloto` · **Spec:** `docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md`

Reemplaza a los indicadores "técnicos" que iba a dar el piloto. Solo figuran números **medidos**;
lo que falta medir está marcado como pendiente, con el comando para medirlo.

## 1.a Matching — ranking contra 120 búsquedas etiquetadas

- Set: `matching-service/tests/eval_relevancia/consultas_etiquetadas.csv` — 85 relevantes, 20 irrelevantes,
  15 sin sentido. Criterio de etiquetado: T11b §1.a (se etiqueta leyendo el catálogo, nunca el puntaje).
- Script: `matching-service/tests/eval_relevancia/evaluar_ranking.py` (37 Tutores del seed, mismo modelo
  `paraphrase-multilingual-MiniLM-L12-v2`, mismas reglas de corte que el backend).
- Reporte completo: `matching-service/tests/eval_relevancia/reporte_ranking.md` y `.csv` (por consulta).
- Máquina: servidor de 4 núcleos sin GPU, modelo desde la caché local de Hugging Face.

| Métrica (umbral vigente 0,46 / relativo 0,25) | Valor | Umbral T11b | Cumple |
|---|---|---|---|
| MRR (relevantes) | 0,920 | ≥ 0,80 | sí |
| recall@5 (relevantes) | 0,949 | ≥ 0,60 | sí |
| precision@1 / @3 / @5 | 0,882 / 0,725 / 0,640 | — | — |
| Relevantes con al menos un resultado | 100 % | ≥ 90 % | sí |
| Irrelevantes en 0 | 60 % (12/20) | ≥ 80 % | **no** |
| Sin sentido en 0 | 80 % (12/15) | 100 % | **no** |
| Sin resultados directos (todas) | 20 % | — | — |

**Hallazgo.** La calibración del 2026-10-15 (`CALIBRACION_busqueda.md`) daba 10/10 irrelevantes y 10/10
sin sentido en 0 con el mismo umbral, pero su `evaluar.py` solo leía los Tutores del seed definidos con
`tema(...)` (7 de 37): los 30 definidos con `trayecto(...)` quedaban afuera. Con todos, cada búsqueda se
compara contra 780 temas distintos en vez de 43 y el máximo coseno de una consulta ajena sube. Falsos positivos con
0,46: "curso de fotografía" (0,574, 7 tutores), "corte y confección" (0,534), "maquillaje profesional"
(0,531), "lkjh gfds" (0,521), entre otros (ver el CSV).

**Barrido** (mismo set, relativo fijo 0,25):

| Absoluto | MRR | recall@5 | Relevantes con resultado | Irrelevantes en 0 | Sin sentido en 0 |
|---|---|---|---|---|---|
| 0,46 (vigente) | 0,920 | 0,949 | 100 % | 60 % | 80 % |
| 0,50 | 0,919 | 0,945 | 100 % | 80 % | 93,3 % |
| 0,52 | 0,904 | 0,920 | 98,8 % | 85 % | 93,3 % |
| **0,54** | 0,900 | 0,913 | 97,6 % | 95 % | 100 % |
| 0,56 | 0,871 | 0,875 | 95,3 % | 95 % | 100 % |

0,54 es el primer valor que cumple las cinco metas. **No se cambió el default** (`MATCHING_SCORE_MINIMO`):
es una decisión del dueño. Si se adopta, repetir el script después de cambiarlo y actualizar
`CALIBRACION_busqueda.md`. Las 2 relevantes que se pierden con 0,54 caen a la recomendación por área
(FR-MATCH-011), que este script no mide.

Cómo repetirlo:

```bash
cd matching-service
python -I tests/eval_relevancia/evaluar_ranking.py --barrido 0.44 0.48 0.50 0.52 0.54 0.56
```

## 1.b Latencias p95 de los endpoints críticos

- Script: `scripts/evaluacion/latencias.py` (solo biblioteca estándar).
- Entorno: stack **local** (Postgres 16 + pgvector en Docker, `matching-service` con uvicorn, backend
  `dev` desde el jar de esta rama), en la misma máquina de 4 núcleos compartida con otros procesos
  (load average 1,7–3,1 durante la corrida). Seed de `dev` (37 Tutores, 17 alumnos) y embeddings
  recalculados. Pasarela apagada (bypass). Sin red entre cliente y servidor: **no** incluye la latencia
  de Internet ni la de Coolify/Cloudflare.
- Carga: 5 concurrentes × 20 requests por endpoint, después de 10 de calentamiento descartados.
- Token del aula: la sala se marcó a mano en la base (`livekit_room_id`) sin LiveKit corriendo; mide el
  endpoint (lectura + firma del JWT), no la creación de la sala.
- Muestras crudas: `docs/operacion/evaluacion_tecnica/latencias_2026-10-18_*.csv`.

| Endpoint | p50 (ms) | p95 (ms) | Máx (ms) | Errores | Umbral p95 | Cumple |
|---|---|---|---|---|---|---|
| `POST /api/busquedas` | 269 | 360 | 404 | 0/100 | 1.500 | sí |
| `POST /api/reservas` | 52 | 74 | 89 | 0/100 | 800 | sí |
| `POST /api/pagos/preferencia` (bypass) | 139 | 195 | 198 | 0/100 | 800 | sí |
| `POST /api/sesiones/{id}/token` | 26 | 69 | 76 | 0/100 | 500 | sí |

Con `tinku.evaluacion.habilitada=true` (registra cada búsqueda): búsqueda p50 262 / p95 360 ms; reserva
52 / 68; pago 143 / 230; token 21 / 29. El registro no agrega una diferencia medible.

**Pendiente:** repetir contra el entorno de la demo (staging o `tinku.site`, fuera del horario de las
sesiones) para incluir la red. Comando:

```bash
python3 scripts/evaluacion/latencias.py --base https://<entorno> --alumno <DNI seed> --password <clave> \
    --admin-financiero <DNI admin> [--sesion <id> --sesion-dni <DNI>] --salida /tmp/latencias
```

## 1.c Kill-switch

No se rehízo nada; se citan los resultados existentes:

- **E2E real** (M3C-06, T-M3-06f, 2026-10-16): 9 casos pasados contra backend y LiveKit reales
  (`docker-compose.e2e.yml`, proyecto Playwright `e2e-real`).
- **Benchmark del clasificador** (M3C-07, anexo 2026-10-16 de `docs/adr/ADR-M3-01.md`): `wasm` a CPU 4×
  → p95 609 ms por inferencia, detección estimada **1,8 s con 1 track** (umbral ≤ 3 s: **cumple**);
  3,7 s con 2 tracks; 0 turnos salteados; tensores 267 → 267 (sin pérdida de memoria).
- Pendiente: la fila en un celular físico de gama media (O-10) → tarea opcional T8 de las sesiones
  moderadas, o trabajo futuro. Exactitud con imágenes reales: fuera de alcance (BR-KS-04).

## 1.d Calidad del software

| Qué | Valor | Fuente | Umbral | Estado |
|---|---|---|---|---|
| Hallazgos de la auditoría 2026-09-21 | 36 de 36 CERRADO (0 críticos abiertos) | `docs/auditoria/REGISTRO_FINDINGS.md` | 0 críticos abiertos | cumple |
| Tests backend y cobertura JaCoCo | **pendiente:** copiar del último CI de `main` al cierre | `./mvnw -B test` + `target/site/jacoco` | 0 fallas; ≥ 85 % | — |
| Tests nuevos de esta rama (backend) | 16 / 0 fallas (`EvaluacionIntegracionTest` 13, `EvaluacionApagadaIntegracionTest` 3) | `target/surefire-reports` | 0 fallas | cumple |
| Tests `matching-service` | 44 / 0 fallas (incluye 18 de `test_evaluar_ranking.py`) | `pytest` | 0 fallas | cumple |
| Playwright de esta rama | 6 / 0 fallas (`tests/evaluacion`) + 101 / 0 de las pantallas tocadas | Playwright `chromium` | 0 fallas | cumple |

El último baseline documentado de la suite completa es el de `AGENTS.md` §5 (2026-10-14): 661 tests,
0 fallas, 85,6 % de líneas. No se volvió a correr la suite completa en esta rama (máquina compartida);
el número final sale del CI.
