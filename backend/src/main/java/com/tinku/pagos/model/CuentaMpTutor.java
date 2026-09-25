package com.tinku.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Cuenta de MercadoPago conectada por OAuth de un Tutor (ADR-M5-02, V39). Tokens cifrados. */
@Entity
@Table(name = "cuentas_mp_tutor", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class CuentaMpTutor {

    @Id
    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    @Column(name = "mp_user_id", nullable = false, length = 40)
    private String mpUserId;

    @Column(name = "access_token_cifrado", nullable = false)
    private byte[] accessTokenCifrado;

    @Column(name = "refresh_token_cifrado")
    private byte[] refreshTokenCifrado;

    @Column(name = "public_key", length = 100)
    private String publicKey;

    @Column(name = "expira_at", nullable = false)
    private Instant expiraAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCuentaMp estado;

    @Column(name = "conectada_at", nullable = false)
    private Instant conectadaAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
