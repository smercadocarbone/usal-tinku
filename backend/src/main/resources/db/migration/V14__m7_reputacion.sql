-- M7 — Sistema de Calificaciones y Reputacion
-- Ver Plan_M7_Reputacion.md, seccion 1 (modelo de datos logico).

SET search_path TO reputacion, public;

-- Calificaciones: una por sesion, por autor, por direccion.
-- `direccion` se deriva del rol del autor (Plan 2.2), nunca de input libre.
-- `comentario` solo aplica a `estudiante_a_tutor` (calificacion oculta sin
-- comentario publico). `editable_hasta` = created_at + 48hs (FR-REP-005).
CREATE TABLE reputacion.calificaciones (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id       UUID NOT NULL REFERENCES aula.sesiones_aprendizaje(id),
    autor_id        UUID NOT NULL REFERENCES identidad.usuarios(id),
    direccion       VARCHAR(30) NOT NULL CHECK (direccion IN ('estudiante_a_tutor', 'tutor_a_estudiante')),
    estrellas       SMALLINT NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
    comentario      VARCHAR(1000),
    editable_hasta  TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_calificacion_por_sesion_autor_direccion UNIQUE (sesion_id, autor_id, direccion)
);

CREATE INDEX idx_calificaciones_sesion ON reputacion.calificaciones(sesion_id);
CREATE INDEX idx_calificaciones_tutor_direccion ON reputacion.calificaciones(direccion);

-- Senales implicitas de comportamiento del Tutor (ADR-M7-01: agregado
-- recalculado incrementalmente, no log crudo). Los pesos que alimentan el
-- matching (FR-MATCH-003) se calculan a partir de estos valores en el
-- servicio de senales, no en este esquema.
CREATE TABLE reputacion.senales_implicitas_tutor (
    tutor_id                        UUID PRIMARY KEY REFERENCES identidad.usuarios(id),
    puntualidad_promedio            DECIMAL(5,4) NOT NULL DEFAULT 1.0,
    tasa_recontratacion             DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    tasa_cancelacion_noshow         DECIMAL(5,4) NOT NULL DEFAULT 0.0,
    tiempo_respuesta_promedio_min   INT NOT NULL DEFAULT 0,
    sesiones_dictadas_total         INT NOT NULL DEFAULT 0,
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now()
);
