package com.tinku.resumen.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Proveedor de resumen con GPT-4o (ADR-M6-03) — Chat Completions de OpenAI.
 * Manda {@link PromptResumen#INSTRUCCIONES} como mensaje de sistema y el
 * transcript YA anonimizado como mensaje de usuario; nada mas sale del backend
 * (ni ids, ni materia, ni audio). Sin API key falla cerrado con
 * {@link ResumenProveedorNoConfiguradoException}; cualquier error HTTP o de red
 * es una RuntimeException comun → backoff de FR-SUM-007.
 */
public class ResumenProveedorOpenAi implements ResumenProveedor {

    static final String MODELO = "gpt-4o";
    private static final String PATH = "/v1/chat/completions";

    private final RestClient restClient;
    private final String apiKey;
    private final String modelo;

    public ResumenProveedorOpenAi(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, MODELO);
    }

    /**
     * {@code modelo} distinto de GPT-4o solo para probar en local contra una API compatible con la
     * de OpenAI (p. ej. Groq); en produccion queda el default.
     */
    public ResumenProveedorOpenAi(String baseUrl, String apiKey, String modelo) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.apiKey = apiKey;
        this.modelo = modelo == null || modelo.isBlank() ? MODELO : modelo;
    }

    @Override
    public ResumenResultado generarResumen(ResumenRequest request) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResumenProveedorNoConfiguradoException();
        }
        Map<String, Object> cuerpo = Map.of(
                "model", modelo,
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system", "content", PromptResumen.INSTRUCCIONES),
                        Map.of("role", "user", "content",
                                "Transcript:\n" + request.transcriptAnonimizado())));
        Respuesta respuesta;
        try {
            respuesta = restClient.post()
                    .uri(PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientException ex) {
            // Sin cuerpo de error en el mensaje: puede traer eco del request.
            throw new IllegalStateException("OpenAI no respondio al pedido de resumen ("
                    + ex.getClass().getSimpleName() + ")");
        }
        String texto = respuesta == null || respuesta.choices() == null || respuesta.choices().isEmpty()
                || respuesta.choices().get(0).message() == null
                ? null : respuesta.choices().get(0).message().content();
        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException("OpenAI devolvio un resumen vacio");
        }
        return new ResumenResultado(texto.strip());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(List<Opcion> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Opcion(Mensaje message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Mensaje(String content) {
    }
}
