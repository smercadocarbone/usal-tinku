package com.tinku.seguridad.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Denuncia estándar (US-1, Plan_M9 §1). {@code sesionId} nullable (denuncia de
 * perfil, sin sesión asociada) y es la unidad de pausa del escrow (FR-SEC-003/011:
 * por caso, no por par — se resuelve consultando la transacción de esta sesión).
 */
@Entity
@Table(name = "denuncias", schema = "seguridad")
@Getter
@Setter
@NoArgsConstructor
public class Denuncia {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "denunciante_id", nullable = false)
    private UUID denuncianteId;

    @Column(name = "denunciado_id", nullable = false)
    private UUID denunciadoId;

    @Column(name = "sesion_id")
    private UUID sesionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MotivoDenuncia motivo;

    @Column(name = "evidencia_url", length = 500)
    private String evidenciaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoDenuncia estado = EstadoDenuncia.REGISTRADA;

    @Column(name = "descargo_texto", length = 300)
    private String descargoTexto;

    @Column(name = "descargo_recibido_at")
    private Instant descargoRecibidoAt;

    @Column(name = "descargo_vence_at")
    private Instant descargoVenceAt;

    @Column(name = "sla_resolucion_vence_at")
    private Instant slaResolucionVenceAt;

    @Column(name = "prioridad_alta", nullable = false)
    private boolean prioridadAlta = false;

    @Column(name = "admin_resolutor_id")
    private UUID adminResolutorId;

    @Column(name = "resuelta_at")
    private Instant resueltaAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}