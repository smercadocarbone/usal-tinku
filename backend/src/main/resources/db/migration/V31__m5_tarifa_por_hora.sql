-- FASE2-01 / D6: la tarifa del tutor pasa a ser POR HORA; la Reserva cotiza
-- precio_hora × duracion_minutos / 60. El valor numérico NO se convierte: una
-- tarifa cargada pensando "por sesión" queda interpretada como "por hora"
-- (advertencia de datos, decisión del producto — ver el reporte de FASE2-01).
-- El CHECK de V17 (precio_sesion >= 0) sigue la columna renombrada.
ALTER TABLE pagos.tarifas_tutor RENAME COLUMN precio_sesion TO precio_hora;
