package com.tinku.matching;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Temas elegidos por un Tutor para su perfil de matching (contrato 2b): GET
 * devuelve los propios de cualquier perfil; el PUT guarda SOLO {@code tema_ids}
 * mediante upsert y no toca {@code embedding} ni {@code activo_para_matching}
 * (los embeddings se repueblan por el recompute de 2c, nunca acá).
 */
@Service
public class PerfilTutorTemasService {

    private final PerfilTutorMatchingRepository perfilRepo;
    private final CatalogoService catalogoService;

    public PerfilTutorTemasService(PerfilTutorMatchingRepository perfilRepo,
                                   CatalogoService catalogoService) {
        this.perfilRepo = perfilRepo;
        this.catalogoService = catalogoService;
    }

    /** GET /api/tutores/me/temas: sin fila en el perfil -> lista vacía. */
    @Transactional(readOnly = true)
    public List<UUID> temasDel(Usuario usuario) {
        return perfilRepo.findTemaIds(usuario.getId());
    }

    /** PUT /api/tutores/me/temas: solo TUTOR (403); ids no UUID -> 422;
     * UUID no existente en el catálogo -> 404; lista vacía es válida. */
    @Transactional
    public List<UUID> guardarTemas(Usuario usuario, List<String> temaIdsCrudos) {
        if (usuario.getTipo() != TipoUsuario.TUTOR) {
            throw new TemasSoloTutorException();
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String crudo : temaIdsCrudos) {
            try {
                ids.add(UUID.fromString(crudo));
            } catch (IllegalArgumentException e) {
                throw new TemaIdMalformadoException();
            }
        }
        if (!catalogoService.existen(ids)) {
            throw new TemaInexistenteException();
        }
        List<UUID> normalizados = List.copyOf(ids);
        perfilRepo.upsertTemaIds(usuario.getId(), normalizados);
        return normalizados;
    }
}