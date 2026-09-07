package com.tinku.identidad.repository;

import com.tinku.identidad.model.CertificadoAntecedentesPenales;
import com.tinku.identidad.model.EstadoCap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CertificadoAntecedentesPenalesRepository
        extends JpaRepository<CertificadoAntecedentesPenales, UUID> {

    Optional<CertificadoAntecedentesPenales> findByTutorIdAndEstado(UUID tutorId, EstadoCap estado);

    Optional<CertificadoAntecedentesPenales> findFirstByTutorIdOrderByCreatedAtDesc(UUID tutorId);

    /** Job de vencimiento (FR-ID-025): CAPs aun pendientes/aprobados vencidos al dia de hoy. */
    List<CertificadoAntecedentesPenales> findByEstadoInAndVenceAtBefore(
            Collection<EstadoCap> estados, LocalDate hoy);

    /** Cola de moderacion de M8 (T-M1-16): pendientes + en revisión legal. */
    List<CertificadoAntecedentesPenales> findByEstadoInOrderByCreatedAtAsc(
            Collection<EstadoCap> estados);
}
