package com.tinku.identidad.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * PT5 (T08, Ley 25.326): aceptación expresa de una cláusula de los Términos en una versión dada.
 * Hoy la única es {@code GRABACION_AUDIO_RESUMEN} (ADR-M3-04).
 */
@Entity
@Table(name = "aceptaciones_clausula", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class AceptacionClausula {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(nullable = false, length = 50)
    private String clausula;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "aceptada_at", nullable = false)
    private Instant aceptadaAt = Instant.now();
}
