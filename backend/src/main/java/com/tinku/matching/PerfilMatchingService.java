package com.tinku.matching;

import com.tinku.identidad.dto.MateriasNivel;
import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Perfil de matching del Tutor (FR-MATCH-006): solo puede elegir materias del
 * catálogo cerrado. La selección se guarda como ids del catálogo en {@code
 * matching.perfiles_tutor_matching} (V7). Un Tutor sin perfil todavía no lo
 * configuró — el puerto {@code PerfilMatchingProvider} devuelve "sin materias".
 */
@Service
public class PerfilMatchingService {

    private final MateriaNivelRepository catalogoRepo;
    private final PerfilTutorMatchingRepository perfilRepo;

    public PerfilMatchingService(MateriaNivelRepository catalogoRepo,
                                 PerfilTutorMatchingRepository perfilRepo) {
        this.catalogoRepo = catalogoRepo;
        this.perfilRepo = perfilRepo;
    }

    @Transactional
    public MateriasNivel cargarPerfil(Usuario tutor, String nivel, List<String> materias) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new PerfilMatchingTutorRequeridoException();
        }
        // FR-MATCH-006: catálogo cerrado — cada (nivel, materia) debe existir.
        List<UUID> ids = new ArrayList<>();
        for (String materia : materias) {
            Optional<MateriaNivel> existente = catalogoRepo.findByNivelAndMateria(nivel.trim(), materia.trim());
            if (existente.isEmpty()) {
                throw new MateriaNivelInvalidaException(nivel, materia);
            }
            ids.add(existente.get().getId());
        }
        perfilRepo.save(new PerfilTutorMatching(
                tutor.getId(), ids.toArray(UUID[]::new)));
        return new MateriasNivel(materias.stream().map(String::trim).toList(), nivel.trim());
    }
}