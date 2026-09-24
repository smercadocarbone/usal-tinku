-- AUD-020 (FASE2-01, paso 1): la Reserva guarda su propia duración (D6: bloques de 30 min, 30 a 180).
-- horario_fin existe porque Postgres no acepta horario + interval en una EXCLUDE (la suma es
-- STABLE, no IMMUTABLE): la exclusión por rango del paso 2 (AUD-009) se apoya en esta columna.
ALTER TABLE reservas.reservas ADD COLUMN duracion_minutos INT;
ALTER TABLE reservas.reservas ADD COLUMN horario_fin TIMESTAMPTZ;

-- 1) Si la Sesión existe, su duración agendada es la mejor fuente.
UPDATE reservas.reservas r
   SET duracion_minutos = s.duracion_agendada_segundos / 60
  FROM aula.sesiones_aprendizaje s
 WHERE s.reserva_id = r.id AND s.duracion_agendada_segundos IS NOT NULL;

-- 2) Si no, la franja PUNTUAL que cubre el horario (hora argentina).
UPDATE reservas.reservas r
   SET duracion_minutos = EXTRACT(EPOCH FROM (f.hora_fin - f.hora_inicio))::int / 60
  FROM reservas.franjas_disponibilidad f
 WHERE r.duracion_minutos IS NULL
   AND f.tutor_id = r.tutor_id
   AND f.fecha_especifica = (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::date
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time >= f.hora_inicio
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time <  f.hora_fin;

-- 3) Si no, la franja SEMANAL (dia_semana: 0 = domingo, igual que EXTRACT(DOW)).
UPDATE reservas.reservas r
   SET duracion_minutos = EXTRACT(EPOCH FROM (f.hora_fin - f.hora_inicio))::int / 60
  FROM reservas.franjas_disponibilidad f
 WHERE r.duracion_minutos IS NULL
   AND f.tutor_id = r.tutor_id
   AND f.dia_semana = EXTRACT(DOW FROM (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires'))
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time >= f.hora_inicio
   AND (r.horario AT TIME ZONE 'America/Argentina/Buenos_Aires')::time <  f.hora_fin;

-- 4) Normalizar a D6: múltiplo de 30, entre 30 y 180. Resto (sin fuente) = 30, la duración
--    mínima de la Tabla de Tiempos (única fuente permitida, A3).
UPDATE reservas.reservas
   SET duracion_minutos = GREATEST(30, LEAST(180, (COALESCE(duracion_minutos, 30) / 30) * 30));

UPDATE reservas.reservas SET horario_fin = horario + make_interval(mins => duracion_minutos);

ALTER TABLE reservas.reservas ALTER COLUMN duracion_minutos SET NOT NULL;
ALTER TABLE reservas.reservas ALTER COLUMN horario_fin SET NOT NULL;
ALTER TABLE reservas.reservas ADD CONSTRAINT chk_reservas_duracion
    CHECK (duracion_minutos BETWEEN 30 AND 180 AND duracion_minutos % 30 = 0);
ALTER TABLE reservas.reservas ADD CONSTRAINT chk_reservas_fin_posterior
    CHECK (horario_fin > horario);

-- T3: la Solicitud del menor también lleva su duración (se congela al aprobarla).
ALTER TABLE reservas.solicitudes_sesion ADD COLUMN duracion_minutos INT;
UPDATE reservas.solicitudes_sesion SET duracion_minutos = 30 WHERE duracion_minutos IS NULL;
ALTER TABLE reservas.solicitudes_sesion ALTER COLUMN duracion_minutos SET NOT NULL;
ALTER TABLE reservas.solicitudes_sesion ADD CONSTRAINT chk_solicitudes_duracion
    CHECK (duracion_minutos BETWEEN 30 AND 180 AND duracion_minutos % 30 = 0);
