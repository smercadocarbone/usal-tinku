package com.tinku.matching;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica la comunicacion interna del backend Java hacia el servicio de
 * matching (T-000-08) a nivel de cliente: se levanta un stub HTTP local que
 * emula el {@code /health} del proceso Python y se confirma que el cliente
 * hace la request correcta y parsea la respuesta.
 *
 * El chequeo ".venv real" de punta a punta (uvicorn main:app) se hace en dev,
 * donde {@link MatchingServiceHealthCheck} loguea el estado al arrancar el
 * backend — este test es independiente de que haya o no un venv de Python
 * instalado (CI no puede depender de eso).
 */
class MatchingServiceClientTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"status\":\"ok\",\"service\":\"tinku-matching-service\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.createContext("/match", exchange -> {
            byte[] received = exchange.getRequestBody().readAllBytes();
            // La regresion del h2 upgrade: el body llegaba vacio y el servicio
            // respondia 422 "body missing". Sin body, el test debe fallar aca.
            if (received.length == 0) {
                exchange.sendResponseHeaders(422, 0);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(("{\"detail\":[{\"loc\":[\"body\"],\"msg\":\"missing\"}]}")
                            .getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            String ranking = "[{\"tutor_id\":\"bd2316c4-1a60-42c2-8e5f-7155c6a655d7\",\"score\":0.78}]";
            byte[] body = ranking.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopStub() {
        server.stop(0);
    }

    @Test
    void healthConsultaElEndpointYParseaLaRespuesta() {
        MatchingServiceClient client = new MatchingServiceClient("http://localhost:" + port);

        MatchingServiceClient.EstadoSalud salud = client.health();

        assertThat(salud.status()).isEqualTo("ok");
        assertThat(salud.service()).isEqualTo("tinku-matching-service");
    }

    @Test
    void matchEjecutaElRankingRealYParseaLaRespuesta() {
        MatchingServiceClient client = new MatchingServiceClient("http://localhost:" + port);

        List<MatchingServiceClient.ResultadoMatch> ranking = client.match(
                List.of(UUID.fromString("bd2316c4-1a60-42c2-8e5f-7155c6a655d7")),
                "algebra para la facultad");

        assertThat(ranking).hasSize(1);
        assertThat(ranking.get(0).tutorId()).isEqualTo(UUID.fromString("bd2316c4-1a60-42c2-8e5f-7155c6a655d7"));
        assertThat(ranking.get(0).score()).isEqualTo(0.78);
    }
}
