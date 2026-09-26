# ADR-M1-08 — Tesseract invocado como programa, no vía Tess4J

**Estado:** Aceptado (2026-09-26). Enmienda la forma de integración de ADR-M1-01; el proveedor
(Tesseract, local, idioma `spa`) no cambia. Es una corrección de un defecto de producción, no un
cambio de proveedor.

## Contexto
- Personas reales no podían registrarse: el paso del DNI siempre fallaba.
- Causa raíz: **en producción el OCR nunca funcionó.** Tess4J 5.x (JNA) busca `libleptonica.so`
  y `libtesseract.so`; la imagen `eclipse-temurin:21-jre` (Ubuntu 24.04) trae `liblept.so.5` y
  `libtesseract.so.5`. Aun con los enlaces creados a mano, lept4j necesita Leptonica ≥ 1.83
  (`undefined symbol: pixAddMultipleBlackWhiteBorders`) y Ubuntu 24.04 trae la 1.82.
  Cada foto terminaba en `OcrNoDisponibleException` ("lector no disponible").
- Los tests no lo detectaban: el test de OCR real se salteaba (`Assumptions`) si no encontraba
  la librería, y el perfil `test` usa el stub.

## Decisión
1. `TesseractOcrService` ejecuta el programa `tesseract` del paquete del sistema
   (`ProcessBuilder`, salida TSV, 30 s de límite). La imagen se escribe en un archivo temporal
   del contenedor, que se borra al terminar: nunca sale del servidor (Artículo V).
2. Se quita la dependencia `tess4j` del `pom.xml`.
3. Pipeline pensado para fotos de celular: rotación EXIF, recorte de la tarjeta sobre la mesa,
   contraste, enderezado fino, detección de orientación de Tesseract (`--psm 0`) y varias
   lecturas (bloque, binarizada, otras rotaciones) hasta que el parser obtiene los cuatro datos.
   El dorso se relee solo con los caracteres de la MRZ; los dígitos de control siguen siendo el
   filtro de una lectura mala.
4. Si el programa no está o no responde → 503, no consume intentos (igual que antes).
5. Propiedades: `tinku.ocr.comando` (`OCR_COMANDO`, por defecto `tesseract`), `tinku.ocr.tessdata`,
   `tinku.ocr.idioma`.

## Alternativas descartadas
- **Compilar Leptonica ≥ 1.83 en la imagen:** más tiempo de build y una librería nativa propia que
  mantener, para 1 desarrollador.
- **Bajar a una versión vieja de Tess4J:** mismo acoplamiento frágil a la versión de la librería
  del sistema; se vuelve a romper con cualquier actualización de la imagen base.
- **OCR en la nube:** ya descartado en ADR-M1-01 (costo y la imagen del DNI saldría del servidor).

## Consecuencias
- Un proceso por lectura (~3-5 s por foto con las variantes). Aceptable con el rate limit de
  `/verificar-dni` (Tabla_Tiempos).
- El test `TesseractOcrServiceRealTest` corre con cualquier `tesseract` instalado (CI con
  `apt-get install tesseract-ocr tesseract-ocr-spa`) y usa fotos sintéticas de celular
  (`src/test/resources/ocr/generar_dnis.py`, datos inventados).
- Evidencia de la causa raíz: `ldconfig -p | grep -E 'lept|tesseract'` en la imagen muestra
  solo `liblept.so.5` / `libtesseract.so.5`; `dpkg -s libleptonica-dev` → 1.82.0.
