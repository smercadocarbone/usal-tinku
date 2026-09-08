package com.tinku.aula;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Validación de firma del webhook de LiveKit (T-M3-02): los webhooks llegan con
 * {@code Authorization: Bearer <JWT>} firmado HS256 con el API secret de la
 * cuenta. El claim {@code sha256} es el digest SHA-256 del body CRUDO en base64.
 *
 * Mismo patrón de seguridad que el futuro webhook de MercadoPago (Chunk M5-B):
 * verificar la firma contra credenciales del proveedor y hash del payload, para
 * nunca confiar en que "el que llama es quien dice ser" por la URL.
 *
 * Fail-closed: sin credenciales configuradas (T-000-06) o ante cualquier error
 * de parseo/firma, el webhook se rechaza — la ausencia de join jamás debe
 * "confirmar" un no-show por una configuración incompleta.
 */
@Component
public class LiveKitWebhookVerificador {

    private final String apiKey;
    private final SecretKey secretKey;

    public LiveKitWebhookVerificador(
            @Value("${tinku.livekit.api-key}") String apiKey,
            @Value("${tinku.livekit.api-secret}") String apiSecret) {
        this.apiKey = apiKey;
        this.secretKey = apiKey.isBlank() || apiSecret.isBlank()
                ? null
                : Keys.hmacShaKeyFor(apiSecret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean esFirmaValida(String authorizationHeader, byte[] cuerpoRaw) {
        if (secretKey == null || authorizationHeader == null
                || !authorizationHeader.startsWith("Bearer ")) {
            return false;
        }
        try {
            String jwt = authorizationHeader.substring("Bearer ".length());
            Claims claims = Jwts.parser().verifyWith(secretKey).build()
                    .parseSignedClaims(jwt).getPayload();
            if (!apiKey.equals(claims.getIssuer())) {
                return false;
            }
            String sha256Base64 = claims.get("sha256", String.class);
            if (sha256Base64 == null) {
                return false;
            }
            byte[] claimHash = decodificar(sha256Base64);
            byte[] bodyHash = sha256(cuerpoRaw);
            return MessageDigest.isEqual(claimHash, bodyHash);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private byte[] decodificar(String valor) {
        try {
            return Base64.getDecoder().decode(valor);
        } catch (IllegalArgumentException e) {
            return Base64.getUrlDecoder().decode(valor);
        }
    }

    private byte[] sha256(byte[] cuerpo) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(cuerpo);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}