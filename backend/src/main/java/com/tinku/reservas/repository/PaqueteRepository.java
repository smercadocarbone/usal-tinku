package com.tinku.reservas.repository;

import com.tinku.reservas.model.Paquete;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaqueteRepository extends JpaRepository<Paquete, UUID> {
}
