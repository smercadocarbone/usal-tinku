package com.tinku.reservas.model;

import com.tinku.identidad.model.Usuario;
import jakarta.persistence.Column;
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
 * Paquete mensual (FR-RES-032..037, ADR-M5-03): 4 clases semanales que se pagan juntas. Cada
 * clase es una {@link Reserva} con {@code paquete}; la primera es la ancla del pago
 * ({@code external_reference} de la preferencia), así la conciliación, la vuelta desde
 * MercadoPago y el webhook siguen trabajando por id de Reserva.
 * {@code estado}: pendiente_pago | confirmado | cancelado.
 */
@Entity
@Table(name = "paquetes", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class Paquete {

    public static final String PENDIENTE_PAGO = "pendiente_pago";
    public static final String CONFIRMADO = "confirmado";
    public static final String CANCELADO = "cancelado";

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

    @Column(name = "reserva_ancla_id")
    private UUID reservaAnclaId;

    @Column(name = "cantidad_clases", nullable = false)
    private int cantidadClases;

    @Column(name = "duracion_minutos", nullable = false)
    private int duracionMinutos;

    @Column(name = "descuento_porcentaje", nullable = false)
    private int descuentoPorcentaje;

    /** Congelado al crear el paquete (FR-PAG-013): la suma de las clases con el descuento. */
    @Column(name = "precio_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioTotal;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE_PAGO;

    /** Tabla_Tiempos: 4 semanas desde la primera clase. Una clase movida tiene que terminar antes. */
    @Column(name = "vigente_hasta", nullable = false)
    private Instant vigenteHasta;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
