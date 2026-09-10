package com.tinku.aula.model;

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

/**
 * Confirmación de la rama "adultos" del kill-switch (T-M3-09, US-7).
 * Solo se crea cuando el beneficiario de la Reserva es adulto (rama adultos).
 * El otro participante responde si vio contenido inapropiado: "Sí" → corte,
 * "No" → la sesión continúa (solo log interno).
 */
@Entity
@Table(name = "confirmaciones_killswitch", schema = "aula")
@Getter
@Setter
@NoArgsConstructor
public class ConfirmacionKillswitch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "sesion_id", nullable = false, unique = true)
    private UUID sesionId;

    @Column(name = "detectado_id", nullable = false)
    private UUID detectadoId;

    /** null hasta que el otro participante responda. */
    @Column(name = "respondido_id")
    private UUID respondidoId;

    /** null hasta que se registre la respuesta. */
    @Column(name = "vio")
    private Boolean vio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;
}
