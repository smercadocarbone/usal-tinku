package com.tinku.resumen.port;

import com.tinku.resumen.GeminiCliente;
import com.tinku.resumen.PromptResumen;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * T07 — el request que arma {@link ResumenProveedorGemini} lleva SOLO el
 * transcript ya anonimizado: el audio crudo ({@code audioMimeType}/
 * {@code audioBase64} del record) NUNCA sale hacia Google (FR-SUM-005). Con el
 * pipeline de dos llamadas (ADR-M6-03) el audio va al transcriber, no aca.
 */
class ResumenProveedorGeminiTest {

    private static final String URI_GENERAR = "https://generativelanguage.googleapis.com/v1beta/"
            + "models/gemini-3.5-flash-lite:generateContent";

    private static final String RESPUESTA_OK = """
            {"candidates":[{"content":{"role":"model","parts":[{"text":"Resumen de la sesion."}]}}]}
            """;

    @Test
    void enviaSoloTranscriptAnonimizado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResumenProveedorGemini proveedor =
                new ResumenProveedorGemini(new GeminiCliente("clave-de-prueba", builder));

        server.expect(once(), requestTo(URI_GENERAR))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(
                        containsString("[nombre] explico fracciones"),
                        not(containsString("AUDIO_CRUDO_BASE64")),
                        not(containsString("audio/webm")))))
                .andRespond(withSuccess(RESPUESTA_OK, MediaType.APPLICATION_JSON));

        ResumenProveedor.ResumenRequest request = new ResumenProveedor.ResumenRequest(
                UUID.randomUUID(),
                "[nombre] explico fracciones",
                "audio/webm",
                "AUDIO_CRUDO_BASE64",
                "Matematica",
                "primario");

        assertThat(proveedor.generarResumen(request).texto())
                .isEqualTo("Resumen de la sesion.");
        server.verify();
    }

    /** Lo enviado es exactamente la plantilla que ResumenService persiste para auditoria. */
    @Test
    void enviaLaMismaPlantillaQueSePersisteParaAuditoria() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ResumenProveedorGemini proveedor =
                new ResumenProveedorGemini(new GeminiCliente("clave-de-prueba", builder));

        server.expect(once(), requestTo(URI_GENERAR))
                .andExpect(jsonPath("$.contents[0].parts[0].text")
                        .value(PromptResumen.armar("[nombre] explico fracciones")))
                .andRespond(withSuccess(RESPUESTA_OK, MediaType.APPLICATION_JSON));

        proveedor.generarResumen(new ResumenProveedor.ResumenRequest(UUID.randomUUID(),
                "[nombre] explico fracciones", null, null, "Matematica", "primario"));
        server.verify();
    }
}
