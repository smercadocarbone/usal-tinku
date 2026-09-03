package com.tinku.matching;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

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
}
