-- M4 — Sistema de Reservas y Agenda
-- Ver Plan_M4_Reservas_Agenda.md, seccion 1 (modelo de datos logico).
-- ADR-M4-01 (resuelto en el sprint de implementacion): la franja puede ser
-- `dia_semana` (recurrente semanal, 0=domingo..6=sabado) o `fecha_especifica`
-- (puntual); la constraint chk_franja_modo exige exactamente uno de los dos.

SET search_path TO reservas, public;

-- FR-RES-012: el Tutor publica franjas; solo se reserva dentro de ellas.
-- La validacion de "dentro de la franja" se resuelve en aplicacion; aca solo
-- se modela la estructura y la no-superposicion de modo de publicacion.
CREATE TABLE reservas.franjas_disponibilidad (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tutor_id         UUID NOT NULL REFERENCES identidad.usuarios(id),
    dia_semana       SMALLINT CHECK (dia_semana BETWEEN 0 AND 6),
    fecha_especifica DATE,
    hora_inicio      TIME NOT NULL,
    hora_fin         TIME NOT NULL CHECK (hora_fin > hora_inicio),
    activa           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_franja_modo CHECK (
        (dia_semana IS NOT NULL AND fecha_especifica IS NULL)
        OR (dia_semana IS NULL AND fecha_especifica IS NOT NULL)
    )
);

CREATE INDEX idx_franjas_tutor_activa ON reservas.franjas_disponibilidad(tutor_id, activa);

-- FR-RES-021/022 (US-2/3): la Solicitud de Sesion es la via "liviana" del menor
-- — no bloquea horario, no genera cobro, no es una Reserva. `expira_at` la setea
-- la aplicacion (created_at + 48hs, Tabla_Tiempos) y el job de T-M4-03 la expira.
CREATE TABLE reservas.solicitudes_sesion (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    menor_id          UUID NOT NULL REFERENCES identidad.usuarios(id),
    tutor_id          UUID NOT NULL REFERENCES identidad.usuarios(id),
    horario_propuesto TIMESTAMPTZ NOT NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'pendiente'
                      CHECK (estado IN ('pendiente', 'convertida', 'expirada', 'rechazada')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_at         TIMESTAMPTZ NOT NULL CHECK (expira_at > created_at)
);

CREATE INDEX idx_solicitudes_menor_estado ON reservas.solicitudes_sesion(menor_id, estado);
CREATE INDEX idx_solicitudes_expiracion ON reservas.solicitudes_sesion(estado, expira_at);

-- US-3/US-4 (FR-RES-001/003/013/017...): la Reserva la crea exclusivamente quien
-- tiene capacidad de pago (Estudiante adulto o Adulto Responsable). `precio` queda
-- congelado al crearla (FR-PAG-013 de M5). `motivo_cancelacion` solo tiene sentido
-- en estado `cancelada` (resuelve E-05 de la ronda de QA — auditoria de por que).
CREATE TABLE reservas.reservas (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pagador_id          UUID NOT NULL REFERENCES identidad.usuarios(id),
    beneficiario_id     UUID NOT NULL REFERENCES identidad.usuarios(id),
    tutor_id            UUID NOT NULL REFERENCES identidad.usuarios(id),
    solicitud_origen_id UUID REFERENCES reservas.solicitudes_sesion(id),
    horario             TIMESTAMPTZ NOT NULL,
    precio              NUMERIC(10,2) NOT NULL CHECK (precio >= 0),
    estado              VARCHAR(20) NOT NULL DEFAULT 'pendiente_pago'
                        CHECK (estado IN ('pendiente_pago', 'confirmada', 'en_curso',
                                          'finalizada', 'cancelada', 'no_show_estudiante',
                                          'no_show_tutor', 'no_show_doble')),
    motivo_cancelacion  VARCHAR(30)
                        CHECK (motivo_cancelacion IN ('voluntaria', 'timeout_pago',
                                                      'revocacion_autorizacion', 'sancion')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_motivo_solo_si_cancelada CHECK (
        (estado = 'cancelada' AND motivo_cancelacion IS NOT NULL)
        OR (estado <> 'cancelada' AND motivo_cancelacion IS NULL)
    )
);

CREATE INDEX idx_reservas_tutor_horario ON reservas.reservas(tutor_id, horario);
CREATE INDEX idx_reservas_beneficiario_horario ON reservas.reservas(beneficiario_id, horario);

-- FR-RES-007 (T-M4-11): prevencion de reservas superpuestas a nivel de BASE, no
-- solo de aplicacion — una condicion de carrera entre dos requests simultaneos no
-- puede crear dos reservas para el mismo tutor (o beneficiario) en el mismo
-- horario. btree_gist aporta el opclass `=` para uuid/timestamptz sobre GiST.
-- `WHERE estado <> 'cancelada'`: al cancelarse, la fila deja de ocupar el horario
-- (Plan 2.3 — "libera el horario"); el resto de estados (incl. pendiente_pago)
-- bloquea mientras viva.
CREATE EXTENSION IF NOT EXISTS btree_gist SCHEMA public;

ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_sin_superposicion_tutor
    EXCLUDE USING gist (tutor_id WITH =, horario WITH =) WHERE (estado <> 'cancelada');

ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_sin_superposicion_beneficiario
    EXCLUDE USING gist (beneficiario_id WITH =, horario WITH =) WHERE (estado <> 'cancelada');

-- FK desde M3 (V8): `aula.sesiones_aprendizaje.reserva_id` no podia apuntar a
-- `reservas.reservas` cuando esa tabla no existia. Se agrega aca como constraint
-- en una migracion NUEVA (AGENTS seccion 7 — V8 ya aplicada no se edita).
ALTER TABLE aula.sesiones_aprendizaje
    ADD CONSTRAINT fk_sesiones_reserva
    FOREIGN KEY (reserva_id) REFERENCES reservas.reservas(id);