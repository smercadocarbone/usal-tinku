-- M1 — Recuperación de contraseña ("olvidé mi contraseña").
-- Token opaco de un solo uso, NUNCA un JWT: JwtAuthenticationFilter acepta
-- cualquier JWT firmado con la clave compartida como sesión completa (no
-- distingue "propósito" del token), así que reusar esa firma para un reset
-- sería replayable como login. Guardamos solo el HASH (SHA-256, 64 hex) del
-- token real — si se filtra la tabla, no alcanza para resetear nada.
CREATE TABLE identidad.tokens_reset_password (
    id         UUID PRIMARY KEY,
    usuario_id UUID        NOT NULL REFERENCES identidad.usuarios(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expira_en  TIMESTAMPTZ NOT NULL,
    usado_en   TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Consulta de solicitudes vigentes de un usuario (para invalidar las
-- anteriores al emitir una nueva) y limpieza de vencidos.
CREATE INDEX ix_tokens_reset_password_usuario ON identidad.tokens_reset_password(usuario_id);
