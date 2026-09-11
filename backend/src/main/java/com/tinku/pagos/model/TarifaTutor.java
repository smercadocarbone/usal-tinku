package com.tinku.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarifa por sesión del Tutor (US-6, FR-PAG-006, Plan M5-H): una fila por Tutor
 * con el precio vigente que cobra por sesión. La crea/actualiza el propio Tutor
 * desde su perfil (Chunk M5-H); la Reserva lo congela al crearse (FR-PAG-013) —
 * un cambio de tarifa jamás afecta una Reserva ya confirmada.
 */
@Entity
@Table(name = "tarifas_tutor", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class TarifaTutor {

    @Id
    @Column(name = "tutor_id", nullable = false)
    private UUID tutorId;

    @Column(name = "precio_sesion", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioSesion;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private Instant updatedAt = Instant.now();
}