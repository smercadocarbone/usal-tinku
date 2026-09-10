package com.tinku.admin.model;

import com.tinku.identidad.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Canal "contactar a soporte" (US-7, FR-ADM-006, V16). El rol destino se deriva
 * de {@code origenModulo} en el momento de crearlo, vía la tabla de mapeo
 * {@code admin.mapeo_origen_rol} (T-M8-05) — una tabla de config que el equipo
 * de operaciones edita sin desplegar código (Plan M8 §3.3).
 */
@Entity
@Table(name = "tickets_soporte", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
public class TicketSoporte {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "origen_modulo", nullable = false, length = 60)
    private String origenModulo;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false, length = 1000)
    private String detalle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoTicket estado = EstadoTicket.ABIERTO;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_asignado", nullable = false, length = 30)
    private RolAdmin rolAsignado;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    @Column(name = "resuelto_en")
    private Instant resueltoEn;
}