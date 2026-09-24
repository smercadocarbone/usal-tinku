package com.tinku.config.security;

import com.tinku.identidad.model.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtUtil(
            @Value("${tinku.jwt.secret}") String secret,
            @Value("${tinku.jwt.expiration-minutes}") long expirationMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMinutes * 60 * 1000;
    }

    /**
     * AUD-027: {@code sub} es el UUID del usuario, nunca el DNI (el token se decodifica
     * sin secreto). {@code cv} es su versión de credenciales: un cambio o reset de
     * contraseña la sube y deja sin efecto los tokens anteriores.
     */
    public String generateToken(Usuario usuario) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(usuario.getId().toString())
                .claim("tipo", usuario.getTipo().name())
                .claim("cap_est", usuario.isCapacidadEstudiante())
                .claim("cap_ar", usuario.isCapacidadAdultoResponsable())
                .claim("cv", usuario.getCredentialsVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** @throws IllegalArgumentException si el {@code sub} no es un UUID (token previo a AUD-027). */
    public UUID extractUsuarioId(String token) {
        return UUID.fromString(validateToken(token).getSubject());
    }

    public Integer extractCredentialsVersion(String token) {
        return validateToken(token).get("cv", Integer.class);
    }

    public boolean isTokenValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
