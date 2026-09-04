package com.tinku.matching;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

/**
 * Cliente Java del Motor de Matching (servicio Python separado, Constitucion
 * Articulo VIII — T-000-08).
 *
 * El backend Java es el UNICO que conoce reglas de negocio de otros modulos
 * (autorizacion del menor, suspensiones de M9, reputacion de M7). A este
 * servicio Python llega solo un texto de busqueda y una lista YA acotada de
 * tutor_ids candidatos; nunca le llega la pregunta "esta suspendido este
 * tutor?" — esa respuesta se resuelve aca, antes de la llamada.
 */
@Component
public class MatchingServiceClient {

    private final RestClient restClient;

    public MatchingServiceClient(@Value("${tinku.matching-service.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Verifica la comunicacion interna backend Java -> proceso Python (T-000-08).
     * Devuelve el estado tal como lo reporta el servicio; no tira error de
     * negocio si esta caido, solo acerca al caller el resultado del /health.
     */
    public EstadoSalud health() {
        return restClient.get()
                .uri("/health")
                .retrieve()
                .body(EstadoSalud.class);
    }

    /**
     * Calculo semantico real (T-M2-04, POST /match del servicio Python).
     * Recibe la lista YA acotada de candidatos resuelta en Java (autorizacion +
     * exclusion de suspendidos, ver MatchingContextoService) y devuelve el
     * ranking por similitud ordenado descendente.
     *
     * Cualquier estado de error del servicio interno (503, 422, conexion
     * caida, timeout) se traduce a {@link MatchingNoDisponibleException}: este
     * modificar no puede fabricar un ranking como si supiese similitud — si el
     * proceso Python no responde, la busqueda es no disponible, no inventada.
     */
    public List<ResultadoMatch> match(List<UUID> tutorIdsCandidatos, String textoBusqueda) {
        try {
            return restClient.post()
                    .uri("/match")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new MatchRequest(textoBusqueda, tutorIdsCandidatos))
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            (request, response) -> {
                                throw new MatchingNoDisponibleException();
                            })
                    .body(MATCH_RESPONSE_TYPE);
        } catch (RestClientException ex) {
            // Conexion rechazada / timeout: mismo contrato que el 503 del servicio.
            throw new MatchingNoDisponibleException();
        }
    }

    private static final ParameterizedTypeReference<List<ResultadoMatch>> MATCH_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    /** JSON del POST /match. El contrato interno con Python es snake_case (no
     * tocar: el servicio Python lo espera con esos nombres exactos). */
    public record MatchRequest(
            @JsonProperty("texto_busqueda") String textoBusqueda,
            @JsonProperty("tutor_ids_candidatos") List<UUID> tutorIdsCandidatos) {
    }

    /** Un ítem del ranking devuelto por el servicio Python (snake_case interno). */
    public record ResultadoMatch(
            @JsonProperty("tutor_id") UUID tutorId,
            double score) {
    }

    public record EstadoSalud(String status, String service) {}
}
