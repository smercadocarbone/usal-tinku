package com.tinku.seguridad.model;

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
 * Sanción de la escala de FR-SEC-005 (Plan_M9 §1). {@code denunciaId} o
 * {@code alertaId} según {@code origen}, nunca ambos (CHECK de V13). Al
 * persistirse se publica {@code SancionAplicadaEvent} que propaga el efecto a
 * M1/M2/M4 en la misma transacción (Plan_M9 §2.5).
 */
@Entity
@Table(name = "sanciones", schema = "seguridad")
@Getter
@Setter
@NoArgsConstructor
public class Sancion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "usuario_sancionado_id", nullable = false)
    private UUID usuarioSancionadoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrigenSancion origen;

    @Column(name = "denuncia_id")
    private UUID denunciaId;

    @Column(name = "alerta_id")
    private UUID alertaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoSancion tipo;

    @Column(name = "dias_suspension")
    private Integer diasSuspension;

    @Column(name = "vigente_desde", nullable = false)
    private Instant vigenteDesde = Instant.now();

    @Column(name = "vigente_hasta")
    private Instant vigenteHasta;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}