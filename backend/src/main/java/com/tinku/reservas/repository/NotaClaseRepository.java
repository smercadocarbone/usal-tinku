package com.tinku.reservas.repository;

import com.tinku.reservas.model.NotaClase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotaClaseRepository extends JpaRepository<NotaClase, UUID> {
}
