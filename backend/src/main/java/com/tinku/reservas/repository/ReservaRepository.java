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
}