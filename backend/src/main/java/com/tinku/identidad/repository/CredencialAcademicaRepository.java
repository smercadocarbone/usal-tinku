package com.tinku.identidad.repository;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredencialAcademicaRepository extends JpaRepository<CredencialAcademica, UUID> {

    Optional<CredencialAcademica> findByTutorIdAndEstado(UUID tutorId, EstadoCredencial estado);

    /** Última credencial cargada por un Tutor (para saber el intento y el ciclo). */
    Optional<CredencialAcademica> findFirstByTutorIdOrderByCreatedAtDesc(UUID tutorId);

    /**
     * Cola de revisión del Admin de Moderación (US-1, T-M8-04): pendientes de
     * aprobar, ordenadas por plazo de resolución ({@code ciclo_espera_hasta}
     * ASC, nulls al final). CON {@code join fetch} del tutor — el column de
     * {@code CredencialColaResponse} lee nombre/apellido fuera de la
     * transacción (open-in-view: false) y no puede caer en una carga lazy.
     */
    @Query("""
            select c from CredencialAcademica c
            left join fetch c.tutor
            where c.estado = :estado
            order by c.cicloEsperaHasta asc nulls last
            """)
    List<CredencialAcademica> colaPendientes(@Param("estado") EstadoCredencial estado);
}
