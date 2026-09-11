package com.tinku.matching;

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

import java.util.UUID;

/**
 * Rama del catálogo cerrado de temas (V12): nivel -> anio_o_carrera (curso o
 * carrera) -> materia. Cada trayecto agrupa una lista de {@link Tema}.
 * Entidad de lectura del catálogo de M2-F; "qué se toca" vive en cada Tema.
 */
@Entity
@Table(name = "trayectos", schema = "matching")
@Getter
@Setter
@NoArgsConstructor
public class Trayecto {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NivelTrayecto nivel;

    /** '4°' (texto) o 'Ingeniería' — columna TEXT en V12 (no poner length). */
    @Column(name = "anio_o_carrera", nullable = false)
    private String anioOCarrera;

    @Column(nullable = false)
    private String materia;
}