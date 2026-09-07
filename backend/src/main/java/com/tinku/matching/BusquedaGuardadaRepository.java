package com.tinku.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Persistencia de búsquedas guardadas (FR-MATCH-008). */
public interface BusquedaGuardadaRepository extends JpaRepository<BusquedaGuardada, UUID> {

    List<BusquedaGuardada> findByUsuarioIdOrderByCreatedAtDesc(UUID usuarioId);
}