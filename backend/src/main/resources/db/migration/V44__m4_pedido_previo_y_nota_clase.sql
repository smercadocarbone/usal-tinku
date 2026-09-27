-- Enmienda v2.5 (2026-09-26): pedido previo a la clase (FR-RES-027, solo texto desde el
-- 2026-09-27) y nota del Tutor al Adulto Responsable (FR-RES-026). Una fila por Reserva en cada tabla.

CREATE TABLE reservas.pedidos_previos (
    reserva_id  UUID PRIMARY KEY REFERENCES reservas.reservas (id),
    texto       VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reservas.notas_clase (
    reserva_id  UUID PRIMARY KEY REFERENCES reservas.reservas (id),
    texto       VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
