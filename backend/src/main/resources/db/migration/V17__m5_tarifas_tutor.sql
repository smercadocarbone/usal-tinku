-- M5 — Tarifa por sesión del Tutor (Fr-PAG-006, Us-6 de M5)
-- Planeada como Chunk M5-H (configuración de perfil de tarifa del Tutor) y hoy
-- traspasada a este módulo: una fila por Tutor con su precio por sesión
-- vigente. La Reserva lo congela al crearse (FR-PAG-013), nunca se re-congela.
-- Solo el Tutor (o, en el futuro, Tinku) lo actualiza. `updated_at` para el
-- control de cuándo cambió el precio vigente.

SET search_path TO pagos, public;

-- FR-PAG-006: la tarifa del Tutor es fija hasta que él la cambie; el valor solo
-- aplica hacia adelante (las Reservas ya creadas conservan su precio congelado).
-- `tutor_id` es la PK: un Tutor tiene exactamente un precio por sesión vigente.
CREATE TABLE pagos.tarifas_tutor (
    tutor_id      UUID PRIMARY KEY REFERENCES identidad.usuarios(id),
    precio_sesion NUMERIC(10, 2) NOT NULL CHECK (precio_sesion >= 0),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now()
);