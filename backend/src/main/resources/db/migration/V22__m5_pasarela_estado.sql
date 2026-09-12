-- M5 — Estado global de la pasarela de pagos (modo Bypass/Salud de Infra).
-- Fila única (id=1 CHECK): el flag es global, no por tenant. Arranca HABILITADA
-- (cobro real MercadoPago); el bypass se activa explícitamente desde M8
-- (PATCH /api/admin/financiero/pasarela, rol Soporte Financiero) y M5 lo lee en
-- cada punto donde toca al proveedor. Sin < 1 fila el default es habilitada=true
-- (fail-closed hacia cobro real: que falte el flag NUNCA puede hacer pasar un
-- cobro por bypass).
CREATE TABLE pagos.pasarela_estado (
    id         SMALLINT PRIMARY KEY CHECK (id = 1),
    habilitada BOOLEAN     NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    updated_by UUID        REFERENCES identidad.usuarios(id)
);

INSERT INTO pagos.pasarela_estado (id, habilitada, updated_at, updated_by)
VALUES (1, TRUE, now(), NULL);

-- Transacciones nacidas en modo Bypass: el escrow existe en el modelo pero NO hay
-- dinero real en MercadoPago — liberación y reembolsos deben ser no-op locales,
-- jamás llamar al proveedor con un id falso.
ALTER TABLE pagos.transacciones ADD COLUMN en_bypass BOOLEAN NOT NULL DEFAULT FALSE;