-- AUD-025 (FASE 3, 3.4): dos franjas activas del mismo Tutor no pueden pisarse en el mismo
-- día. Antes FranjaService.franjaQueCubre resolvía la ambigüedad con findFirst() en orden
-- arbitrario. La app valida también el cruce semanal↔puntual (que una constraint no puede
-- expresar); acá quedan los choques dentro de un mismo modo.
--
-- Primero, las superposiciones que ya existan: se desactiva la más nueva de cada par (la
-- más vieja es la que los Estudiantes ya venían viendo). No se borra nada.
UPDATE reservas.franjas_disponibilidad nueva
   SET activa = FALSE
  FROM reservas.franjas_disponibilidad vieja
 WHERE nueva.activa AND vieja.activa
   AND nueva.tutor_id = vieja.tutor_id
   AND nueva.id <> vieja.id
   AND (nueva.dia_semana = vieja.dia_semana OR nueva.fecha_especifica = vieja.fecha_especifica)
   AND nueva.hora_inicio < vieja.hora_fin AND vieja.hora_inicio < nueva.hora_fin
   AND (nueva.ctid > vieja.ctid);

-- tsrange sobre una fecha fija: TIME no tiene tipo de rango propio. DATE + TIME es inmutable.
ALTER TABLE reservas.franjas_disponibilidad ADD CONSTRAINT ex_franjas_semanal_sin_superposicion
    EXCLUDE USING gist (
        tutor_id WITH =,
        dia_semana WITH =,
        tsrange(DATE '2000-01-01' + hora_inicio, DATE '2000-01-01' + hora_fin, '[)') WITH &&
    ) WHERE (activa AND dia_semana IS NOT NULL);

ALTER TABLE reservas.franjas_disponibilidad ADD CONSTRAINT ex_franjas_puntual_sin_superposicion
    EXCLUDE USING gist (
        tutor_id WITH =,
        fecha_especifica WITH =,
        tsrange(fecha_especifica + hora_inicio, fecha_especifica + hora_fin, '[)') WITH &&
    ) WHERE (activa AND fecha_especifica IS NOT NULL);
