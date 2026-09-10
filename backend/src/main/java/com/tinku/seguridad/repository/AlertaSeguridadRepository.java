package com.tinku.seguridad.repository;

import com.tinku.seguridad.model.AlertaSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertaSeguridadRepository extends JpaRepository<AlertaSeguridad, UUID> {

    /** Cola de M8: alertas pendientes de resolución (12hs, prioridad alta). */
    List<AlertaSeguridad> findByEstadoOrderByCreatedAtAsc(String estado);
}