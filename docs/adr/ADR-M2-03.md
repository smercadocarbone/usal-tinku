# ADR-M2-03 — El score de un Tutor es el de su tema más parecido a la búsqueda

**Estado:** Aceptado (2026-09-26, revisión del matching). Enmienda el contrato 2c de
`docs/plan/Plan_M2_Temas.md`. No cambia ADR-M2-01: el índice sigue en pgvector.

## Contexto
- Cada Tutor tenía **un solo embedding** hecho con el texto de todos sus temas juntos.
- Quien daba muchos temas quedaba "diluido": su vector es un promedio de temas distintos, y
  salía más abajo en cada uno que quien daba solo ese tema. Ejemplo validado contra Postgres
  real: un especialista en Fracciones y un Tutor que da Fracciones, Historia y Química,
  buscando "fracciones", daban 1,0 contra 0,58.

## Decisión
1. Migración V42: `matching.temas` suma `embedding VECTOR(384)` y `embedding_fuente TEXT` (el
   texto con el que se hizo).
2. `/recompute-embeddings` también embebe cada tema **elegido por algún Tutor** cuyo embedding
   falte o se haya hecho con otro texto. El catálogo tiene ~1.400 temas: se embeben una vez y
   solo los usados.
3. `/match`: score del Tutor = `MAX(similitud con cada uno de sus temas)`. Sin temas embebidos
   todavía, se usa el embedding del perfil (el de antes, que se sigue calculando).
4. El contrato `/match` no cambia (mismo request y respuesta).

## Alternativas descartadas
- **Promedio por tema:** tiene el mismo problema de dilución.
- **Suma de los temas que coinciden:** premia a quien carga más temas, no a quien es relevante.

## Consecuencias
- El primer recompute después del deploy embebe los temas de todos los tutores (una vez).
- Tests: `test_main.py` (`test_recompute_embebe_cada_tema_pendiente_con_su_texto`,
  `test_sql_de_scores_toma_el_mejor_tema_y_cae_al_perfil`); la consulta se validó contra
  `pgvector/pgvector:pg16`.
