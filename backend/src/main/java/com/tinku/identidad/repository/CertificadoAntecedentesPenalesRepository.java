package com.tinku.identidad.repository;

import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificadoAntecedentesPenalesRepository
        extends JpaRepository<CertificadoAntecedentesPenales, UUID> {

    Optional<CertificadoAntecedentesPenales> findByTutorIdAndEstado(UUID tutorId, EstadoCap estado);

    Optional<CertificadoAntecedentesPenales> findFirstByTutorIdOrderByCreatedAtDesc(UUID tutorId);

    /** FR-ID-026: CAP aprobado y vigente (vence_at >= hoy). */
    boolean existsByTutorIdAndEstadoAndVenceAtGreaterThanEqual(UUID tutorId, EstadoCap estado, LocalDate hoy);

    /** Job de vencimiento (FR-ID-025): CAPs aun pendientes/aprobados vencidos al dia de hoy. */
    List<CertificadoAntecedentesPenales> findByEstadoInAndVenceAtBefore(
            Collection<EstadoCap> estados, LocalDate hoy);

    /** Cola de moderacion de M8 (T-M1-16): pendientes + en revisión legal. */
    List<CertificadoAntecedentesPenales> findByEstadoInOrderByCreatedAtAsc(
            Collection<EstadoCap> estados);

    /**
     * T4-H1: misma cola con el Tutor ya cargado. La respuesta ({@code CapColaResponse}) lee el nombre
     * del Tutor en el controller, fuera de la transacción ({@code open-in-view: false}); sin el fetch,
     * la relación LAZY tiraba LazyInitializationException y la cola respondía 403.
     */
    @Query("select c from CertificadoAntecedentesPenales c join fetch c.tutor "
            + "where c.estado in :estados order by c.createdAt asc")
    List<CertificadoAntecedentesPenales> colaConTutor(@Param("estados") Collection<EstadoCap> estados);
}
