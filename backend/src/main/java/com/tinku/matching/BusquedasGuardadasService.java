package com.tinku.matching;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Búsquedas guardadas (T-M2-09, FR-MATCH-008): guardar, listar y validar
 * pertenencia para re-ejecutar. La re-ejecución NO vive acá: vuelve a correr
 * el flujo completo del {@link MatchingOrquestador} con el texto guardado, así
 * los resultados siempre están actualizados y nunca congelados.
 */
@Service
public class BusquedasGuardadasService {

    private final BusquedaGuardadaRepository repository;

    public BusquedasGuardadasService(BusquedaGuardadaRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BusquedaGuardada guardar(UUID usuarioId, String textoBusqueda) {
        BusquedaGuardada guardada = new BusquedaGuardada();
        guardada.setUsuarioId(usuarioId);
        guardada.setTextoBusqueda(textoBusqueda);
        return repository.save(guardada);
    }

    @Transactional(readOnly = true)
    public List<BusquedaGuardada> listar(UUID usuarioId) {
        return repository.findByUsuarioIdOrderByCreatedAtDesc(usuarioId);
    }

    /**
     * La búsqueda a re-ejecutar debe pertenecer al usuario autenticado; si no es
     * suya (o no existe) es lo mismo: 404, sin filtrar existencia ajenas.
     */
    @Transactional(readOnly = true)
    public BusquedaGuardada propia(UUID usuarioId, UUID id) {
        return repository.findById(id)
                .filter(guardada -> guardada.getUsuarioId().equals(usuarioId))
                .orElseThrow(BusquedaGuardadaNoEncontradaException::new);
    }
}