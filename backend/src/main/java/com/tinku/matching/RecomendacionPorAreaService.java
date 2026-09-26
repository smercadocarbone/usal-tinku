package com.tinku.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * FR-MATCH-011: cuando nadie da exactamente lo que se buscó, reconoce el área del catálogo
 * (materia y nivel del tema más parecido a lo escrito) para recomendar tutores de esa área.
 * El servicio Python solo devuelve los temas cercanos; la regla (umbral, qué hacer con el
 * área) vive acá.
 */
@Service
public class RecomendacionPorAreaService {

    private static final Logger LOG = LoggerFactory.getLogger(RecomendacionPorAreaService.class);
    private static final int TEMAS_CONSULTADOS = 3;

    private final MatchingServiceClient matchingClient;
    private final PerfilTutorTemasRepository temasRepo;
    private final double scoreMinimoArea;

    public RecomendacionPorAreaService(MatchingServiceClient matchingClient,
                                       PerfilTutorTemasRepository temasRepo,
                                       @Value("${tinku.matching.score-minimo-area:0.25}") double scoreMinimoArea) {
        this.matchingClient = matchingClient;
        this.temasRepo = temasRepo;
        this.scoreMinimoArea = scoreMinimoArea;
    }

    /**
     * El área del tema del catálogo más parecido, si se parece lo suficiente. Si el servicio de
     * matching no responde, no hay recomendación: la búsqueda principal ya se resolvió y no se
     * la hace fallar por esto.
     */
    public Optional<AreaTema> inferir(String texto) {
        try {
            return matchingClient.temasCercanos(texto, TEMAS_CONSULTADOS).stream()
                    .filter(t -> t.score() >= scoreMinimoArea)
                    .findFirst()
                    .flatMap(t -> temasRepo.areaDeTema(UUID.fromString(t.id())));
        } catch (MatchingNoDisponibleException | IllegalArgumentException e) {
            LOG.info("Sin recomendación por área: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
