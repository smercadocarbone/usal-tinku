package com.tinku.reputacion.repository;

import com.tinku.reputacion.model.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalificacionRepository extends JpaRepository<Calificacion, UUID> {

    /** FR-REP-007: COUNT de calificaciones publicas del Tutor para el umbral de 5. */
    @Query("SELECT COUNT(c) FROM Calificacion c WHERE c.direccion = 'estudiante_a_tutor' "
            + "AND c.sesionId IN (SELECT s.id FROM com.tinku.aula.model.SesionAprendizaje s "
            + "WHERE s.reservaId IN (SELECT r.id FROM com.tinku.reservas.model.Reserva r "
            + "WHERE r.tutor.id = :tutorId))")
    long countPublicasPorTutor(UUID tutorId);

    /** FR-REP-007: promedio de calificaciones publicas del Tutor. */
    @Query("SELECT AVG(c.estrellas) FROM Calificacion c WHERE c.direccion = 'estudiante_a_tutor' "
            + "AND c.sesionId IN (SELECT s.id FROM com.tinku.aula.model.SesionAprendizaje s "
            + "WHERE s.reservaId IN (SELECT r.id FROM com.tinku.reservas.model.Reserva r "
            + "WHERE r.tutor.id = :tutorId))")
    Optional<Double> promedioPublicoPorTutor(UUID tutorId);

    /** T-M7-05 (FR-REP-006): Tutores con al menos una sesion finalizada sin su
     *  calificacion oculta tutor_a_estudiante pendiente. Solo estado
     *  {@code finalizada} — ni canceladas, interrumpidas ni no-show cuentan
     *  (FR-REP-008). */
    @Query("SELECT DISTINCT r.tutor.id FROM com.tinku.reservas.model.Reserva r "
            + "WHERE r.id IN (SELECT s.reservaId FROM com.tinku.aula.model.SesionAprendizaje s "
            + "WHERE s.estado = 'finalizada' "
            + "AND NOT EXISTS (SELECT 1 FROM Calificacion c WHERE c.sesionId = s.id "
            + "AND c.direccion = 'tutor_a_estudiante'))")
    List<UUID> findAllTutoresConCalificacionPendiente();

    /** T-M7-04: calificaciones ocultas (tutor_a_estudiante) de un estudiante,
     *  identificado por las sesiones donde es beneficiario de la Reserva. */
    @Query("SELECT c FROM Calificacion c WHERE c.direccion = 'tutor_a_estudiante' "
            + "AND c.sesionId IN (SELECT s.id FROM com.tinku.aula.model.SesionAprendizaje s "
            + "WHERE s.reservaId IN (SELECT r.id FROM com.tinku.reservas.model.Reserva r "
            + "WHERE r.beneficiario.id = :estudianteId))")
    List<Calificacion> findOcultasPorEstudiante(UUID estudianteId);

    /** T-M7-02: si ya existe una calificacion de esa direccion para la sesion
     *  (la unicidad la garantizan tambien la BD, uq_calificacion_por_sesion...). */
    Optional<Calificacion> findBySesionIdAndAutorIdAndDireccion(UUID sesionId, UUID autorId, String direccion);

    /** T-M7-06: calificacion publica de una sesion (para decidir el recordatorio). */
    Optional<Calificacion> findBySesionIdAndDireccion(UUID sesionId, String direccion);

    /** BR-MATCH-01 (T-M7-07): Tutores con alguna calificacion PUBLICA de 1-2
     *  estrellas en las ultimas 24hs. El servicio intersepta contra la lista de
     *  candidatos de M2. */
    @Query("SELECT DISTINCT r.tutor.id FROM com.tinku.reservas.model.Reserva r "
            + "WHERE r.id IN (SELECT s.reservaId FROM com.tinku.aula.model.SesionAprendizaje s "
            + "WHERE s.id IN (SELECT c.sesionId FROM Calificacion c "
            + "WHERE c.direccion = 'estudiante_a_tutor' "
            + "AND c.estrellas <= 2 AND c.createdAt >= :desde))")
    List<UUID> findTutoresEnSombraBrMatch01(Instant desde);
}