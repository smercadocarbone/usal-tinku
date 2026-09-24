package com.tinku.resumen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de Gemini (T07, ADR-M6-03) con {@link RestClient}, sin SDK nuevo
 * (A5) — mismo patron que {@code LiveKitService}/{@code MercadoPagoClientHttp}.
 * La API key vive SOLO en el entorno ({@code GEMINI_API_KEY}), nunca en el repo;
 * sin ella el bean arranca igual y falla al USAR el servicio (fail-closed), igual
 * que LiveKit.
 *
 * <p>Pipeline de dos llamadas (PT7, 2026-09-23): {@link #transcribir(byte[], String)}
 * para el audio de la sesion (T08) y {@link #generarContenido(String)} para el
 * resumen del transcript ya anonimizado. Un 5xx (o una respuesta sin texto util)
 * cae como {@link RuntimeException} para que {@code ResumenService} lo traduzca al
 * backoff de FR-SUM-007 — no se reintenta aca.
 */
@Component
public class GeminiCliente {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    public static final String MODELO = "gemini-3.5-flash-lite";
    private static final String ENDPOINT_GENERAR = "/v1beta/models/" + MODELO + ":generateContent";
    private static final String HEADER_API_KEY = "x-goog-api-key";

    /** TF de la transcripcion: literal, sin completar ni inventar contenido. */
    private static final String PROMPT_TRANSCRIPCION =
            "Transcribi literalmente el audio de la sesion de tutoria. No completes, "
                    + "corrijas ni inventes contenido: solo la transcripcion tal cual se escucha.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final RestClient restClient;

    @Autowired
    public GeminiCliente(@Value("${tinku.resumen.gemini.api-key:}") String apiKey) {
        this(apiKey, RestClient.builder());
    }

    /** Para tests ({@code MockRestServiceServer.bindTo(builder)}). */
    public GeminiCliente(String apiKey, RestClient.Builder builder) {
        this.apiKey = apiKey;
        // Sin requestFactory propio: el default de Spring (JDK) habla HTTP/2 con
        // Google sin problema, y asi no se pisa el factory que el mock de los
        // tests inyecta en el builder.
        this.restClient = builder
                .baseUrl(BASE_URL)
                .build();
    }

    /** Transcribe el audio de una sesion (lo invoca T08). */
    public String transcribir(byte[] audio, String mimeType) {
        verificarConfigurado();
        try {
            String audioBase64 = Base64.getEncoder().encodeToString(audio);
            Map<String, Object> parteAudio = Map.of("inlineData",
                    Map.of("mimeType", mimeType, "data", audioBase64));
            Map<String, Object> contenido = Map.of("role", "user",
                    "parts", List.of(Map.of("text", PROMPT_TRANSCRIPCION), parteAudio));
            return extraerTexto(llamar(bodyJson(contenido)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el request de transcripcion a Gemini.", e);
        }
    }

    /** Genera contenido a partir de un prompt (lo usa {@code ResumenProveedorGemini}). */
    public String generarContenido(String prompt) {
        verificarConfigurado();
        try {
            Map<String, Object> contenido = Map.of("role", "user",
                    "parts", List.of(Map.of("text", prompt)));
            return extraerTexto(llamar(bodyJson(contenido)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo armar el request de contenido a Gemini.", e);
        }
    }

    private String bodyJson(Map<String, Object> contenido) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of("contents", List.of(contenido)));
    }

    private RespuestaGenerativa llamar(String jsonBody) {
        try {
            return restClient.post()
                    .uri(ENDPOINT_GENERAR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HEADER_API_KEY, apiKey)
                    .body(jsonBody)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new IllegalStateException("Gemini rechazo la llamada ("
                                + response.getStatusCode() + ").");
                    })
                    .body(RespuestaGenerativa.class);
        } catch (RestClientException e) {
            throw new IllegalStateException("Gemini no responde: " + e.getMessage(), e);
        }
    }

    /** Extrae el texto de la primera respuesta; vacio/ausente = error del proveedor. */
    private String extraerTexto(RespuestaGenerativa respuesta) {
        String texto = respuesta != null && respuesta.candidates() != null
                && !respuesta.candidates().isEmpty()
                && respuesta.candidates().get(0).content() != null
                && respuesta.candidates().get(0).content().parts() != null
                && !respuesta.candidates().get(0).content().parts().isEmpty()
                ? respuesta.candidates().get(0).content().parts().get(0).text()
                : null;
        if (texto == null || texto.isBlank()) {
            throw new IllegalStateException("Gemini no devolvio texto util en la respuesta "
                    + "(candidates[0].content.parts[0]).");
        }
        return texto;
    }

    private void verificarConfigurado() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini no configurado: defini GEMINI_API_KEY (ADR-M6-03).");
        }
    }

    /** Contrato de respuesta de {@code :generateContent} (los nombres no se traducen). */
    record RespuestaGenerativa(List<Candidato> candidates) {
    }

    record Candidato(Contenido content) {
    }

    record Contenido(List<Parte> parts) {
    }

    record Parte(String text) {
    }
}