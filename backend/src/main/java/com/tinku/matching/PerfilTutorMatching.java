package com.tinku.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Extensión del Tutor para matching ({@code matching.perfiles_tutor_matching},
 * V7): las materias/niveles que dicta, referenciadas al catálogo cerrado
 * (FR-MATCH-006). {@code embedding} (pgvector) y {@code activo_para_matching}
 * los administran el servicio Python y M1/M9 respectivamente — este perfil
 * guarda SOLO la selección de materias/niveles del Tutor.
 *
 * Un Tutor sin fila todavía no configuró su perfil de matching: el puerto
 * {@code PerfilMatchingProvider} devuelve "sin materias/nivel".
 */
@Entity
@Table(name = "perfiles_tutor_matching", schema = "matching")
@Getter
@Setter
@NoArgsConstructor
public class PerfilTutorMatching {

    @Id
    @Column(name = "tutor_id")
    private UUID tutorId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "materias_niveles_ids", nullable = false)
    private UUID[] materiasNivelesIds = new UUID[0];

    public PerfilTutorMatching(UUID tutorId, UUID[] materiasNivelesIds) {
        this.tutorId = tutorId;
        this.materiasNivelesIds = materiasNivelesIds;
    }
}