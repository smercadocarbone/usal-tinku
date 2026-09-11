package com.tinku.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Registro append-only de toda acción del Admin (US-6, FR-ADM-005, NFR-SEC-04).
 * La tabla {@code admin.log_auditoria_admin} (V16) NO admite UPDATE/DELETE a
 * nivel de motor de base de datos (owner = rol NOLOGIN ajeno a la app; la app
 * solo tiene SELECT/INSERT) — ver el mecanismo documentado en la migración.
 *
 * {@code adminId} referencia {@code admins.id} (no {@code usuarios.id}): es la
 * atribución de quién ejecutó, sobre el mismo rol que autorizó la acción.
 */
@Entity
@Table(name = "log_auditoria_admin", schema = "admin")
@Getter
@NoArgsConstructor
public class LogAuditoriaAdmin {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(nullable = false, length = 100)
    private String accion;

    @Column(name = "entidad_tipo", nullable = false, length = 60)
    private String entidadTipo;

    @Column(name = "entidad_id", length = 64)
    private String entidadId;

    /** Snapshot de la operación (método HTTP + ruta) — {@code jsonb} en BD. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detalle")
    private Map<String, String> detalle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LogAuditoriaAdmin(UUID adminId, String accion, String entidadTipo,
                             String entidadId, Map<String, String> detalle) {
        this.adminId = adminId;
        this.accion = accion;
        this.entidadTipo = entidadTipo;
        this.entidadId = entidadId;
        this.detalle = detalle;
    }
}