package com.tinku.identidad.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Consentimiento explícito del Adulto Responsable para el tratamiento de
 * datos del menor, SEPARADO del T&C general (BR-CONSENT-01) — tabla
 * {@code consentimientos_menor} (migración V2). Se persiste en la misma
 * transacción que el alta del menor (FR-ID-019/020).
 *
 * La tabla también contempla revocación ({@code revocadoAt}), que se usa
 * en el bloqueo de desactivación de la capacidad Adulto Responsable
 * (FR-ID-016, Chunk M1-D) y en la baja de menor (Chunk M1-E).
 */
@Entity
@Table(name = "consentimientos_menor", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class ConsentimientoMenor {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menor_id", nullable = false)
    private Usuario menor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adulto_responsable_id", nullable = false)
    private Usuario adultoResponsable;

    @Column(name = "version_texto", nullable = false, length = 50)
    private String versionTexto;

    @Column(name = "aceptado_at", nullable = false)
    private Instant aceptadoAt = Instant.now();

    @Column(name = "revocado_at")
    private Instant revocadoAt;
}
