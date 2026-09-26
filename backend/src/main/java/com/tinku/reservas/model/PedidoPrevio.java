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
 * Pedido previo a la clase (FR-RES-027, ADR-M4-01): lo que quien pagó quiere ver, con un archivo
 * opcional. El texto se guarda ya filtrado. El archivo se borra a las 24 hs del fin agendado o al
 * cancelarse la reserva ({@code archivoRef} vuelve a null); el texto queda.
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

    @Column(length = 1000)
    private String texto;

    @Column(name = "archivo_ref", length = 500)
    private String archivoRef;

    @Column(name = "archivo_nombre", length = 200)
    private String archivoNombre;

    @Column(name = "archivo_tipo", length = 50)
    private String archivoTipo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public PedidoPrevio(UUID reservaId) {
        this.reservaId = reservaId;
    }
}
