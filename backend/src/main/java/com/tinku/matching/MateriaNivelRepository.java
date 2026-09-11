package com.tinku.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia del catálogo cerrado de materias/niveles (FR-MATCH-006). */
public interface MateriaNivelRepository extends JpaRepository<MateriaNivel, UUID> {

    /** La (nivel, materia) que el Tutor eligió — debe existir en el catálogo. */
    Optional<MateriaNivel> findByNivelAndMateria(String nivel, String materia);

    /** Resuelve los ids del perfil del Tutor a sus filas del catálogo. */
    List<MateriaNivel> findByIdIn(Collection<UUID> ids);
}