-- T-TES-11b (docs/superpowers/specs/tesis/T11b-evaluacion-sin-piloto.md §4): instrumentación mínima
-- para las sesiones moderadas que reemplazan al piloto. Solo se escribe con
-- tinku.evaluacion.habilitada=true (default false).
--
-- Minimización (Constitución Art. V): de la búsqueda NO se guarda el texto libre ni un hash (un hash
-- de un texto corto se revierte por diccionario), solo su longitud. Las búsquedas de cuentas Menor
-- no se registran (Art. II). Retención: sin plazo automático; lo decide el dueño (T11b §4.4).

-- I-01: qué Tutores se mostraron, en qué orden.
CREATE TABLE matching.evaluacion_busquedas (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id           UUID NOT NULL REFERENCES identidad.usuarios(id),
    longitud_texto       INTEGER NOT NULL CHECK (longitud_texto >= 0),
    cantidad_resultados  INTEGER NOT NULL CHECK (cantidad_resultados >= 0),
    tutor_ids            UUID[] NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evaluacion_busquedas_created ON matching.evaluacion_busquedas(created_at);

-- I-01: el usuario abrió el perfil o reservó un Tutor sugerido. La posición (1 = primero) la
-- calcula el servidor desde tutor_ids, nunca viene del cliente.
CREATE TABLE matching.evaluacion_aceptaciones (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    busqueda_id  UUID NOT NULL REFERENCES matching.evaluacion_busquedas(id) ON DELETE CASCADE,
    tutor_id     UUID NOT NULL REFERENCES identidad.usuarios(id),
    posicion     INTEGER NOT NULL CHECK (posicion >= 1),
    tipo         VARCHAR(10) NOT NULL CHECK (tipo IN ('PERFIL', 'RESERVA')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_evaluacion_aceptacion UNIQUE (busqueda_id, tutor_id, tipo)
);

-- I-04: NPS de intención, una respuesta por usuario.
CREATE TABLE admin.nps_respuestas (
    usuario_id  UUID PRIMARY KEY REFERENCES identidad.usuarios(id),
    puntaje     SMALLINT NOT NULL CHECK (puntaje BETWEEN 0 AND 10),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_nps_respuestas_created ON admin.nps_respuestas(created_at);
