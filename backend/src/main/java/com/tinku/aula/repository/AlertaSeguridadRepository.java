package com.tinku.aula.repository;

import com.tinku.aula.model.AlertaSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertaSeguridadRepository extends JpaRepository<AlertaSeguridad, UUID> {

    Optional<AlertaSeguridad> findBySesionId(UUID sesionId);

    /** Cola de M8: alertas pendientes de resolución (ventana 12hs, prioridad alta). */
    List<AlertaSeguridad> findByEstadoOrderByCreatedAtAsc(String estado);
}
