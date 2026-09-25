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
 * Personal interno de Tinku (Plan M8 §1, V16) — fila de {@code admin.admins}.
 * Tabla separada de {@code usuarios} (un Admin no es un Estudiante/Tutor/menor),
 * aunque vinculada 1:1 a la cuenta real: el JWT existente lleva el DNI como
 * principal ({@code UsuarioDetailsService}) y el gate de autorización
 * ({@code com.tinku.admin.AdminModeracionGate}) resuelve esta fila por ese DNI.
 */
@Entity
@Table(name = "admins", schema = "admin")
@Getter
@Setter
@NoArgsConstructor
public class Admin {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RolAdmin rol;

    /** Un Admin desactivado no autoriza ningún endpoint (fail-closed). */
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}