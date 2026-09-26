package com.tinku.matching;

import com.tinku.identidad.model.TipoUsuario;
import com.tinku.identidad.model.Usuario;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asistente de "Mis materias": el Tutor cuenta con sus palabras qué enseña y se le sugieren
 * temas del catálogo cerrado (FR-MATCH-006), ordenados por similitud con el mismo modelo del
 * matching. Solo sugiere: guardar sigue siendo {@code PUT /api/tutores/me/temas}, que valida
 * contra el catálogo. El filtro por nivel (reglas del catálogo) se aplica acá, no en Python.
 */
@Service
public class SugerenciaTemasService {

    static final int MINIMO_CARACTERES = 10;
    static final int MAXIMO_CARACTERES = 1000;
    static final int LIMITE = 8;

    private final TemaRepository temaRepository;
    private final MatchingServiceClient matchingClient;

    public SugerenciaTemasService(TemaRepository temaRepository, MatchingServiceClient matchingClient) {
        this.temaRepository = temaRepository;
        this.matchingClient = matchingClient;
    }

    @Transactional(readOnly = true)
    public List<TemaSugerido> sugerir(Usuario tutor, String texto, String nivel) {
        if (tutor.getTipo() != TipoUsuario.TUTOR) {
            throw new TemasSoloTutorException();
        }
        String limpio = texto == null ? "" : texto.strip();
        if (limpio.length() < MINIMO_CARACTERES || limpio.length() > MAXIMO_CARACTERES) {
            throw new BusquedaInvalidaException();
        }
        List<Tema> candidatos = temaRepository.findAllConTrayecto().stream()
                .filter(t -> nivel == null || nivel.isBlank() || t.getTrayecto().getNivel().name().equals(nivel))
                .toList();
        if (candidatos.isEmpty()) {
            return List.of();
        }
        Map<String, Tema> porId = candidatos.stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), Function.identity()));
        return matchingClient.sugerirTemas(limpio, candidatos.stream()
                        .map(t -> new MatchingServiceClient.TemaCandidato(t.getId().toString(),
                                t.getNombre() + ": " + t.getDescripcion()))
                        .toList(), LIMITE).stream()
                .filter(s -> porId.containsKey(s.id()))
                .map(s -> {
                    Tema t = porId.get(s.id());
                    return new TemaSugerido(t.getId(), t.getNombre(), t.getTrayecto().getMateria(),
                            t.getTrayecto().getAnioOCarrera(), t.getTrayecto().getNivel().name(), s.score());
                })
                .toList();
    }

    public record TemaSugerido(UUID id, String nombre, String materia, String curso, String nivel, double score) {
    }
}
