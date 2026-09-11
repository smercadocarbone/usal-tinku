-- M3-E: Kill-switch — confirmación de la rama "adultos" (T-M3-09).
-- La rama menor corta directo y no necesita confirmación; la rama adultos
-- registra la respuesta del otro participante en esta tabla antes de cortar.

SET search_path TO aula, public;

-- US-5 (FR-AULA-005): la regla de corte <50% necesita la duración AGENDADA de
-- la sesión. Se fija al programarla (horario + duración de la franja que la
-- cubre) y queda congelada: si la franja se edita/elimina después, el umbral
-- del corte automático sigue siendo el original.
ALTER TABLE aula.sesiones_aprendizaje
    ADD COLUMN duracion_agendada_segundos INT;

CREATE TABLE aula.confirmaciones_killswitch (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id               UUID NOT NULL UNIQUE REFERENCES aula.sesiones_aprendizaje(id),
    detectado_id            UUID NOT NULL REFERENCES identidad.usuarios(id),
    respondido_id           UUID,                       -- null = esperando respuesta
    vio                     BOOLEAN,                    -- null hasta que responda
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at            TIMESTAMPTZ
);

CREATE INDEX idx_confirmaciones_killswitch_sesion ON aula.confirmaciones_killswitch(sesion_id);
