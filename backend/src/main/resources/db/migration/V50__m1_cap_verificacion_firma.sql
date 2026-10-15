-- ADR-M1-10 — Verificación de la firma digital del CAP con el validador oficial
-- (validadordefirmas.gob.ar), por una cola persistida que manda los pedidos de a uno y
-- espaciados. Columnas nuevas: no se edita V6 (AGENTS.md §7).
--
--  * firma_estado: NO_VERIFICADA (validador apagado o CAP fuera de revisión), PENDIENTE (en
--    cola), EN_PROCESO (enviado, esperando el resultado), VALIDA, INVALIDA,
--    FIRMANTE_NO_RECONOCIDO, ERROR (agotó los reintentos).
--  * firma_sha256: hash del archivo que se envió; el resultado solo vale si el validador
--    informa el mismo hash.
--  * No se guarda la respuesta completa del validador: solo el firmante y un detalle corto
--    (minimización, Art. V).

ALTER TABLE identidad.certificados_antecedentes_penales
    ADD COLUMN firma_estado             VARCHAR(30) NOT NULL DEFAULT 'NO_VERIFICADA'
        CHECK (firma_estado IN ('NO_VERIFICADA', 'PENDIENTE', 'EN_PROCESO', 'VALIDA', 'INVALIDA',
                                'FIRMANTE_NO_RECONOCIDO', 'ERROR')),
    ADD COLUMN firma_detalle            VARCHAR(500),
    ADD COLUMN firma_firmante           VARCHAR(300),
    ADD COLUMN firma_sha256             VARCHAR(64),
    ADD COLUMN firma_solicitud_id       VARCHAR(120),
    ADD COLUMN firma_intentos           INT NOT NULL DEFAULT 0,
    ADD COLUMN firma_proximo_intento_at TIMESTAMPTZ,
    ADD COLUMN firma_enviada_at         TIMESTAMPTZ,
    ADD COLUMN firma_verificada_at      TIMESTAMPTZ;

-- Los CAP que todavía esperan revisión entran a la cola.
UPDATE identidad.certificados_antecedentes_penales
   SET firma_estado = 'PENDIENTE', firma_proximo_intento_at = now()
 WHERE estado IN ('PENDIENTE', 'EN_REVISION_LEGAL');

CREATE INDEX idx_cap_firma_cola
    ON identidad.certificados_antecedentes_penales (firma_estado, firma_proximo_intento_at);
