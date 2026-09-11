package com.tinku.pagos.repository;

import com.tinku.pagos.model.TarifaTutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code pagos.tarifas_tutor} (FR-PAG-006, Chunk M5-H). */
public interface TarifaTutorRepository extends JpaRepository<TarifaTutor, UUID> {

    Optional<TarifaTutor> findByTutorId(UUID tutorId);
}