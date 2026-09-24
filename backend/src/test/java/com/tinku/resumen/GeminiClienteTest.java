package com.tinku.resumen;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * T07 — {@link GeminiCliente} contra un HTTP simulado (sin red, sin API key
 * real): request bien formado, modelo {@code gemini-3.5-flash-lite} y API key en
 * el header. Los 5xx del proveedor caen como {@link RuntimeException} para que
 * {@code ResumenService} los traduzca al backoff de FR-SUM-007.
 */
class GeminiClienteTest {

    private static final String CLAVE = "clave-de-prueba-google";

    private static final String URI_GENERAR = "https://generativelanguage.googleapis.com/v1beta/"
            + "models/gemini-3.5-flash-lite:generateContent";

    private static final String RESPUESTA_OK = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Resumen de la sesion."}]}}]}
            """;

    @Test
    void generaContenido_enviaElModeloLaApiKeyYElPrompt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCliente cliente = new GeminiCliente(CLAVE, builder);

        server.expect(once(), requestTo(URI_GENERAR))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", CLAVE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(containsString("\"text\":\"resumi la sesion\"")))
                .andRespond(withSuccess(RESPUESTA_OK, MediaType.APPLICATION_JSON));

        assertThat(cliente.generarContenido("resumi la sesion"))
                .isEqualTo("Resumen de la sesion.");
        server.verify();
    }

    @Test
    void transcribir_pegaElAudioEnInlineData() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCliente cliente = new GeminiCliente(CLAVE, builder);
        byte[] audio = "audio-crudo-de-prueba".getBytes(StandardCharsets.UTF_8);
        String audioBase64 = Base64.getEncoder().encodeToString(audio);

        server.expect(once(), requestTo(URI_GENERAR))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", CLAVE))
                .andExpect(content().string(containsString("\"inlineData\"")))
                .andExpect(content().string(containsString("\"audio/webm\"")))
                .andExpect(content().string(containsString(audioBase64)))
                .andRespond(withSuccess(RESPUESTA_OK, MediaType.APPLICATION_JSON));

        assertThat(cliente.transcribir(audio, "audio/webm"))
                .isEqualTo("Resumen de la sesion.");
        server.verify();
    }

    @Test
    void error5xxDelProveedorLanzaRuntimeException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GeminiCliente cliente = new GeminiCliente(CLAVE, builder);

        server.expect(once(), requestTo(URI_GENERAR))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> cliente.generarContenido("resumi la sesion"))
                .isInstanceOf(RuntimeException.class);
        server.verify();
    }
}