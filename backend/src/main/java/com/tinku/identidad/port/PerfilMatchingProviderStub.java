package com.tinku.identidad.port;

import com.tinku.identidad.dto.MateriasNivel;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Stub de {@link PerfilMatchingProvider}: M2 no puebla {@code
 * matching.perfiles_tutor_matching} (materias_niveles_ids queda vacío) hasta
 * que el perfil de matching del Tutor exista. Devuelve "sin materias/nivel" —
 * el perfil público del Tutor se serializa con listas vacías, que es lo
 * correcto hasta entonces.
 */
@Primary
@Component
public class PerfilMatchingProviderStub implements PerfilMatchingProvider {

    @Override
    public Optional<MateriasNivel> materiasYNivel(UUID tutorId) {
        return Optional.empty();
    }
}