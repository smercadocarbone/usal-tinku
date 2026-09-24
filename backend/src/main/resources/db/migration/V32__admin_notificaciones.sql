-- FASE2-03 / AUD-014: outbox de notificaciones. La tabla ES la bandeja in-app
-- (el usuario ve sus avisos al entrar) y la cola del envío por email (ADR-000-06).
-- La fila se escribe en la MISMA transacción que el hecho que la origina: si el
-- hecho se revierte, el aviso también.
CREATE TABLE admin.notificaciones (
    id                       UUID PRIMARY KEY,
    destinatario_id          UUID        NOT NULL REFERENCES identidad.usuarios (id),
    tipo                     VARCHAR(40) NOT NULL,
    datos                    JSONB       NOT NULL DEFAULT '{}',
    creada_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    leida_at                 TIMESTAMPTZ NULL,
    -- Email (ADR-000-06): pendiente mientras enviada_email_at y email_descartado_at
    -- sean NULL y proximo_intento_email_at haya llegado. Backoff 5/15/60 min
    -- (Tabla de Tiempos); agotados o sin email del destinatario → descartado.
    enviada_email_at         TIMESTAMPTZ NULL,
    intentos_email           INTEGER     NOT NULL DEFAULT 0,
    proximo_intento_email_at TIMESTAMPTZ NULL,
    email_descartado_at      TIMESTAMPTZ NULL
);

CREATE INDEX ix_notificaciones_destinatario ON admin.notificaciones (destinatario_id, creada_at DESC);
CREATE INDEX ix_notificaciones_email_pendiente ON admin.notificaciones (proximo_intento_email_at)
    WHERE enviada_email_at IS NULL AND email_descartado_at IS NULL;
