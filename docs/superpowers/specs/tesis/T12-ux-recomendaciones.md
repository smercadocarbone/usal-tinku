# T12 — Presentar las recomendaciones por su resultado, no como "IA"

**Branch:** `tesis/ux-recomendaciones` · **Riesgo:** bajo · **Bloqueada por:** —

## 1. Problema

En la encuesta de validación (tesis, apéndice A), el 68,2 % quiere que la plataforma le
recomiende el tutor más adecuado, pero **solo el 5,7 %** eligió "IA que recomiende profesores"
entre las funciones importantes (último lugar). En cambio, el resumen automático, descrito por
su utilidad, fue la segunda función más valorada. La decisión de la tesis (Cap. 7): presentar la
recomendación por lo que resuelve, sin destacar la IA.

## 2. Implementación

1. `rg -n -i "\bIA\b|inteligencia artificial|\bAI\b|matching" frontend/src` y revisar cada texto
   visible al usuario en búsqueda, resultados y perfil del tutor.
2. Reemplazar el rótulo técnico por el beneficio: por ejemplo, "Tutores recomendados para lo que
   necesitás" y, en cada resultado, una línea breve de **por qué** se sugiere (materia y nivel
   que coinciden), si el backend ya devuelve esa información. Si no la devuelve, **PARAR**: no
   inventar explicaciones.
3. Coordinar con `docs/superpowers/specs/ux/04-descubrir-reservar-pagar.md` para no pisar el
   rediseño: si esa spec ya cambia estos textos, esta tarea se reduce a verificar el criterio.

## 3. Criterios de aceptación

- Ningún texto de búsqueda o resultados presenta la función como "IA" o "inteligencia
  artificial". La declaración de uso de IA y los términos y condiciones no cambian.
- Tests E2E de Playwright del flujo de búsqueda actualizados (sin tocar `frontend/tests/` para
  cambios de backend, A9).
