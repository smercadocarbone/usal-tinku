package com.tinku.resumen.repository;

import com.tinku.resumen.model.ResumenSesion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResumenSesionRepository extends JpaRepository<ResumenSesion, UUID> {

    /** 1:1 con la Sesion (sesion_id UNIQUE, V15 — FR-SUM-006). */
    Optional<ResumenSesion> findBySesionId(UUID sesionId);
}