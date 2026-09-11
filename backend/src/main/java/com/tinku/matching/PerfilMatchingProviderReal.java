package com.tinku.matching;

import com.tinku.identidad.dto.MateriasNivel;
import com.tinku.identidad.port.PerfilMatchingProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación real del puerto {@link PerfilMatchingProvider}: lee el perfil
 * del Tutor de {@code matching.perfiles_tutor_matching} (V7) y resuelve los ids
 * del catálogo a materias concretas. Un Tutor sin perfil o sin materias
 * seleccionadas devuelve {@code Optional.empty()} — el perfil público se
 * serializa con listas vacías (idéntico al comportamiento del stub retirado).
 */
@Component
public class PerfilMatchingProviderReal implements PerfilMatchingProvider {

    private final PerfilTutorMatchingRepository perfilRepo;
    private final MateriaNivelRepository catalogoRepo;

    public PerfilMatchingProviderReal(PerfilTutorMatchingRepository perfilRepo,
                                      MateriaNivelRepository catalogoRepo) {
        this.perfilRepo = perfilRepo;
        this.catalogoRepo = catalogoRepo;
    }

    @Override
    public Optional<MateriasNivel> materiasYNivel(UUID tutorId) {
        return perfilRepo.findById(tutorId)
                .filter(p -> p.getMateriasNivelesIds().length > 0)
                .map(p -> {
                    List<MateriaNivel> filas =
                            catalogoRepo.findByIdIn(List.of(p.getMateriasNivelesIds()));
                    List<String> materias = filas.stream().map(MateriaNivel::getMateria).toList();
                    String nivel = filas.isEmpty() ? null : filas.getFirst().getNivel();
                    return new MateriasNivel(materias, nivel);
                });
    }
}