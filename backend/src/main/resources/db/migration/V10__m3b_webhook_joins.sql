-- M3-B — Webhook de LiveKit (joins de participantes), T-M3-02/T-M3-04.
-- Preparado de la mano del plan M3 (3.1): el job de no-show T+10 (T-M3-04) no
-- puede decidir "no se unió nadie" contando sobre nada — necesita saber cuándo
-- (y quién) entró a la sala. `participant_joined` de LiveKit llega con media
-- activa, así que el primer join de cada lado es la marca confiable.
--
-- Quiénes son "tutor" y "estudiante" se resuelve contra la Reserva (reservas.reservas
-- via reserva_id): el beneficiario de la Reserva es quien entra como estudiante.
ALTER TABLE aula.sesiones_aprendizaje
    ADD COLUMN estudiante_joined_at TIMESTAMPTZ,
    ADD COLUMN tutor_joined_at      TIMESTAMPTZ;