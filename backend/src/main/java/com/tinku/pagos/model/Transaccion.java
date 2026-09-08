package com.tinku.pagos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Una fila por cobro retenido en escrow (FR-PAG-001/002/003/007, Plan M5 §1). La
 * crea el webhook de MercadoPago (Chunk M5-B, {@code EscrowService}) cuando el pago
 * queda aprobado y la Reserva pasa a {@code confirmada}. El vínculo es DIRECTO con
 * la Reserva ({@code reservaId}), nunca con la Sesión (corrige E-06/E-07/E-18 del
 * informe de QA).
 *
 * {@code liberar_at} = {@code sesion.finalizada.timestamp + 24h} de FR-PAG-002
 * (nullable hasta que la sesión finalice); {@code intentos_liberacion} cuenta
 * fallos del job de liberación (máximo 3 antes de intervención manual, FR-PAG-007).
 */
@Entity
@Table(name = "transacciones", schema = "pagos")
@Getter
@Setter
@NoArgsConstructor
public class Transaccion {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reserva_id", nullable = false)
    private UUID reservaId;

    @Column(name = "mp_payment_id", nullable = false)
    private String mpPaymentId;

    /** Precio congelado de la Reserva (FR-PAG-013) — lo que el Estudiante pagó. */
    @Column(name = "monto_bruto", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoBruto;

    /** BR-PAG-01: 15% de {@code montoBruto}, siempre sobre el precio congelado. */
    @Column(name = "comision_plataforma", nullable = false, precision = 10, scale = 2)
    private BigDecimal comisionPlataforma;

    @Convert(converter = EstadoTransaccionConverter.class)
    @Column(nullable = false, length = 20)
    private EstadoTransaccion estado = EstadoTransaccion.RETENIDO_ESCROW;

    @Column(name = "liberar_at")
    private Instant liberarAt;

    @Column(name = "intentos_liberacion", nullable = false)
    private int intentosLiberacion = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}