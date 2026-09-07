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

import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud de Sesión del menor (FR-RES-021, US-2): liviana, sin pago, NO bloquea
 * horario — un menor no puede reservar ni pagar (Artículo II). Su Adulto
 * Responsable la convierte en Reserva al aprobarla (T-M4-04) o se expira sola
 * a las 48hs (FR-RES-022, job de T-M4-03).
 */
@Entity
@Table(name = "solicitudes_sesion", schema = "reservas")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudSesion {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menor_id", nullable = false)
    private Usuario menor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tutor_id", nullable = false)
    private Usuario tutor;

    @Column(name = "horario_propuesto", nullable = false)
    private Instant horarioPropuesto;

    @Convert(converter = EstadoSolicitudConverter.class)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** created_at + 48hs (Tabla_Tiempos — FR-RES-022); lo programa el job del chunk M4-B. */
    @Column(name = "expira_at", nullable = false)
    private Instant expiraAt;
}