-- U1 (docs/superpowers/specs/ux/00-LEEME-ux.md §5, respuesta del usuario 2026-09-24):
-- el Tutor puede cargar una bio (hasta 500 caracteres) y una foto opcional en su
-- perfil público. Spec_M1 US-7.
--
-- Minimización (Constitución, Art. I §1.5): solo la referencia INTERNA del
-- almacenamiento (nunca una URL pública; los bytes se sirven por
-- GET /api/tutores/{id}/foto) y la bio. Las dos columnas son nulables: ambos
-- campos son opcionales y el Admin de Moderación puede borrarlos.

ALTER TABLE identidad.usuarios
    ADD COLUMN bio VARCHAR(500),
    ADD COLUMN foto_ref TEXT;
