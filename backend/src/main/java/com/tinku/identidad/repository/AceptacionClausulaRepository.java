package com.tinku.identidad.repository;

import com.tinku.identidad.model.AceptacionClausula;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AceptacionClausulaRepository extends JpaRepository<AceptacionClausula, UUID> {

    boolean existsByUsuarioIdAndClausulaAndVersion(UUID usuarioId, String clausula, String version);
}
