package com.tinku.resumen.port;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-M6-03 — contrato HTTP hacia OpenAI contra un stub local: modelo gpt-4o,
 * Bearer, instrucciones como system y SOLO el transcript anonimizado como user
 * (ni id de sesion ni audio salen). Errores → RuntimeException (backoff);
 * sin API key → fail-closed sin llamada de red.
 */
class ResumenProveedorOpenAiTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HttpServer stub(int status, String body, AtomicReference<String> cuerpo,
                            AtomicReference<String> auth) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
        return server;
    }

    private String url(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Test
    void mandaSoloElTranscriptAnonimizadoYDevuelveElTexto() throws Exception {
        AtomicReference<String> cuerpo = new AtomicReference<>();
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = stub(200,
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"  1. Temas: fracciones \"}}]}",
                cuerpo, auth);
        UUID sesionId = UUID.randomUUID();
        try {
            ResumenProveedor.ResumenResultado r = new ResumenProveedorOpenAi(url(server), "sk-test")
                    .generarResumen(new ResumenProveedor.ResumenRequest(
                            sesionId, "[nombre] explico fracciones", null, null, null, null));

            assertThat(r.texto()).isEqualTo("1. Temas: fracciones");
            assertThat(auth.get()).isEqualTo("Bearer sk-test");
            JsonNode json = objectMapper.readTree(cuerpo.get());
            assertThat(json.get("model").asText()).isEqualTo("gpt-4o");
            assertThat(json.get("messages").get(0).get("content").asText())
                    .isEqualTo(PromptResumen.INSTRUCCIONES);
            assertThat(json.get("messages").get(1).get("content").asText())
                    .isEqualTo("Transcript:\n[nombre] explico fracciones");
            assertThat(cuerpo.get()).doesNotContain(sesionId.toString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void errorDelProveedorEsReintentableYSinApiKeyFallaCerrado() throws Exception {
        AtomicReference<String> cuerpo = new AtomicReference<>();
        HttpServer server = stub(500, "{\"error\":{\"message\":\"boom\"}}", cuerpo, new AtomicReference<>());
        ResumenProveedor.ResumenRequest req = new ResumenProveedor.ResumenRequest(
                UUID.randomUUID(), "texto", null, null, null, null);
        try {
            assertThatThrownBy(() -> new ResumenProveedorOpenAi(url(server), "sk-test").generarResumen(req))
                    .isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(ResumenProveedorNoConfiguradoException.class);

            cuerpo.set(null);
            assertThatThrownBy(() -> new ResumenProveedorOpenAi(url(server), " ").generarResumen(req))
                    .isInstanceOf(ResumenProveedorNoConfiguradoException.class);
            assertThat(cuerpo.get()).isNull();
        } finally {
            server.stop(0);
        }
    }
}
