package com.tinku.identidad.port;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente del validador oficial de firma digital de la Secretaría de Innovación Pública
 * (validadordefirmas.gob.ar), ADR-M1-10. Usa los mismos pedidos que hace la página del
 * validador: no es una API documentada, por eso todo lo que no se puede leer termina en
 * {@link ValidadorNoDisponibleException} y la cola lo reintenta más tarde.
 *
 * <ul>
 *   <li>{@code GET /api/PdfSignature/verificacion/countRequestsQueue} → pedidos en espera.</li>
 *   <li>{@code POST /api/PdfSignature/verificacion/async} (multipart, campo {@code pdf}) →
 *       {@code idRequest}.</li>
 *   <li>{@code GET /api/PdfSignature/verificacion/{id}} → {@code state} y
 *       {@code verificationAsyncResult}.</li>
 * </ul>
 */
@Component
public class ValidadorFirmasGobAr implements ValidadorFirmasPdf {

    private final RestClient http;
    private final ObjectMapper json;

    public ValidadorFirmasGobAr(@Value("${tinku.cap.validador.base-url}") String baseUrl, ObjectMapper json) {
        HttpClient cliente = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(cliente);
        fabrica.setReadTimeout(Duration.ofSeconds(60));
        this.http = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(fabrica)
                .defaultHeader("User-Agent", "Tinku (verificacion de CAP; contacto: tinku.site)")
                .build();
        this.json = json;
    }

    @Override
    public int pedidosEnCola() {
        JsonNode r = get("/api/PdfSignature/verificacion/countRequestsQueue");
        if (r == null || !r.has("countRequestsQueue")) {
            throw new ValidadorNoDisponibleException("Respuesta sin countRequestsQueue");
        }
        return r.get("countRequestsQueue").asInt();
    }

    @Override
    public String enviar(byte[] pdf) {
        MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
        partes.add("pdf", new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return "certificado.pdf";
            }
        });
        String cuerpo;
        try {
            cuerpo = http.post().uri("/api/PdfSignature/verificacion/async")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(partes)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            throw new ValidadorNoDisponibleException("No se pudo enviar el PDF: " + e.getMessage(), e);
        }
        JsonNode r = leer(cuerpo);
        String id = r == null ? null : r.path("idRequest").asText(null);
        if (id == null || id.isBlank()) {
            throw new ValidadorNoDisponibleException("Respuesta sin idRequest");
        }
        return id;
    }

    @Override
    public Resultado consultar(String idPedido) {
        return interpretar(get("/api/PdfSignature/verificacion/" + idPedido));
    }

    /** Traduce la respuesta del validador. Público para probarlo con respuestas reales. */
    public static Resultado interpretar(JsonNode r) {
        if (r == null || !r.has("state")) {
            throw new ValidadorNoDisponibleException("Respuesta sin state");
        }
        String state = r.get("state").asText();
        JsonNode res = r.path("verificationAsyncResult");
        String mensaje = res.path("message").asText("");
        String sha = res.path("sha256Digest").asText(null);
        EstadoValidacion estado = switch (state) {
            case "WAITING_PROCESS" -> EstadoValidacion.EN_COLA;
            case "FINISHIED", "FINISHED" -> EstadoValidacion.TERMINADO; // el validador lo escribe así
            case "FINISHIED_PROTECTED", "FINISHIED_PROTECTED_INVALID" -> EstadoValidacion.PROTEGIDO;
            default -> EstadoValidacion.DESCONOCIDO;
        };
        List<Firma> firmas = new ArrayList<>();
        for (JsonNode f : res.path("signatures")) {
            firmas.add(new Firma(f.path("validSign").asBoolean(false),
                    f.path("commonName").asText(""),
                    f.path("organization").asText(""),
                    f.path("organizationalUnit").asText("")));
        }
        return new Resultado(estado, res.path("valid").asBoolean(false), mensaje, sha, firmas);
    }

    private JsonNode get(String ruta) {
        try {
            return leer(http.get().uri(ruta).retrieve().body(String.class));
        } catch (RestClientException e) {
            throw new ValidadorNoDisponibleException("El validador no respondió: " + e.getMessage(), e);
        }
    }

    private JsonNode leer(String cuerpo) {
        try {
            return cuerpo == null ? null : json.readTree(cuerpo);
        } catch (RuntimeException e) {
            throw new ValidadorNoDisponibleException("Respuesta ilegible del validador", e);
        }
    }
}
