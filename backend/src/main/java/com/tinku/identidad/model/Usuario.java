package com.tinku.identidad.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Entidad central de M1. Ver Spec_M1_Identidad_Perfiles.md y
 * Plan_M1_Identidad_Perfiles.md, sección 1.
 *
 * Decisiones de negocio que este modelo encierra (no las repitas en otro
 * lado del código, referenciá esta clase):
 *  - Un DNI = una sola fila en todo el sistema (constraint UNIQUE en BD,
 *    no solo validación de aplicación) — FR-ID-001/018/019.
 *  - Un usuario ADULTO puede tener ambas capacidades (Estudiante y Adulto
 *    Responsable) activas a la vez, combinables — Constitución, Registro
 *    de Decisiones + Spec de M1.
 *  - Un usuario MENOR siempre tiene un adultoResponsable — nunca se crea
 *    a sí mismo (FR-ID-020).
 */
@Entity
@Table(name = "usuarios", schema = "identidad")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String dni;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, length = 150)
    private String apellido;

    @Column(name = "fecha_nacimiento", nullable = false)
    private LocalDate fechaNacimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoUsuario tipo;

    @Column(name = "capacidad_estudiante", nullable = false)
    private boolean capacidadEstudiante = false;

    @Column(name = "capacidad_adulto_responsable", nullable = false)
    private boolean capacidadAdultoResponsable = false;

    /** Solo poblado si tipo = MENOR. Ver FR-ID-020: quién lo dio de alta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adulto_responsable_id")
    private Usuario adultoResponsable;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Email de contacto, exigido en el alta desde la migración V12.
     * Nullable por compatibilidad con filas creadas antes; unicidad parcial
     * (índice único solo sobre emails no nulos) en BD. */
    @Column(length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_cuenta", nullable = false, length = 20)
    private EstadoCuenta estadoCuenta = EstadoCuenta.ACTIVA;

    /**
     * Habilitación para matching (FR-ID-025): lo aprueba la Credencial/CAP y
     * lo suspende el vencimiento del CAP o una sanción de M9. Mismo flag que
     * M2/M9 usan — este es su origen en `usuarios`, M2 lo lee para excluir
     * Tutores suspendidos del matching.
     */
    @Column(name = "activo_para_matching", nullable = false)
    private boolean activoParaMatching = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Edad calculada al momento de la consulta — no persistida (evita
     * inconsistencias si la fila se lee mucho tiempo después de creada). */
    @Transient
    public int getEdad() {
        return java.time.Period.between(fechaNacimiento, LocalDate.now()).getYears();
    }
}
