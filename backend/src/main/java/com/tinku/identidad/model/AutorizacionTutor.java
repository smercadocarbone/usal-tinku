package com.tinku.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Autorización de un Adulto Responsable para que un Tutor dicte clases a uno
 * de SUS menores, y el marcado "no confiable" de ese Tutor para su cuenta
 * (FR-ID-009) — tabla {@code autorizaciones_tutor} (V2).
 *
 *  - La fila es por (adulto_responsable, menor, tutor) — un AR autoriza a un
 *    Tutor por cada uno de sus menores (constraint única uq_autorizacion).
 *  - {@code noConfiable}: filtro PRIVADO de esa cuenta — el Tutor deja de
 *    aparecer en los resultados de matching de ese Adulto Responsable. No
 *    alerta a Admin ni afecta la reputación pública del Tutor (FR-ID-009).
 */
@Entity
@Table(name = "autorizaciones_tutor", schema = "identidad",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_autorizacion",
                columnNames = {"adulto_responsable_id", "menor_id", "tutor_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AutorizacionTutor {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adulto_responsable_id", nullable = false)
    private Usuario adultoResponsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menor_id", nullable = false)
    private Usuario menor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @Column(name = "no_confiable", nullable = false)
    private boolean noConfiable = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
