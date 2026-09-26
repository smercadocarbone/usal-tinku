package com.tinku.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** {@code state} (CSRF) y {@code code_verifier} (PKCE) de una conexión en curso (V39). */
@Entity
@Table(name = "oauth_estados", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class OAuthEstadoMp {

    @Id
    @Column(name = "state", nullable = false, length = 64)
    private String state;

    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    @Column(name = "code_verifier", nullable = false, length = 128)
    private String codeVerifier;

    @Column(name = "expira_at", nullable = false)
    private Instant expiraAt;
}
