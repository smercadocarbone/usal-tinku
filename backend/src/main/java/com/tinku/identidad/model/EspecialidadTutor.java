package com.tinku.identidad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Especialidad declarada por el Tutor (ADR-M1-06, V56): una materia del catálogo y el nivel máximo
 * que puede enseñar. De 1 a 3 por Tutor. Su estado sale de las credenciales que la cubren
 * ({@link EstadoEspecialidad}).
 */
@Entity
@Table(name = "especialidades_tutor", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class EspecialidadTutor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    @Column(nullable = false, length = 100)
    private String materia;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_maximo", nullable = false, length = 20)
    private NivelEspecialidad nivelMaximo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
