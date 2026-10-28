package com.tinku.matching.service;

import com.tinku.matching.port.MatchingServiceClient;
import com.tinku.matching.repository.PerfilTutorTemasRepository;
import com.tinku.matching.repository.PerfilTutorTemasRepository.TemaCatalogo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FR-MATCH-015: el matching semántico real para la presentación de la tesis. Compara un texto
 * libre con el catálogo de temas (pgvector, mismo modelo que la búsqueda) y devuelve los que
 * superan el umbral de la búsqueda de tutores ({@code tinku.matching.score-minimo}). Si ninguno
 * lo supera, la consulta está fuera del espacio del catálogo: solo se informa el mejor puntaje.
 * No toca tutores ni datos personales.
 */
@Service
public class TemasPresentacionService {

    static final int MAX_TEXTO = 200;
    static final int TEMAS_CONSULTADOS = 5;

    private final MatchingServiceClient matchingClient;
    private final PerfilTutorTemasRepository temasRepo;
    private final double scoreMinimo;

    public TemasPresentacionService(MatchingServiceClient matchingClient,
                                    PerfilTutorTemasRepository temasRepo,
                                    @Value("${tinku.matching.score-minimo:0.46}") double scoreMinimo) {
        this.matchingClient = matchingClient;
        this.temasRepo = temasRepo;
        this.scoreMinimo = scoreMinimo;
    }

    /** {@link MatchingNoDisponibleException} si el servicio de matching no responde. */
    public Resultado buscar(String texto) {
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.length() > MAX_TEXTO) {
            limpio = limpio.substring(0, MAX_TEXTO);
        }
        if (limpio.isEmpty()) {
            return new Resultado(List.of(), 0, scoreMinimo, temasRepo.contarTemasConEmbedding());
        }
        List<MatchingServiceClient.SugerenciaTema> cercanos = matchingClient.temasCercanos(limpio, TEMAS_CONSULTADOS);
        double mejor = cercanos.stream().mapToDouble(MatchingServiceClient.SugerenciaTema::score).max().orElse(0);
        List<MatchingServiceClient.SugerenciaTema> dentro = cercanos.stream()
                .filter(t -> t.score() >= scoreMinimo)
                .toList();
        Map<UUID, TemaCatalogo> porId = temasRepo.temasDelCatalogo(
                        dentro.stream().map(t -> UUID.fromString(t.id())).toList())
                .stream().collect(Collectors.toMap(TemaCatalogo::id, Function.identity()));
        List<Tema> temas = dentro.stream()
                .filter(t -> porId.containsKey(UUID.fromString(t.id())))
                .map(t -> {
                    TemaCatalogo c = porId.get(UUID.fromString(t.id()));
                    return new Tema(c.nombre(), c.materia(), c.nivel(), t.score());
                })
                .toList();
        return new Resultado(temas, mejor, scoreMinimo, temasRepo.contarTemasConEmbedding());
    }

    public record Tema(String nombre, String materia, String nivel, double score) {
    }

    /** {@code temas} vacío = fuera del espacio del catálogo; {@code mejorScore} es el del tema más parecido. */
    public record Resultado(List<Tema> temas, double mejorScore, double umbral, int temasEnCatalogo) {
    }
}
