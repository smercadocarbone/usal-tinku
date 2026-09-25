package com.tinku.resumen.port;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tinku.aula.AudioResumenService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * Transcript real del audio de la clase (ADR-M3-04): OpenAI {@code gpt-4o-mini-transcribe}, activo
 * con {@code LLM_PROVEEDOR=gpt-4o}. El audio solo existe en clases entre adultos con el adicional y
 * los dos consentimientos (lo controla aula). Sin audio → {@code null} (caso borde #2 del Spec M6).
 * Errores HTTP o de red → RuntimeException → backoff de FR-SUM-007.
 */
public class TranscriptSesionProveedorOpenAi implements TranscriptSesionProveedor {

    static final String MODELO = "gpt-4o-mini-transcribe";
    private static final String PATH = "/v1/audio/transcriptions";

    private final RestClient restClient;
    private final String apiKey;
    private final AudioResumenService audio;

    public TranscriptSesionProveedorOpenAi(String baseUrl, String apiKey, AudioResumenService audio) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(5));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.apiKey = apiKey;
        this.audio = audio;
    }

    @Override
    public String transcript(UUID sesionId) {
        byte[] bytes = audio.leer(sesionId);
        if (bytes == null) {
            return null;
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResumenProveedorNoConfiguradoException();
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", MODELO);
        form.add("language", "es");
        form.add("response_format", "json");
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "clase.webm";
            }
        });
        Respuesta r;
        try {
            r = restClient.post()
                    .uri(PATH)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(Respuesta.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("OpenAI no respondio al pedido de transcripcion ("
                    + ex.getClass().getSimpleName() + ")");
        }
        return r == null ? null : r.text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Respuesta(String text) {
    }
}
