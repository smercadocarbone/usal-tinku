# Evaluación del modo salud del kill-switch (ADR-M3-07 §4bis)

Fecha: 2026-10-28. Modelo: NSFWJS MobileNetV2 de 5 clases, el mismo del aula
(`/modelos/nsfw/model.json`, entrada de 224 px). Script:
`docs/operacion/pruebas_aula/scripts/puntuar-laminas.mjs`.

## Pregunta

En una clase de Medicina o Anatomía entre adultos se muestran láminas y modelos del cuerpo humano.
¿La regla de adultos (`Porn + Hentai ≥ 0,85`) corta esas clases sin motivo? ¿Qué regla las deja pasar
y sigue cortando el contenido sexual explícito?

## Base

- **Qué es cada clase en NSFWJS.** Según su README, `Hentai` agrupa los «dibujos pornográficos» y
  `Sexy` las imágenes sexualmente sugestivas que no son pornografía.
  - Las láminas anatómicas son dibujos de cuerpos desnudos, así que el modelo las acerca a `Hentai`.
  - La piel expuesta lleva a `Sexy`.
  - Solo `Porn` describe actos sexuales y pornografía fotográfica.
- **Falsos positivos conocidos.** Las fotos de dermatología, la documentación quirúrgica y los libros
  de anatomía son falsos positivos frecuentes de los clasificadores de desnudez, porque estos
  responden a la piel y no al contexto. Lo mismo pasa con el arte: en un estudio sobre desnudos
  artísticos, la tasa de falsos positivos fue del 21,5 % al 47,9 %. Las fuentes recomiendan políticas
  por categoría, es decir, permitir anatomía y bloquear contenido sexual.
- **Advertencia.** No encontramos un benchmark revisado por pares que mida los falsos positivos de
  NSFWJS sobre imágenes médicas. Por eso hicimos la medición propia que sigue.

## Medición

Usamos 30 imágenes de Wikimedia Commons en dominio público:
- 29 láminas de *Gray's Anatomy* (1918) y de Sobotta (1909), entre ellas las del aparato reproductor
  (Gray 1152–1228);
- el Hombre de Vitruvio;
- esquemas anatómicos actuales (mama, músculos, regiones del cuerpo).

| Imagen | Porn | Hentai | Sexy | Entre adultos (P+H) | Modo salud (P) |
|---|---|---|---|---|---|
| Gray 1219 | 0,151 | 0,779 | 0,036 | **0,930 → corta** | 0,151 |
| Gray 1211 | 0,244 | 0,134 | 0,598 | 0,378 | 0,244 |
| Gray 1154 | 0,009 | 0,371 | 0,001 | 0,379 | 0,009 |
| Gray 1156 | 0,002 | 0,240 | 0,000 | 0,242 | 0,002 |
| Gray 1225 | 0,004 | 0,169 | 0,001 | 0,173 | 0,004 |
| Las 25 restantes | ≤ 0,005 | ≤ 0,15 | ≤ 0,003 | ≤ 0,15 | ≤ 0,005 |

Resultados:
- **Entre adultos:** 1 de 30 láminas corta la clase (Gray 1219, con un 84 % de su puntaje en `Hentai`).
- **En modo salud:** ninguna corta. El `Porn` más alto que da una lámina es 0,244.
- **Con un menor:** Gray 1211 sumaría 0,976 (`Sexy` 0,598) y cortaría. Esto confirma que el modo
  salud no debe aplicarse nunca con un menor: con un menor, el costo de no cortar es mayor
  (Artículo II).

## Decisión que se apoya en esto

En modo salud, el puntaje es **solo `Porn`** y el umbral es **0,95**.

- **Por qué se saca `Hentai`.** Es la clase donde caen las láminas: un dibujo anatómico llega a 0,78.
- **Por qué 0,95.** El `Porn` más alto de una lámina quedó en 0,244, unos 0,70 por debajo del umbral.
  Subir el umbral de 0,85 a 0,95 suma margen frente a fotos clínicas reales, que tienen más piel que
  una lámina y no medimos.
  - El contenido sexual explícito es justamente lo que `Porn` describe.
  - Se siguen pidiendo 3 lecturas seguidas, y la rama de adultos pregunta antes de cortar (US-7).

## Límites

- **Muestra chica, todos dibujos.** No hay fotos clínicas ni de cadáveres, ni modelos anatómicos
  filmados con cámara.
- **El lado explícito no está medido.** No medimos qué tan alto da `Porn` el contenido sexual
  explícito. El 0,95 es una decisión de diseño, no un umbral calibrado.
- **Trabajo futuro:** calibrar con un set etiquetado de imágenes clínicas y con las alertas de las
  clases en modo salud. Es el mismo disparador que BR-KS-04.

## Fuentes

- NSFWJS, README (definición de las 5 clases y precisión de alrededor del 93 %):
  https://github.com/infinitered/nsfwjs
- *An Art-centric perspective on AI-based content moderation of nudity* (falsos positivos del 21,5 %
  al 47,9 % en desnudos artísticos): https://arxiv.org/pdf/2409.17156
- *NSFW Detection: Challenges and Solutions* (imágenes médicas y de anatomía como falsos positivos;
  políticas por categoría): https://www.deepcleer.com/blog/challenges-solution-of-nsfw-moderation
- *How AI Content Moderation Works* (arte, imágenes médicas y trajes de baño como falsos positivos
  frecuentes): https://scanly.co/blog/how-nsfw-detection-works
