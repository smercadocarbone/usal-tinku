package com.tinku.seguridad.repository;

import com.tinku.seguridad.model.Sancion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SancionRepository extends JpaRepository<Sancion, UUID> {

    Optional<Sancion> findByDenunciaId(UUID denunciaId);

    Optional<Sancion> findByAlertaId(UUID alertaId);
}