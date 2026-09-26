# ADR-M2-02 — Ranking final: la reputación solo desempata y hay un mínimo de relevancia

**Estado:** Aceptado (2026-09-26, revisión del matching). Resuelve el ADR-M2-02 que el Plan M2
(§5) dejaba pendiente. Decisión ya tomada en el código de forma implícita (se sumaba el peso
crudo de M7) y recién ahora formalizada y corregida.

## Contexto
- FR-MATCH-003 / US-4: las señales implícitas de M7 deciden **entre tutores de relevancia
  semántica equivalente**.
- `ReputacionSignalProveedorImpl` da un peso de 0 a ~1,6 (volumen, recontratación,
  puntualidad, cancelaciones). `AjusteRankingService` lo sumaba entero a la similitud
  coseno (≈ 0,1–0,7 en la práctica). Un Tutor con historial pero de otro tema le ganaba al
  relevante: la reputación decidía el orden, no la búsqueda.
- US-1: "si no hay resultados relevantes, lo comunica en vez de forzar resultados de baja
  calidad". Se devolvía todo Tutor con temas, sin importar el puntaje.

## Decisión
1. **Reputación normalizada y acotada:** `score = similitud + peso × min(señal, 1,6) / 1,6`,
   con `peso = tinku.matching.peso-reputacion` (0,05 por defecto, `MATCHING_PESO_REPUTACION`).
   Solo cambia el orden entre tutores que están a menos de 0,05 de similitud.
2. **Mínimo de relevancia:** con texto libre (o una búsqueda guardada), los resultados con
   similitud menor a `tinku.matching.score-minimo` (0,3 por defecto, `MATCHING_SCORE_MINIMO`)
   no se muestran; el frontend ya muestra "No encontramos tutores…". Con solo filtros de
   catálogo (materia/tema) no se aplica: los candidatos ya cumplen el filtro.
3. Los dos valores son configuración, no código: se calibran con las búsquedas reales del
   piloto (lo que el Plan pedía: "empíricamente, con datos reales").

## Alternativas descartadas
- **Multiplicar por la reputación:** un Tutor nuevo (señal 0) quedaría en cero.
- **Umbral en el servicio Python:** es una regla de producto; el servicio Python no tiene
  reglas de negocio (Constitución, Artículo VIII).

## Consecuencias
- Si el umbral queda alto, búsquedas válidas pueden volver vacías: se revisa con los logs y
  las búsquedas del piloto. El 0,3 es conservador para el modelo
  `paraphrase-multilingual-MiniLM-L12-v2`, que puntúa por tema (ADR-M2-03).
- Tests: `MatchingFlujosIntegracionTest.us4_laReputacionNoLeGanaAUnaDiferenciaRealDeRelevancia`,
  `us1_resultadosPorDebajoDelPuntajeMinimo_noSeMuestran`.
