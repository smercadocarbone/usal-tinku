package com.tinku.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
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
     * El área de lo que se buscó: primero el tema del catálogo más parecido según el modelo; si
     * no alcanza (o el catálogo todavía no está embebido), las palabras del texto contra los
     * nombres de los temas y materias. El nivel que la persona escribió ("en primario") manda
     * sobre el del tema encontrado.
     */
    public Optional<AreaTema> inferir(String texto) {
        Optional<String> nivelDicho = InterpreteBusqueda.nivel(texto);
        Optional<AreaTema> area = porSimilitud(texto);
        if (area.isEmpty()) {
            List<String> raices = InterpreteBusqueda.raices(texto);
            area = temasRepo.areaPorPalabras(raices, nivelDicho.orElse(null));
            if (area.isEmpty() && nivelDicho.isPresent()) {
                area = temasRepo.areaPorPalabras(raices, null);
            }
        }
        return nivelDicho.isPresent() ? area.map(a -> a.conNivel(nivelDicho.get())) : area;
    }

    /** Si el servicio de matching no responde, se sigue con las palabras: no se hace fallar la búsqueda. */
    private Optional<AreaTema> porSimilitud(String texto) {
        try {
            return matchingClient.temasCercanos(texto, TEMAS_CONSULTADOS).stream()
                    .filter(t -> t.score() >= scoreMinimoArea)
                    .findFirst()
                    .flatMap(t -> temasRepo.areaDeTema(UUID.fromString(t.id())));
        } catch (MatchingNoDisponibleException | IllegalArgumentException e) {
            LOG.info("Área por similitud no disponible ({}); se usan las palabras", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
