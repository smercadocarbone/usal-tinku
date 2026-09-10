package com.tinku.reputacion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calificaciones", schema = "reputacion")
@Getter
@Setter
@NoArgsConstructor
public class Calificacion {

    public static final String DIR_ESTUDIANTE_A_TUTOR = "estudiante_a_tutor";
    public static final String DIR_TUTOR_A_ESTUDIANTE = "tutor_a_estudiante";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "sesion_id", nullable = false)
    private UUID sesionId;

    @Column(name = "autor_id", nullable = false)
    private UUID autorId;

    @Column(nullable = false, length = 30)
    private String direccion;

    @Column(nullable = false)
    private Short estrellas;

    @Column(length = 1000)
    private String comentario;

    @Column(name = "editable_hasta", nullable = false)
    private Instant editableHasta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
