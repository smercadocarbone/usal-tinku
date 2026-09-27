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
 * Pedido previo a la clase (FR-RES-027): lo que quien pagó quiere ver, en texto. Se guarda ya
 * filtrado. Sin archivos (decisión del dueño, 2026-09-27).
 */
@Entity
@Table(name = "pedidos_previos", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class PedidoPrevio {

    @Id
    @Column(name = "reserva_id")
    private UUID reservaId;

    @Column(nullable = false, length = 1000)
    private String texto;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PedidoPrevio(UUID reservaId) {
        this.reservaId = reservaId;
    }
}
