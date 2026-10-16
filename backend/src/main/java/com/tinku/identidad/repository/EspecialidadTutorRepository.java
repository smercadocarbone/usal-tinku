package com.tinku.identidad.repository;

import com.tinku.identidad.model.EspecialidadTutor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Especialidades del Tutor (ADR-M1-06, V56). */
public interface EspecialidadTutorRepository extends JpaRepository<EspecialidadTutor, UUID> {

    List<EspecialidadTutor> findByTutorIdOrderByCreatedAtAsc(UUID tutorId);
}
