-- Enmienda v2.5 (2026-09-26): pedido de reprogramación del Tutor (FR-RES-029..031).
-- Uno solo pendiente por Reserva; el historial (aceptados, rechazados, vencidos, retirados) queda.

CREATE TABLE reservas.pedidos_reprogramacion (
    id                 UUID PRIMARY KEY,
    reserva_id         UUID NOT NULL REFERENCES reservas.reservas (id),
    horario_original   TIMESTAMPTZ NOT NULL,
    horario_propuesto  TIMESTAMPTZ NOT NULL,
    motivo             VARCHAR(300),
    estado             VARCHAR(20) NOT NULL
        CHECK (estado IN ('pendiente', 'aceptado', 'rechazado', 'vencido', 'retirado')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    resuelto_at        TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_pedido_reprogramacion_pendiente
    ON reservas.pedidos_reprogramacion (reserva_id) WHERE estado = 'pendiente';
