package com.tinku.shared.email;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Adaptador de {@link EnviadorEmail} para Resend (ADR-000-06): un POST a su API con el
 * {@code HttpClient} del JDK, sin dependencia nueva. Sin {@code RESEND_API_KEY} o sin
 * remitente verificado ({@code EMAIL_REMITENTE}) no está configurado y no envía nada.
 */
@Component
public class ResendEnviadorEmail implements EnviadorEmail {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final String apiKey;
    private final String remitente;
    private final URI url;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Autowired
    public ResendEnviadorEmail(@Value("${tinku.email.resend-api-key:}") String apiKey,
                               @Value("${tinku.email.remitente:}") String remitente,
                               @Value("${tinku.email.resend-url:https://api.resend.com/emails}") String url,
                               ObjectMapper objectMapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.remitente = remitente == null ? "" : remitente.trim();
        this.url = URI.create(url);
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean configurado() {
        return !apiKey.isEmpty() && !remitente.isEmpty();
    }

    @Override
    public void enviar(MensajeEmail mensaje) {
        if (!configurado()) {
            throw new IllegalStateException("Email sin configurar (RESEND_API_KEY / EMAIL_REMITENTE).");
        }
        HttpRequest pedido = HttpRequest.newBuilder(url)
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo(mensaje)))
                .build();
        HttpResponse<String> respuesta;
        try {
            respuesta = http.send(pedido, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new EmailEnvioException("Resend no respondió", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmailEnvioException("Envío interrumpido", e);
        }
        if (respuesta.statusCode() / 100 != 2) {
            // Sin el cuerpo en el mensaje: podría incluir la dirección del destinatario.
            throw new EmailEnvioException("Resend respondió HTTP " + respuesta.statusCode());
        }
    }

    private String cuerpo(MensajeEmail m) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "from", remitente,
                    "to", List.of(m.para()),
                    "subject", m.asunto(),
                    "text", m.texto()));
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
