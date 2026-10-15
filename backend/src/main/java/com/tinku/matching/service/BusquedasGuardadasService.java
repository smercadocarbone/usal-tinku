package com.tinku.matching.service;

import com.tinku.matching.model.BusquedaGuardada;
import com.tinku.matching.repository.BusquedaGuardadaRepository;

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

    /** Resultado de guardar: {@code nueva == false} si el usuario ya la tenía (no se duplica). */
    public record Guardado(BusquedaGuardada guardada, boolean nueva) {
    }

    /** R-E E-2 (T1-6): guarda con sus filtros; si ya existe la misma búsqueda, devuelve esa. */
    @Transactional
    public Guardado guardar(UUID usuarioId, String textoBusqueda, java.util.Map<String, Object> filtros) {
        String huella = huella(textoBusqueda, filtros);
        java.util.Optional<BusquedaGuardada> existente = repository.findByUsuarioIdAndHuella(usuarioId, huella);
        if (existente.isPresent()) {
            return new Guardado(existente.get(), false);
        }
        BusquedaGuardada guardada = new BusquedaGuardada();
        guardada.setUsuarioId(usuarioId);
        guardada.setTextoBusqueda(textoBusqueda);
        guardada.setFiltros(new java.util.LinkedHashMap<>(filtros));
        guardada.setHuella(huella);
        return new Guardado(repository.save(guardada), true);
    }

    /** R-E E-2: borra una búsqueda propia; la ajena o inexistente es 404 (no se revela). */
    @Transactional
    public void borrar(UUID usuarioId, UUID id) {
        repository.delete(propia(usuarioId, id));
    }

    /** sha256 del texto normalizado (minúsculas, espacios simples) + filtros ordenados por clave. */
    static String huella(String texto, java.util.Map<String, Object> filtros) {
        StringBuilder base = new StringBuilder(texto.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " "));
        new java.util.TreeMap<>(filtros).forEach((k, v) ->
                base.append('|').append(k).append('=').append(String.valueOf(v).trim().toLowerCase(java.util.Locale.ROOT)));
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(base.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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