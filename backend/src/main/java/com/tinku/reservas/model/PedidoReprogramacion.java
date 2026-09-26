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
 * Pedido de reprogramación del Tutor (FR-RES-029..031): propone otro horario para una clase
 * confirmada. Quien pagó (con un Menor, su Adulto Responsable) lo acepta o cancela con la
 * devolución; si nadie responde a T-60 de la clase original, vence y la clase se cancela.
 * {@code estado}: pendiente | aceptado | rechazado | vencido | retirado.
 */
@Entity
@Table(name = "pedidos_reprogramacion", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class PedidoReprogramacion {

    public static final String PENDIENTE = "pendiente";
    public static final String ACEPTADO = "aceptado";
    public static final String RECHAZADO = "rechazado";
    public static final String VENCIDO = "vencido";
    public static final String RETIRADO = "retirado";

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "horario_original", nullable = false)
    private Instant horarioOriginal;

    @Column(name = "horario_propuesto", nullable = false)
    private Instant horarioPropuesto;

    @Column(length = 300)
    private String motivo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resuelto_at")
    private Instant resueltoAt;

    public void resolver(String nuevoEstado) {
        this.estado = nuevoEstado;
        this.resueltoAt = Instant.now();
    }
}
