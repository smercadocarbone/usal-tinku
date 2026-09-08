package com.tinku.reservas.repository;

import com.tinku.reservas.model.EstadoReserva;
import com.tinku.reservas.model.Reserva;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ReservaRepository extends JpaRepository<Reserva, UUID> {

    /** FR-RES-020: barrido de recuperación de reservas cuyo timeout de pago venció. */
    List<Reserva> findByEstadoAndCreatedAtBefore(EstadoReserva estado, Instant antesDe);
}