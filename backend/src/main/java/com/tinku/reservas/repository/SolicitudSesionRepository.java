package com.tinku.reservas.repository;

import com.tinku.reservas.model.EstadoSolicitud;
import com.tinku.reservas.model.SolicitudSesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SolicitudSesionRepository extends JpaRepository<SolicitudSesion, UUID> {

    /** T-M4-04: al aprobar, la Solicitud debe seguir pendiente (no expirada ni ya convertida). */
    Optional<SolicitudSesion> findByIdAndEstado(UUID id, EstadoSolicitud estado);

    /** T-M4-03 (job de expiración a 48hs, FR-RES-022): pendientes ya vencidas. */
    List<SolicitudSesion> findByEstadoAndExpiraAtBefore(EstadoSolicitud estado, Instant cuando);

    /** US-3: listado de Solicitudes pendientes que el Adulto Responsable debe revisar. */
    @Query("""
            select s from SolicitudSesion s
             join s.menor m
             join m.adultoResponsable ar
             where ar.id = :adultoResponsableId
               and s.estado = :estado
             order by s.createdAt
            """)
    List<SolicitudSesion> findByAdultoResponsableIdAndEstado(
            @Param("adultoResponsableId") UUID adultoResponsableId,
            @Param("estado") EstadoSolicitud estado);

    /** FR-ID-014: Solicitudes pendientes de un menor, para rechazarlas en su baja. */
    List<SolicitudSesion> findByMenorIdAndEstado(UUID menorId, EstadoSolicitud estado);

    /** Defensivo (US-2): el mismo menor no genera dos Solicitudes pendientes idénticas. */
    boolean existsByMenorIdAndTutorIdAndHorarioPropuestoAndEstado(
            UUID menorId, UUID tutorId, Instant horarioPropuesto, EstadoSolicitud estado);
}