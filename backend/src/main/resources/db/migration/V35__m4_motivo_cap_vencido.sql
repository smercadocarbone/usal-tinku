-- PT10 (T02, CAP para tutores de menores): cancelación de clases con menores cuando el
-- Tutor pierde la habilitación (CAP vencido). No se edita V9 (AGENTS §7): se reemplaza el CHECK.
ALTER TABLE reservas.reservas DROP CONSTRAINT reservas_motivo_cancelacion_check;
ALTER TABLE reservas.reservas ADD CONSTRAINT reservas_motivo_cancelacion_check
    CHECK (motivo_cancelacion IN ('voluntaria', 'timeout_pago', 'revocacion_autorizacion', 'sancion', 'cap_vencido'));
