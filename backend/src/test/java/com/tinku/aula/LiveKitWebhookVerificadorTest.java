package com.tinku.aula;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato del webhook de LiveKit (T-M3-02): la firma es un JWT HS256 firmado
 * con el API secret cuyo claim {@code sha256} es el digest del body CRUDO.
 * Cualquier desvío (body alterado, otro secret, issuer distinto, sin
 * Authorization, o credenciales ausentes) → rechazado. Mismo patrón que el
 * webhook de MercadoPago (Chunk M5-B).
 */
class LiveKitWebhookVerificadorTest {

    private static final String API_KEY = "APItest123456789";
    private static final String API_SECRET = "0123456789012345678901234567890123456789";

    private static final byte[] BODY =
            "{\"event\":\"participant_joined\",\"participant\":{\"identity\":\"tutor-1\"}}"
                    .getBytes(StandardCharsets.UTF_8);

    private final LiveKitWebhookVerificador verificador =
            new LiveKitWebhookVerificador(API_KEY, API_SECRET);

    @Test
    void firmaValida_aceptaElWebhook() {
        assertThat(verificador.esFirmaValida("Bearer " + firmar(BODY), BODY)).isTrue();
    }

    @Test
    void bodyAlterado_rechaza() {
        String firma = firmar(BODY);
        byte[] otroBody = "{\"event\":\"room_finished\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(verificador.esFirmaValida("Bearer " + firma, otroBody)).isFalse();
    }

    @Test
    void tokenFirmadoConOtroSecret_rechaza() {
        SecretKey otra = Keys.hmacShaKeyFor(
                "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee".getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder().issuer(API_KEY).claim("sha256", digestBase64(BODY))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otra).compact();

        assertThat(verificador.esFirmaValida("Bearer " + token, BODY)).isFalse();
    }

    @Test
    void issuerDistintoDelApiKey_rechaza() {
        String token = Jwts.builder().issuer("otro-api-key").claim("sha256", digestBase64(BODY))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(verificador.esFirmaValida("Bearer " + token, BODY)).isFalse();
    }

    @Test
    void sinAuthorizationHeader_rechaza() {
        assertThat(verificador.esFirmaValida(null, BODY)).isFalse();
        assertThat(verificador.esFirmaValida("Basic abc123", BODY)).isFalse();
    }

    @Test
    void sinCredencialesConfiguradas_failClosed() {
        LiveKitWebhookVerificador sinCreds = new LiveKitWebhookVerificador("", "");

        assertThat(sinCreds.esFirmaValida("Bearer " + firmar(BODY), BODY)).isFalse();
    }

    private String firmar(byte[] body) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(API_KEY)
                .claim("sha256", digestBase64(body))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(API_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String digestBase64(byte[] body) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(body);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}