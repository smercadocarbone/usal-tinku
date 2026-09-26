-- FR-MATCH-012 / FR-ADM-009 (ADR-M2-04): temas que se buscan y el catálogo no cubre.
-- Un contador por texto normalizado — no un registro de búsquedas: sin usuario, sin una fila
-- por búsqueda, sin búsquedas de menores. El servicio lo mantiene en 500 filas como máximo.
CREATE TABLE matching.temas_sugeridos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    texto       TEXT NOT NULL UNIQUE,
    veces       INT NOT NULL DEFAULT 1,
    nivel       TEXT,                 -- área reconocida (tema del catálogo más parecido)
    materia     TEXT,
    ultima_vez  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_temas_sugeridos_veces ON matching.temas_sugeridos (veces DESC, ultima_vez DESC);
