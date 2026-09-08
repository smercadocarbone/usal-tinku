-- M5 — Motor de Pagos
-- Ver Plan_M5_Motor_Pagos.md, seccion 1 (modelo de datos logico).
-- ADR-M5-01 (pendiente, Plan §5): el ambiente de pruebas de MercadoPago se
-- define antes de escribir tests de integracion contra el provider real; esta
-- migracion es solo estructura local y no depende de esa decision.

SET search_path TO pagos, public;

-- FR-PAG-001/002/003/007 (escrow): una fila por cobro retenido. La crea el
-- webhook de MercadoPago (Chunk M5-B) cuando el pago queda aprobado y la Reserva
-- pasa a `confirmada`. El vinculo es DIRECTO con la Reserva, nunca con la Sesion
-- (corrige la contradiccion E-06/E-07/E-18 del informe de QA — Plan §1).
-- `liberar_at` = timestamp_fin de la sesion + 24h (FR-PAG-002, nullable hasta
-- que la sesion finalice); `intentos_liberacion` cuenta fallos del job de
-- liberacion (maximo 3 antes de intervencion manual, FR-PAG-007).
CREATE TABLE pagos.transacciones (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reserva_id          UUID NOT NULL REFERENCES reservas.reservas(id),
    mp_payment_id       VARCHAR        NOT NULL,
    monto_bruto         NUMERIC(10, 2) NOT NULL CHECK (monto_bruto >= 0),
    comision_plataforma NUMERIC(10, 2) NOT NULL
                        CHECK (comision_plataforma >= 0 AND comision_plataforma <= monto_bruto),
    estado              VARCHAR(20) NOT NULL DEFAULT 'retenido_escrow'
                        CHECK (estado IN ('retenido_escrow', 'liberado',
                                          'reembolsado', 'pausado_denuncia')),
    liberar_at          TIMESTAMPTZ,
    intentos_liberacion INT NOT NULL DEFAULT 0 CHECK (intentos_liberacion >= 0),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transacciones_reserva ON pagos.transacciones(reserva_id);
CREATE INDEX idx_transacciones_escrow_liberar ON pagos.transacciones(estado, liberar_at);

-- FR-ADM-007 (M8): precio de referencia regional por provincia. PK (provincia,
-- version): el Plan marca a `provincia` como PK pero su propia nota ("nunca se
-- sobreescribe la fila, para no afectar retroactivamente a Tutores que ya
-- fijaron su precio con una version anterior") exige acumular versiones, no
-- actualizar una sola fila. M5-E consulta la de mayor `version` por provincia
-- como vigente (Plan §3.4 — el valor se COPIA al perfil del Tutor, sin FK).
CREATE TABLE pagos.precios_referencia_regional (
    provincia      VARCHAR         NOT NULL,
    version        INT             NOT NULL CHECK (version >= 1),
    valor_sugerido NUMERIC(10, 2)  NOT NULL,
    vigente_desde  TIMESTAMPTZ     NOT NULL,
    PRIMARY KEY (provincia, version)
);