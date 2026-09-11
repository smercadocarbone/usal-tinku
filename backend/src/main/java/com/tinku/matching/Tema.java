package com.tinku.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Unidad atómica del catálogo (V12, contrato 2a): el TEMA es lo que un Tutor
 * elige en su perfil y lo que la descripción alimenta al embedding del motor.
 * "Buscar 'cómo dividir' hace match semántico con el tema 'División', no con
 * 'Matemática 4°'" (Plan_M2_Temas.md sección 1).
 */
@Entity
@Table(name = "temas", schema = "matching")
@Getter
@Setter
@NoArgsConstructor
public class Tema {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trayecto_id", nullable = false)
    private Trayecto trayecto;

    @Column(nullable = false)
    private String nombre;

    /** 'qué se toca': la fuente de texto del embedding del Tutor. */
    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private int orden;
}