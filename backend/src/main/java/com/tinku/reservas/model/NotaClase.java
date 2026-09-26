package com.tinku.reservas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Nota del Tutor al Adulto Responsable sobre una clase con un Menor (FR-RES-026). Texto filtrado,
 * editable 48 hs desde {@code createdAt} (Tabla_Tiempos). No es un resumen automático: no se
 * graba nada (Art. II y V).
 */
@Entity
@Table(name = "notas_clase", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class NotaClase {

    @Id
    @Column(name = "reserva_id")
    private UUID reservaId;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public NotaClase(UUID reservaId) {
        this.reservaId = reservaId;
    }
}
