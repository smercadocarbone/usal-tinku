-- FR-ID-011: el ciclo de fotos ilegibles es configurable (tinku.ocr.max-intentos, 6 por defecto
-- desde ADR-M1-08) y V4 lo había fijado en la base a 0..3: la cuarta foto ilegible violaba la
-- restricción (500) y la espera nunca se activaba. El tope lo pone la aplicación; acá solo >= 0.
ALTER TABLE identidad.intentos_ocr DROP CONSTRAINT intentos_ocr_intentos_consumidos_check;
ALTER TABLE identidad.intentos_ocr ADD CONSTRAINT intentos_ocr_intentos_consumidos_check
    CHECK (intentos_consumidos >= 0);
