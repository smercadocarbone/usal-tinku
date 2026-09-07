-- M1 — Backoff de OCR (FR-ID-011) — T-M1-07
-- Ver Spec_M1_Identidad_Perfiles.md, sección 3 (US-3) y 4 (FR-ID-011).
--
-- Registra, por DNI DECLARADO de quien intenta darse de alta, cuántos
-- intentos de foto ilegible consumió dentro del ciclo actual y hasta cuándo
-- debe esperar (24hs) antes de un nuevo ciclo. El "reset" de ciclo es PASIVO
-- (comparamos `proximo_intento_permitido` contra ahora al leer — no hay job
-- que corra para limpiarlo): por eso no se agrega un job de Quartz acá, es un
-- cooldown persistido, no un callback (Constitución, Artículo IV/X).
CREATE TABLE identidad.intentos_ocr (
    dni                         VARCHAR(20) PRIMARY KEY,
    intentos_consumidos         INT NOT NULL DEFAULT 0 CHECK (intentos_consumidos BETWEEN 0 AND 3),
    proximo_intento_permitido   TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
