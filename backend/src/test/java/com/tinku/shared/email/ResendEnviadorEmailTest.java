package com.tinku.shared.email;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ADR-000-06: adaptador de Resend contra un servidor HTTP local (sin red). */
class ResendEnviadorEmailTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void levantar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/emails", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"id\":\"abc\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, resp.length);
            ex.getResponseBody().write(resp);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void bajar() {
        server.stop(0);
    }

    private ResendEnviadorEmail enviador(String apiKey, String remitente) {
        return new ResendEnviadorEmail(apiKey, remitente,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/emails", objectMapper);
    }

    @Test
    void enviar_mandaBearerYElJsonQuePideResend() throws Exception {
        enviador("re_test", "Tinku <avisos@tinku.site>")
                .enviar(new MensajeEmail("ana@example.com", "Hola", "Texto"));

        assertThat(auth.get()).isEqualTo("Bearer re_test");
        JsonNode json = objectMapper.readTree(cuerpo.get());
        assertThat(json.get("from").asText()).isEqualTo("Tinku <avisos@tinku.site>");
        assertThat(json.get("to").get(0).asText()).isEqualTo("ana@example.com");
        assertThat(json.get("subject").asText()).isEqualTo("Hola");
        assertThat(json.get("text").asText()).isEqualTo("Texto");
    }

    @Test
    void respuestaNo2xx_lanzaEmailEnvioException() {
        status = 500;
        assertThatThrownBy(() -> enviador("re_test", "avisos@tinku.site")
                .enviar(new MensajeEmail("ana@example.com", "Hola", "Texto")))
                .isInstanceOf(EmailEnvioException.class);
    }

    @Test
    void sinApiKeyOSinRemitente_noEstaConfigurado_yNoLlamaAlProveedor() {
        assertThat(enviador("", "avisos@tinku.site").configurado()).isFalse();
        assertThat(enviador("re_test", " ").configurado()).isFalse();
        assertThat(enviador("re_test", "avisos@tinku.site").configurado()).isTrue();
        assertThatThrownBy(() -> enviador("", "avisos@tinku.site")
                .enviar(new MensajeEmail("ana@example.com", "Hola", "Texto")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(cuerpo.get()).isNull();
    }
}
