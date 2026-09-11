-- M9 — Denuncias, Seguridad y Moderación (Spec_M9, Plan_M9 §1).
-- Tracks separados: denuncias estándar (descargo 48hs + SLA 5 días hábiles) y
-- alertas_seguridad (viven en aula, BR-KS-03 — acá solo la resolución).
-- Enums con nombre en mayúsculas (convención @Enumerated(EnumType.STRING)).

CREATE TABLE seguridad.denuncias (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    denunciante_id          UUID NOT NULL REFERENCES identidad.usuarios(id),
    denunciado_id           UUID NOT NULL REFERENCES identidad.usuarios(id),
    sesion_id               UUID REFERENCES aula.sesiones_aprendizaje(id),
    motivo                  VARCHAR(40) NOT NULL
                            CHECK (motivo IN ('COMPORTAMIENTO_INAPROPIADO', 'INCUMPLIMIENTO',
                                              'FRAUDE', 'CONTENIDO_ILEGAL', 'ACOSO')),
    evidencia_url           VARCHAR(500),
    estado                  VARCHAR(30) NOT NULL DEFAULT 'REGISTRADA'
                            CHECK (estado IN ('REGISTRADA', 'EN_REVISION',
                                              'RESUELTA_INFUNDADA', 'RESUELTA_FUNDADA', 'ESCALADA')),
    descargo_texto          VARCHAR(300),          -- descargo del denunciado (FR-SEC-006, 300 caracteres)
    descargo_recibido_at    TIMESTAMPTZ,
    descargo_vence_at       TIMESTAMPTZ,           -- en_revision + 48hs (Tabla_Tiempos)
    sla_resolucion_vence_at TIMESTAMPTZ,           -- descargo_vence_at + 5 días hábiles (FR-SEC-010)
    prioridad_alta          BOOLEAN NOT NULL DEFAULT FALSE, -- marcado por el job de SLA (no se auto-resuelve, Plan §2.3)
    admin_resolutor_id      UUID REFERENCES identidad.usuarios(id),
    resuelta_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_denuncias_estado ON seguridad.denuncias(estado);
CREATE INDEX idx_denuncias_sesion ON seguridad.denuncias(sesion_id);
CREATE INDEX idx_denuncias_denunciado ON seguridad.denuncias(denunciado_id);

CREATE TABLE seguridad.sanciones (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_sancionado_id UUID NOT NULL REFERENCES identidad.usuarios(id),
    origen               VARCHAR(30) NOT NULL CHECK (origen IN ('DENUNCIA', 'ALERTA_SEGURIDAD')),
    denuncia_id          UUID REFERENCES seguridad.denuncias(id),
    alerta_id            UUID REFERENCES aula.alertas_seguridad(id),
    tipo                 VARCHAR(30) NOT NULL
                         CHECK (tipo IN ('ADVERTENCIA', 'SUSPENSION_TEMPORAL',
                                         'SUSPENSION_DEFINITIVA', 'BANEO_AUTORIDADES')),
    dias_suspension      INT CHECK (dias_suspension IN (7, 15, 30)), -- solo si tipo = SUSPENSION_TEMPORAL (Plan §1)
    vigente_desde        TIMESTAMPTZ,
    vigente_hasta        TIMESTAMPTZ,    -- nulo si definitiva/baneo (Plan §1)
    admin_id             UUID NOT NULL REFERENCES identidad.usuarios(id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- cada sanción referencia exactamente un origen (denuncia XOR alerta).
    CHECK ((origen = 'DENUNCIA' AND denuncia_id IS NOT NULL AND alerta_id IS NULL)
        OR (origen = 'ALERTA_SEGURIDAD' AND alerta_id IS NOT NULL AND denuncia_id IS NULL)),
    -- días_suspension es exclusivo del tipo SUSPENSION_TEMPORAL.
    CHECK ((tipo = 'SUSPENSION_TEMPORAL' AND dias_suspension IS NOT NULL AND vigente_hasta IS NOT NULL)
        OR (tipo != 'SUSPENSION_TEMPORAL' AND dias_suspension IS NULL))
);

CREATE INDEX idx_sanciones_usuario ON seguridad.sanciones(usuario_sancionado_id);
CREATE INDEX idx_sanciones_origen ON seguridad.sanciones(origen, denuncia_id, alerta_id);

-- US-2/FR-SEC-004: el descargo del Tutor se adjunta a la Alerta en cualquier
-- momento (apelación), pero nunca bloquea la resolución de 12hs. El resto de
-- los campos de alerta ya viven en V8 (BR-KS-03).
ALTER TABLE aula.alertas_seguridad
    ADD COLUMN descargo_texto       VARCHAR(300),
    ADD COLUMN descargo_recibido_at TIMESTAMPTZ;