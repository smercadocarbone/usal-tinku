package com.tinku.identidad.repository;

import com.tinku.identidad.model.CredencialAcademica;
import com.tinku.identidad.model.EstadoCredencial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredencialAcademicaRepository extends JpaRepository<CredencialAcademica, UUID> {

    Optional<CredencialAcademica> findByTutorIdAndEstado(UUID tutorId, EstadoCredencial estado);

    /** Última credencial cargada por un Tutor (para saber el intento y el ciclo). */
    Optional<CredencialAcademica> findFirstByTutorIdOrderByCreatedAtDesc(UUID tutorId);
}
