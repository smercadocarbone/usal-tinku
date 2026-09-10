package com.tinku.identidad.port;

import com.tinku.identidad.dto.MateriasNivel;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto hacia el perfil de matching del Tutor (M2). Los datos de {@code
 * materias/nivel} viven en {@code matching.materias_niveles} y se relacionan vía
 * {@code matching.perfiles_tutor_matching.materias_niveles_ids}. STUB: M2 todavía
 * no puebla esa tabla (ver BusquedaRequest) — la implementación real reemplaza
 * {@link PerfilMatchingProviderStub} cuando el perfil de matching exista.
 */
public interface PerfilMatchingProvider {

    /** Materias y nivel del Tutor, si su perfil de matching existe. */
    Optional<MateriasNivel> materiasYNivel(UUID tutorId);
}