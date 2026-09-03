package com.tinku.matching;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
     * Placeholder del calculo semantico real (ADR-M2-01 pendiente). No llamar
     * con logica de negocio sin resolver: el ranking requiere resolver antes
     * autorizacion/suspensiones/reputacion en Java (Plan_M2, seccion 3).
     */
    public void match() {
        // TODO (Chunk M2-B): POST /match con MatchRequest{texto_busqueda, tutor_ids_candidatos}
    }

    public record EstadoSalud(String status, String service) {}
}
