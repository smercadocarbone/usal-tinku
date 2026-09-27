-- Enmienda v2.5 (2026-09-26): paquete mensual (FR-RES-032..037, FR-PAG-021..023, ADR-M5-03).

-- El Tutor lo ofrece desde su tarifa, con un descuento opcional de 0 a 30 %.
ALTER TABLE pagos.tarifas_tutor
    ADD COLUMN paquete_habilitado           BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN paquete_descuento_porcentaje INTEGER NOT NULL DEFAULT 0
        CHECK (paquete_descuento_porcentaje BETWEEN 0 AND 30);

-- Un paquete = 4 clases semanales pagadas juntas. Cada clase es una Reserva normal con
-- paquete_id; la primera es la "ancla" del pago (external_reference de la preferencia).
CREATE TABLE reservas.paquetes (
    id                    UUID PRIMARY KEY,
    pagador_id            UUID NOT NULL REFERENCES identidad.usuarios (id),
    beneficiario_id       UUID NOT NULL REFERENCES identidad.usuarios (id),
    tutor_id              UUID NOT NULL REFERENCES identidad.usuarios (id),
    reserva_ancla_id      UUID,
    cantidad_clases       INTEGER NOT NULL CHECK (cantidad_clases > 0),
    duracion_minutos      INTEGER NOT NULL,
    descuento_porcentaje  INTEGER NOT NULL CHECK (descuento_porcentaje BETWEEN 0 AND 30),
    precio_total          NUMERIC(10, 2) NOT NULL CHECK (precio_total >= 0),
    estado                VARCHAR(20) NOT NULL CHECK (estado IN ('pendiente_pago', 'confirmado', 'cancelado')),
    vigente_hasta         TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE reservas.reservas ADD COLUMN paquete_id UUID REFERENCES reservas.paquetes (id);
CREATE INDEX idx_reservas_paquete ON reservas.reservas (paquete_id) WHERE paquete_id IS NOT NULL;

ALTER TABLE reservas.paquetes
    ADD CONSTRAINT fk_paquetes_reserva_ancla FOREIGN KEY (reserva_ancla_id) REFERENCES reservas.reservas (id);
