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

    private final MatchingContextoService contextoService;
    private final MatchingServiceClient matchingClient;
    private final AjusteRankingService ajusteRanking;

    public MatchingOrquestador(MatchingContextoService contextoService,
                               MatchingServiceClient matchingClient,
                               AjusteRankingService ajusteRanking) {
        this.contextoService = contextoService;
        this.matchingClient = matchingClient;
        this.ajusteRanking = ajusteRanking;
    }

    public List<BusquedaResponse> buscar(Usuario buscador, String textoBusqueda) {
        ContextoAutorizacion contexto = contextoService.resolverContexto(buscador);
        List<UUID> candidatos = contextoService.tutoresCandidatos(contexto);

        List<ResultadoRanking> semantico = matchingClient.match(candidatos, textoBusqueda)
                .stream()
                .map(match -> new ResultadoRanking(match.tutorId(), match.score(),
                        esResultadoNoAutorizado(contexto, match.tutorId())))
                .toList();

        return ajusteRanking.ajustar(semantico)
                .stream()
                .map(ranking -> new BusquedaResponse(ranking.tutorId(), ranking.score(), ranking.noAutorizado()))
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