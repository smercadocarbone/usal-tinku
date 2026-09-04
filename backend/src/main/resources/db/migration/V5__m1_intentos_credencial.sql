-- M1 — Backoff escalonado de Credencial Académica (FR-ID-012) — T-M1-10
-- Ver Spec_M1, sección 3 (US-4) y 4 (FR-ID-012, Tabla_Tiempos 24→48→96...).
--
-- Registra por tutor cuántas veces se agotó el ciclo de reintentos de
-- credencial (para escalar el cooldown: 24hs * 2^n) y hasta cuándo debe
-- esperar antes del próximo ciclo. Cooldown PASIVO (se compara el timestamp
-- contra ahora al leer), como el de OCR — sin job de Quartz.
CREATE TABLE identidad.intentos_credencial (
    tutor_id                    UUID PRIMARY KEY REFERENCES identidad.usuarios(id),
    veces_ciclo_agotado         INT NOT NULL DEFAULT 0,
    proximo_intento_permitido   TIMESTAMPTZ,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
