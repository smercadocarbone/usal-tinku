package com.tinku.reservas.model;

import com.tinku.identidad.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva (US-3/US-4, FR-RES-001/003/...). La crea y paga exclusivamente quien
 * tiene esa capacidad — Estudiante adulto o Adulto Responsable por un menor —
 * nunca el menor directamente (Artículo II). {@code precio} se congela al crear
 * la fila (FR-PAG-013 de M5). La EXCLUDE de V9 (FR-RES-007) garantiza sin
 * superposición sobre tutor/beneficiario + horario mientras no esté cancelada.
 */
@Entity
@Table(name = "reservas", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pagador_id", nullable = false)
    private Usuario pagador;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiario_id", nullable = false)
    private Usuario beneficiario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitud_origen_id")
    private SolicitudSesion solicitudOrigen;

    @Column(nullable = false)
    private Instant horario;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Convert(converter = EstadoReservaConverter.class)
    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE_PAGO;

    @Convert(converter = MotivoCancelacionConverter.class)
    @Column(name = "motivo_cancelacion", length = 30)
    private MotivoCancelacion motivoCancelacion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}