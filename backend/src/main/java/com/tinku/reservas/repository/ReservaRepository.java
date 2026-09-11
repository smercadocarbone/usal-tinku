package com.tinku.reservas.repository;

import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    /** FR-RES-020: barrido de recuperación de reservas cuyo timeout de pago venció. */
    List<Reserva> findByEstadoAndCreatedAtBefore(EstadoReserva estado, Instant antesDe);

    /** T-M4-09 (FR-SEC-008): reservas futuras y cancelables de un Tutor sancionado. */
    List<Reserva> findByEstadoInAndHorarioAfterAndTutor_Id(Collection<EstadoReserva> estados,
                                                           Instant despuesDe, UUID tutorId);

    /** T-M4-09 (FR-SEC-012): reservas futuras y cancelables pagadas por un sancionado. */
    List<Reserva> findByEstadoInAndHorarioAfterAndPagador_Id(Collection<EstadoReserva> estados,
                                                             Instant despuesDe, UUID pagadorId);

    /** GET /api/reservas — las reservas donde el usuario es pagador, beneficiario o tutor. */
    List<Reserva> findByPagador_IdOrBeneficiario_IdOrTutor_IdOrderByHorario(
            UUID pagadorId, UUID beneficiarioId, UUID tutorId);

    /** T-M7-07 (señal de re-enganche): historial completo Tutor+beneficiario. */
    List<Reserva> findByTutor_IdAndBeneficiario_Id(UUID tutorId, UUID beneficiarioId);

    /** FR-ID-014 (T-M1-12): reservas futuras y activas del menor (baja de perfil). */
    long countByEstadoInAndHorarioAfterAndBeneficiario_Id(
            Collection<EstadoReserva> estados, Instant despuesDe, UUID beneficiarioId);
}