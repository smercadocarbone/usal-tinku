-- AUD-009 (FASE2-01, paso 2): V9 comparaba `horario WITH =` (igualdad exacta), así que una
-- reserva a las 10:00 y otra a las 10:30 del mismo tutor no chocaban aunque se superpusieran.
-- Con bloques de 30' las reservas contiguas son el caso normal y hay que distinguirlas del
-- solapamiento real: rango semiabierto [inicio, fin). horario_fin viene de V29 (una EXCLUDE no
-- acepta horario + interval: la suma no es IMMUTABLE). btree_gist ya existe (V9).
ALTER TABLE reservas.reservas DROP CONSTRAINT ex_reservas_sin_superposicion_tutor;
ALTER TABLE reservas.reservas DROP CONSTRAINT ex_reservas_sin_superposicion_beneficiario;

ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_rango_tutor
    EXCLUDE USING gist (tutor_id WITH =, tstzrange(horario, horario_fin, '[)') WITH &&)
    WHERE (estado <> 'cancelada');
ALTER TABLE reservas.reservas ADD CONSTRAINT ex_reservas_rango_beneficiario
    EXCLUDE USING gist (beneficiario_id WITH =, tstzrange(horario, horario_fin, '[)') WITH &&)
    WHERE (estado <> 'cancelada');
