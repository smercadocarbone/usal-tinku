package com.tinku.reservas.repository;

import com.tinku.reservas.model.PedidoPrevio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PedidoPrevioRepository extends JpaRepository<PedidoPrevio, UUID> {
}
