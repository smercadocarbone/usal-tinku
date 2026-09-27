package com.tinku.reservas.repository;

import com.tinku.reservas.model.PedidoReprogramacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PedidoReprogramacionRepository extends JpaRepository<PedidoReprogramacion, UUID> {

    Optional<PedidoReprogramacion> findByReservaIdAndEstado(UUID reservaId, String estado);
}
