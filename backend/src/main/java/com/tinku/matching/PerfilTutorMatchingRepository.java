package com.tinku.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Persistencia del perfil de matching del Tutor (V7). */
public interface PerfilTutorMatchingRepository extends JpaRepository<PerfilTutorMatching, UUID> {
}