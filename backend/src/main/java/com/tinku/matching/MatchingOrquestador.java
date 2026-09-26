package com.tinku.matching;

import com.tinku.identidad.model.Usuario;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orquestación del flujo completo de búsqueda (T-M2-08). Es el único lugar que
 * encadena: contexto de autorización y exclusión de suspendidos (Java) ->
 * cálculo semántico (Python) -> marcado {@code no_autorizado} -> reordenamiento
 * por reputación (Java). El servicio Python NUNCA ve reglas de negocio; acá es
 * donde se resuelven (Plan_M2, sección 3).
 */
@Service
public class MatchingOrquestador {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MatchingOrquestador.class);

    private final MatchingContextoService contextoService;
    private final MatchingServiceClient matchingClient;
    private final AjusteRankingService ajusteRanking;
    private final PerfilTutorTemasRepository perfilMatchingRepo;
    private final RecomendacionPorAreaService recomendacionPorArea;
    private final TemasSugeridosService temasSugeridos;

    public MatchingOrquestador(MatchingContextoService contextoService,
                               MatchingServiceClient matchingClient,
                               AjusteRankingService ajusteRanking,
                               PerfilTutorTemasRepository perfilMatchingRepo,
                               RecomendacionPorAreaService recomendacionPorArea,
                               TemasSugeridosService temasSugeridos) {
        this.contextoService = contextoService;
        this.matchingClient = matchingClient;
        this.ajusteRanking = ajusteRanking;
        this.perfilMatchingRepo = perfilMatchingRepo;
        this.recomendacionPorArea = recomendacionPorArea;
        this.temasSugeridos = temasSugeridos;
    }

    public List<BusquedaResponse> buscar(Usuario buscador, String textoBusqueda) {
        return buscar(buscador, textoBusqueda, null, null, null);
    }

    public List<BusquedaResponse> buscar(Usuario buscador, String texto, String nombre, String materia) {
        return buscar(buscador, texto, nombre, materia, null);
    }

    /**
     * Flujo completo con los filtros de catálogo de M2-F (contrato 2b). El
     * orden ES: (1) contexto de autorización + candidatos activos — siempre
     * primero, en Java (FR-MATCH-004/007); (2) si vienen {nombre}/{materia},
     * acotar los candidatos en Java cruzando tema_ids ↔ catálogo; (3) /match
     * con el texto EFECTIVO (texto_busqueda si viene, si no nombre, si no
     * materia) y los candidatos finales; (4) marcado no_autorizado + ajuste por
     * reputación (sin cambios). El proceso Python nunca ve reglas de negocio.
     */
    public List<BusquedaResponse> buscar(Usuario buscador, String texto, String nombre, String materia,
                                         String nivel) {
        String textoEfectivo = texto != null ? texto : (nombre != null ? nombre : materia);
        if (textoEfectivo == null || textoEfectivo.isBlank()) {
            throw new BusquedaInvalidaException();
        }

        ContextoAutorizacion contexto = contextoService.resolverContexto(buscador);
        // ADR-M1-07: un Tutor que busca clases para sí nunca se encuentra a sí mismo.
        List<UUID> candidatos = contextoService.tutoresCandidatos(contexto).stream()
                .filter(id -> !id.equals(buscador.getId())).toList();
        if (nombre != null || materia != null || nivel != null) {
            candidatos = perfilMatchingRepo.acotarCandidatos(candidatos, nombre, materia, nivel);
        }

        List<ResultadoRanking> semantico = matchingClient.match(candidatos, textoEfectivo)
                .stream()
                .map(match -> new ResultadoRanking(match.tutorId(), match.score(),
                        esResultadoNoAutorizado(contexto, match.tutorId())))
                .toList();

        // El nivel que la persona escribió ("divisiones en primario") vale como el chip de nivel
        // para los resultados directos. Se aplica sobre el ranking, no antes del /match: si nadie
        // de ese nivel sirve, la recomendación por área todavía puede mirar otros niveles.
        List<ResultadoRanking> candidatosDirectos = semantico;
        java.util.Optional<String> nivelDicho = texto != null && nivel == null
                ? InterpreteBusqueda.nivel(texto) : java.util.Optional.empty();
        if (nivelDicho.isPresent()) {
            java.util.Set<UUID> delNivel = new java.util.HashSet<>(
                    perfilMatchingRepo.acotarCandidatos(candidatos, null, null, nivelDicho.get()));
            candidatosDirectos = semantico.stream().filter(r -> delNivel.contains(r.tutorId())).toList();
        }

        // US-1: el puntaje mínimo de relevancia aplica cuando hay texto libre (o una búsqueda
        // guardada, que es texto); con solo filtros de catálogo los candidatos ya los cumplen.
        List<ResultadoRanking> directos = ajusteRanking.ajustar(candidatosDirectos, texto != null);
        if (texto == null || !directos.isEmpty()) {
            LOG.info("Búsqueda: {} candidatos, {} con puntaje, {} resultados directos",
                    candidatos.size(), semantico.size(), directos.size());
            return respuesta(directos, null);
        }

        // Nadie da exactamente lo que se escribió. FR-MATCH-011: se reconoce el área del catálogo
        // (materia y nivel) y se recomiendan tutores de esa área, aclarado como tal.
        // FR-MATCH-012: el tema queda como sugerencia para actualizar el catálogo.
        java.util.Optional<AreaTema> area = recomendacionPorArea.inferir(texto);
        temasSugeridos.registrar(buscador, texto, area);
        List<ResultadoRanking> recomendados = area.isEmpty() ? List.of() : recomendadosDelArea(
                contexto, candidatos, semantico, area.get());
        // Sin datos personales: solo cantidades y el área reconocida (para diagnosticar en producción).
        LOG.info("Búsqueda sin resultado directo: {} candidatos, {} con puntaje, área {}, {} recomendados",
                candidatos.size(), semantico.size(), area.map(AreaTema::rotulo).orElse("no reconocida"),
                recomendados.size());
        return area.isEmpty() ? List.of() : respuesta(recomendados, area.get().rotulo());
    }

    /**
     * Candidatos que dan temas de esa materia y nivel; si no hay de ese nivel, de la misma materia
     * en otro. Un tutor del área sin puntaje semántico (embedding pendiente) igual entra, último.
     */
    private List<ResultadoRanking> recomendadosDelArea(ContextoAutorizacion contexto, List<UUID> candidatos,
                                                       List<ResultadoRanking> semantico, AreaTema area) {
        List<UUID> delArea = perfilMatchingRepo.acotarCandidatos(candidatos, null, area.materia(), area.nivel());
        if (delArea.isEmpty()) {
            delArea = perfilMatchingRepo.acotarCandidatos(candidatos, null, area.materia(), null);
        }
        java.util.Map<UUID, Double> puntajes = new java.util.HashMap<>();
        semantico.forEach(r -> puntajes.put(r.tutorId(), r.score()));
        List<ResultadoRanking> delAreaRanking = delArea.stream()
                .map(id -> new ResultadoRanking(id, puntajes.getOrDefault(id, 0.0),
                        esResultadoNoAutorizado(contexto, id)))
                .toList();
        return ajusteRanking.ajustar(delAreaRanking, false);
    }

    private static List<BusquedaResponse> respuesta(List<ResultadoRanking> ranking, String area) {
        return ranking.stream()
                .map(r -> new BusquedaResponse(r.tutorId(), r.score(), r.noAutorizado(), area != null, area))
                .toList();
    }

    /**
     * FR-MATCH-005: en búsquedas de un menor, todo resultado que no esté en su
     * lista de autorización se marca {@code no_autorizado: true}. Para un menor
     * sin autorizados la lista es VACÍA -> todos quedan marcados (el frontend
     * muestra "Solicitar autorización"). Para un adulto nunca se marca.
     */
    private static boolean esResultadoNoAutorizado(ContextoAutorizacion contexto, UUID tutorId) {
        return contexto.esMenor() && !contexto.tutoresAutorizados().contains(tutorId);
    }
}