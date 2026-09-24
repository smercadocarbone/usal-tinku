-- FASE2-05 (AUD-029) — estado de conexión del par, para la desconexión real.
-- El webhook ya registraba "quién entró" (V10, joinedAt); ahora registra "quién
-- está conectado ahora" (tutor_conectado / estudiante_conectado) y desde cuándo
-- el par está roto (par_roto_at). Con eso el CorteAutomaticoJob calcula la
-- duración efectiva contra el instante real de desconexión, no contra
-- Instant.now() cuando corre (fin agendado + 5 min) — antes facturaba como
-- "clase de 125 min" una sesión de 120 min donde ambos se fueron a los 5 min.
--
-- par_roto_at NULL = están los dos o todavía no empezó. No hay cierre inmediato
-- al salir ambos: un microcorte de red de los dos no termina la clase (Spec
-- fase2-05 §2); solo queda registrado el hecho para que el corte ya existente
-- decida con la duración real.
ALTER TABLE aula.sesiones_aprendizaje
    ADD COLUMN tutor_conectado      BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN estudiante_conectado BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN par_roto_at          TIMESTAMPTZ;