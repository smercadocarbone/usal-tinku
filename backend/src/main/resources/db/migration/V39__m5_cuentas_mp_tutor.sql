-- R3 / ADR-M5-02 (modelo A, OAuth por Tutor): cada Tutor conecta su cuenta de MercadoPago y
-- Tinku crea las preferencias con SU token, así el marketplace_fee reparte el cobro.
-- Los tokens se guardan cifrados (AES-256-GCM, clave TINKU_CLAVE_CIFRADO fuera de la base).
CREATE TABLE pagos.cuentas_mp_tutor (
    tutor_id               UUID PRIMARY KEY REFERENCES identidad.usuarios(id),
    mp_user_id             VARCHAR(40) NOT NULL,
    access_token_cifrado   BYTEA NOT NULL,
    refresh_token_cifrado  BYTEA,
    public_key             VARCHAR(100),
    expira_at              TIMESTAMPTZ NOT NULL,
    estado                 VARCHAR(20) NOT NULL CHECK (estado IN ('CONECTADA', 'REVOCADA', 'ERROR')),
    conectada_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_cuentas_mp_user ON pagos.cuentas_mp_tutor (mp_user_id) WHERE estado = 'CONECTADA';

-- CSRF (state) y PKCE (code_verifier) del flujo de autorización; vive minutos.
CREATE TABLE pagos.oauth_estados (
    state          VARCHAR(64) PRIMARY KEY,
    tutor_id       UUID NOT NULL REFERENCES identidad.usuarios(id),
    code_verifier  VARCHAR(128) NOT NULL,
    expira_at      TIMESTAMPTZ NOT NULL
);
