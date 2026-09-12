package com.tinku.pagos.model;

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
 * Flag global de la pasarela de pagos (V22): fila única {@code id=1}. Con
 * {@code habilitada=true} M5 cobra por MercadoPago real; con {@code false} las
 * Reservas se confirman en modo Bypass (sin cobro real) y todo el ciclo del
 * escrow se resuelve en local. Lo escribe M8 (rol Soporte Financiero) vía
 * {@code PasarelaService}; M5 lo lee en cada punto de contacto con el proveedor.
 */
@Entity
@Table(name = "pasarela_estado", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class EstadoPasarela {

    @Id
    @Column(nullable = false)
    private Short id;

    @Column(nullable = false)
    private boolean habilitada;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}