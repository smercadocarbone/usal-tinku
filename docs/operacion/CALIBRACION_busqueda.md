# Calibración del corte de relevancia de la búsqueda (R-E E-1, T1-M1)

**Fecha:** 2026-10-15 · **Rama:** `fix/rem-e` · **Decisión:** D-3 del plan de remediación (corte relativo + mínimo absoluto)

## Qué se midió

- Consultas: `matching-service/tests/eval_relevancia/consultas.csv` (40, inventadas: 20 relevantes para los
  Tutores del seed, 10 irrelevantes con sentido, 10 sin sentido).
- Modelo: el mismo del `matching-service` (`paraphrase-multilingual-MiniLM-L12-v2`).
- Puntaje: se reprodujo el de `SQL_SCORES` de `main.py` sin base de datos (`evaluar.py --offline`): cada tema
  del catálogo (`docs/catalogo_seed/*.json`) se embebe como `"{nombre}: {descripcion}"`, cada Tutor del seed
  (`TutorSeedRunner.java`) tiene sus temas y su puntaje es el **máximo coseno** contra ellos.
- Resultados crudos: `matching-service/tests/eval_relevancia/resultados.csv`.

## Distribución del mejor puntaje (top1) por grupo

| Grupo | n | mínimo | mediana | máximo |
|---|---|---|---|---|
| Relevante | 20 | 0,385 | 0,748 | 0,895 |
| Irrelevante con sentido | 10 | 0,230 | 0,294 | 0,443 ("clases de tango bailarín") |
| Sin sentido | 10 | 0,257 | 0,338 | 0,458 ("123456") |

Confirma el hallazgo: con el umbral anterior (0,30) pasaban 5 de 10 irrelevantes y 8 de 10 sin sentido.

## Qué umbral separa mejor

| Absoluto | Relevantes con resultado | Irrelevantes en 0 | Sin sentido en 0 |
|---|---|---|---|
| 0,30 (anterior) | 20/20 (100 %) | 5/10 | 2/10 |
| 0,35 | 20/20 (100 %) | 6/10 | 5/10 |
| 0,40 | 19/20 (95 %) | 8/10 | 9/10 |
| 0,45 | 19/20 (95 %) | 10/10 | 9/10 |
| **0,46 (elegido)** | **19/20 (95 %)** | **10/10** | **10/10** |
| 0,48 | 18/20 (90 %) | 10/10 | 10/10 |

Además, la **validación de texto** (menos de 3 letras → sin resultados, sin llamar al modelo) saca de la
cuenta a "a", "123456" y "!!!!": el peor "sin sentido con letras" queda en 0,398 ("lkjh gfds"). 0,46 es el punto
medio entre el peor irrelevante (0,443) y el relevante más bajo que se conserva (0,477).

La relevante que se pierde es "inglés para chicos de primer grado" (0,385): cae a la **recomendación por área**
(FR-MATCH-011), que reconoce "Inglés · Primario" y muestra a esos Tutores aclarado como recomendación.

**Corte relativo:** no hace falta para separar los grupos; se deja en **0,25** del mejor resultado para cortar
colas largas (p. ej. [0,9; 0,5] muestra solo el primero) sin afectar listas con relevancia pareja (los casos de
prueba existentes tienen diferencias de hasta 0,2).

**Criterio de aceptación de la spec:** ≥ 90 % relevantes con resultado (95 %), 100 % sin sentido en 0 (10/10),
≥ 80 % irrelevantes en 0 (10/10). **Cumple.**

## Configuración

- `MATCHING_SCORE_MINIMO` = `0.46` (default en `application.yml`; antes `0.3`).
- `MATCHING_MARGEN_RELATIVO` = `0.25`.
- **Ojo en producción:** si Coolify define `MATCHING_SCORE_MINIMO`, esa variable manda sobre el default.
  Hay que borrarla o ponerla en `0.46` (acción del dueño).

## Límites y cómo repetirla (O-11)

La medición es sobre el catálogo y los Tutores del seed, no sobre las búsquedas reales del piloto. Para medir
contra el `matching-service` desplegado (staging):

```bash
cd matching-service
python -I tests/eval_relevancia/evaluar.py --url https://<matching-staging> --tutores ids.txt --token "$MATCHING_TOKEN"
```

(`ids.txt`: los UUID de los Tutores activos, uno por línea.) Si con datos reales los relevantes bajan del 90 %,
bajar el absoluto de a 0,02 y repetir; si no hay ningún valor que cumpla, parar: puede hacer falta otro modelo
(eso exige un ADR, AGENTS §2).
