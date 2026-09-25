package com.tinku.seguridad.repository;

import com.tinku.seguridad.model.AlertaSeguridad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AlertaSeguridadRepository extends JpaRepository<AlertaSeguridad, UUID> {

    Optional<AlertaSeguridad> findBySesionId(UUID sesionId);

    /** Auditoría 2026-09-20: propias del Tutor detectado, para que pueda verlas
     * y presentar su descargo (US-2) — antes no existía forma de listarlas. */
    List<AlertaSeguridad> findByDetectadoIdOrderByCreatedAtDesc(UUID detectadoId);

    /** Cola de M8: alertas pendientes de resolución (ventana 12hs, prioridad alta). */
    List<AlertaSeguridad> findByEstadoOrderByCreatedAtAsc(String estado);

    /** Alerta pendiente sobre una sesión puntual (T-M6-03): M6 pausa la generación
     *  del resumen si existe (FR-SUM-008, BR-KS-03). */
    boolean existsBySesionIdAndEstado(UUID sesionId, String estado);
}
