package com.tinku.identidad.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de un solo uso para "olvidé mi contraseña" — tabla
 * {@code tokens_reset_password} (migración V23). Se guarda solo el HASH
 * (SHA-256) del token real; el valor plano viaja únicamente en el link que
 * recibe el usuario y nunca se persiste. Ver {@code PasswordResetService}.
 */
@Entity
@Table(name = "tokens_reset_password", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class TokenResetPassword {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado_en")
    private Instant usadoEn;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
