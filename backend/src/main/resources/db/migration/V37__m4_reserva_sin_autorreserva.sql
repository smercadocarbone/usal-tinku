-- Revisión por rol (R1, docs/spikes/DISENO-soluciones-revision-por-rol.md): una cuenta de Tutor
-- puede tener además capacidades de alumno/Adulto Responsable, pero nunca reservarse a sí misma.
-- ReservaService.exigirTutorReservable ya lo bloquea (422); esto es la red de la base.
-- "Tutor = Adulto Responsable del beneficiario" cruza tablas: lo cubre solo el servicio.
ALTER TABLE reservas.reservas
    ADD CONSTRAINT ck_reservas_tutor_no_es_parte
    CHECK (tutor_id <> pagador_id AND tutor_id <> beneficiario_id);
