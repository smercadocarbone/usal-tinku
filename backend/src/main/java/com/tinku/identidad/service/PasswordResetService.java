package com.tinku.identidad.service;

import com.tinku.identidad.validacion.PoliticaPassword;

import com.tinku.identidad.model.TokenResetPassword;
import com.tinku.identidad.model.Usuario;
import com.tinku.identidad.port.NotificadorResetPassword;
import com.tinku.identidad.repository.TokenResetPasswordRepository;
import com.tinku.identidad.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * "Olvidé mi contraseña" (auditoría 2026-09-19). Token opaco de un solo uso,
 * NUNCA un JWT: {@code JwtAuthenticationFilter} trata cualquier JWT firmado
 * con la clave de la app como una sesión completa (no distingue "propósito"
 * del token) — reusar esa firma acá lo volvería replayable como login. Solo
 * se persiste el HASH (SHA-256) del token; el valor plano viaja únicamente
 * en el link que recibe el usuario.
 */
@Service
public class PasswordResetService {

    private static final Duration TTL = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;

    private final UsuarioRepository usuarioRepository;
    private final TokenResetPasswordRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificadorResetPassword notificador;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UsuarioRepository usuarioRepository,
                                TokenResetPasswordRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                NotificadorResetPassword notificador) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificador = notificador;
    }

    /**
     * Responde siempre igual exista o no el DNI (FR-ID-018: no confirmar de
     * quién es un dato de identidad) — de lo contrario, esta pantalla se
     * convierte en un oráculo para enumerar DNIs registrados en Tinku.
     */
    @Transactional
    public void solicitarReset(String dni) {
        usuarioRepository.findByDni(dni).ifPresent(usuario -> {
            tokenRepository.deleteByUsuarioIdAndUsadoEnIsNull(usuario.getId());

            byte[] bytes = new byte[TOKEN_BYTES];
            random.nextBytes(bytes);
            String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            TokenResetPassword token = new TokenResetPassword();
            token.setUsuario(usuario);
            token.setTokenHash(hash(tokenPlano));
            token.setExpiraEn(Instant.now().plus(TTL));
            tokenRepository.save(token);

            notificador.notificar(usuario, tokenPlano);
        });
    }

    @Transactional
    public void resetearPassword(String tokenPlano, String passwordNueva) {
        TokenResetPassword token = tokenRepository.findByTokenHash(hash(tokenPlano))
                .orElseThrow(TokenResetInvalidoException::new);

        if (token.getUsadoEn() != null || token.getExpiraEn().isBefore(Instant.now())) {
            throw new TokenResetInvalidoException();
        }

        Usuario usuario = token.getUsuario();
        PoliticaPassword.exigirDistintaDelDni(passwordNueva, usuario.getDni());
        usuario.setPasswordHash(passwordEncoder.encode(passwordNueva));
        usuario.invalidarCredenciales(); // AUD-027: las sesiones abiertas dejan de valer
        usuarioRepository.save(usuario);

        token.setUsadoEn(Instant.now());
        tokenRepository.save(token);
    }

    private String hash(String tokenPlano) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tokenPlano.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
