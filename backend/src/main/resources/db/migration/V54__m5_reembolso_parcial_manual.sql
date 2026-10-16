-- FR-PAG-010: el reembolso parcial manual de Soporte Financiero queda registrado en la
-- transacción. Antes no se guardaba nada: dos envíos seguidos del mismo formulario
-- devolvían dos veces. Uno por transacción; la clave de idempotencia hacia MercadoPago es
-- 'parcial-{transaccionId}'. Columnas nuevas: no se edita V11/V24 (AGENTS.md §7).
ALTER TABLE pagos.transacciones
    ADD COLUMN reembolso_parcial_monto NUMERIC(10, 2),
    ADD COLUMN reembolso_parcial_at    TIMESTAMPTZ;
