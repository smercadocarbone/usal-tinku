-- R2 (docs/spikes/DISENO-soluciones-revision-por-rol.md §5): Tinku guarda cada preferencia de
-- MercadoPago que genera para poder conciliar el pago aunque no vuelva el navegador ni llegue
-- el webhook. conciliado_at se completa cuando la Reserva ya tiene su Transaccion (confirmada o
-- reembolsada por tardía); el barrido deja de mirarla a las 48 hs (Tabla de Tiempos).
CREATE TABLE pagos.preferencias_pago (
    reserva_id            UUID PRIMARY KEY REFERENCES reservas.reservas(id),
    preference_id         VARCHAR(100) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    conciliado_at         TIMESTAMPTZ,
    intentos_conciliacion INT NOT NULL DEFAULT 0,
    -- Pago aprobado que no se pudo atribuir (monto distinto): se avisa a Soporte una sola vez.
    alertado_at           TIMESTAMPTZ
);
CREATE INDEX idx_preferencias_sin_conciliar ON pagos.preferencias_pago (created_at)
    WHERE conciliado_at IS NULL;
