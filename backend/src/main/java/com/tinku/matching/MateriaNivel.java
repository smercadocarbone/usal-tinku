package com.tinku.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Fila del catálogo cerrado de materias/niveles ({@code matching.materias_niveles},
 * V7) — FR-MATCH-006. Se siembra con los niveles educativos oficiales de
 * Argentina (primario, secundario, universitario) y sus materias curriculares;
 * la curaduría es de operaciones, no del Tutor.
 */
@Entity
@Table(name = "materias_niveles", schema = "matching")
@Getter
@NoArgsConstructor
public class MateriaNivel {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 20)
    private String nivel;

    @Column(nullable = false, length = 100)
    private String materia;

    public MateriaNivel(String nivel, String materia) {
        this.nivel = nivel;
        this.materia = materia;
    }
}