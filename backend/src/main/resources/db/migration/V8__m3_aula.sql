-- M3 — Aula Virtual
-- Ver Plan_M3_Aula_Virtual.md, seccion 2 (modelo de datos logico).
-- JobStore de Quartz: los timeouts del modulo (T-5, T+10, tolerancia +5) no son
-- schedules en memoria — se programan en el JOB_STORE persistido (V3, Articulo IV/X).

SET search_path TO aula, public;

-- Ciclo de vida de la Sesion de Aprendizaje (US-1 a US-8, FR-AULA-001/005/007).
-- `reserva_id` es 1:1 con reservas.reservas (M4) pero NO se crea la FK aca:
-- la tabla de M4 todavia no existe al aplicar esta migracion. La FK se agrega
-- como constraint en la migracion de M4 (ALTER TABLE ... ADD CONSTRAINT), en una
-- migracion NUEVA — esta migracion ya aplicada nunca se edita (AGENTS seccion 7).
CREATE TABLE aula.sesiones_aprendizaje (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id                  UUID NOT NULL UNIQUE,
    livekit_room_id             VARCHAR(100),           -- null hasta T-5 (creacion diferida, US-1)
    estado                      VARCHAR(30) NOT NULL DEFAULT 'no_iniciada'
                                CHECK (estado IN ('no_iniciada', 'en_curso', 'finalizada',
                                                  'finalizada_anticipada', 'interrumpida')),
    inicio_real                 TIMESTAMPTZ,            -- para el % de duracion efectiva (regla del 50%, US-5)
    fin_real                    TIMESTAMPTZ,
    duracion_efectiva_segundos  INT,                    -- calculo al finalizar; M6 usa el umbral de 10 min
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sesiones_aprendizaje_estado ON aula.sesiones_aprendizaje(estado);

-- Alerta de Seguridad del kill-switch (US-6/US-7, BR-KS-01 a BR-KS-04).
-- Entidad separada de Denuncia (BR-KS-03): la recorre M9 en su ventana de 12hs
-- (Tabla_Tiempos) y la resuelve su propio flujo, sin esperar descargo.
-- `rama`: la decide el backend con datos de M1, nunca un flag del cliente (Plan 3.3.2).
CREATE TABLE aula.alertas_seguridad (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sesion_id               UUID NOT NULL REFERENCES aula.sesiones_aprendizaje(id),
    rama                    VARCHAR(10) NOT NULL CHECK (rama IN ('menor', 'adultos')),
    detectado_id            UUID NOT NULL REFERENCES identidad.usuarios(id),
    clip_url                VARCHAR(500),               -- se completa al subir el clip (T-M3-08)
    clip_retencion_hasta    TIMESTAMPTZ,                -- BR-KS-02: 30 dias desde la RESOLUCION (lo fija M9 al resolver)
    estado                  VARCHAR(30) NOT NULL DEFAULT 'pendiente_revision'
                            CHECK (estado IN ('pendiente_revision', 'resuelta_reactivacion', 'resuelta_baja')),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_alertas_seguridad_sesion ON aula.alertas_seguridad(sesion_id);
CREATE INDEX idx_alertas_seguridad_estado ON aula.alertas_seguridad(estado);