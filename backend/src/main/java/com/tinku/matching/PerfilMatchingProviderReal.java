package com.tinku.matching;

import com.tinku.identidad.dto.MateriasNivel;
import com.tinku.identidad.port.PerfilMatchingProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementación real del puerto {@link PerfilMatchingProvider}: las materias
 * del Tutor salen de los temas que eligió ({@code tema_ids}, M2-F: el camino que
 * usa la app). Solo si no eligió ningún tema se cae al catálogo viejo
 * ({@code materias_niveles_ids}, V7, que únicamente llena PUT /api/perfil-matching).
 * Sin nada de eso → {@code Optional.empty()} y el perfil público va con listas vacías.
 */
@Component
public class PerfilMatchingProviderReal implements PerfilMatchingProvider {

    private final PerfilTutorMatchingRepository perfilRepo;
    private final MateriaNivelRepository catalogoRepo;
    private final PerfilTutorTemasRepository temasRepo;

    public PerfilMatchingProviderReal(PerfilTutorMatchingRepository perfilRepo,
                                      MateriaNivelRepository catalogoRepo,
                                      PerfilTutorTemasRepository temasRepo) {
        this.perfilRepo = perfilRepo;
        this.catalogoRepo = catalogoRepo;
        this.temasRepo = temasRepo;
    }

    @Override
    public Optional<MateriasNivel> materiasYNivel(UUID tutorId) {
        List<String[]> deTemas = temasRepo.nivelYMateriaDeTemas(tutorId);
        if (!deTemas.isEmpty()) {
            // La misma materia puede venir de dos cursos (4° y 5° de Matemática): una sola vez.
            List<String> materias = deTemas.stream().map(f -> f[1]).distinct().toList();
            return Optional.of(new MateriasNivel(materias, deTemas.getFirst()[0]));
        }
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